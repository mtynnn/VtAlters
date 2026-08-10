/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.ritual;

import com.valerinsmp.valtars.VAltarsPlugin;
import com.valerinsmp.valtars.domain.AltarDefinition;
import com.valerinsmp.valtars.domain.BlockKey;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class RitualPresentation {
    private static final long PRE_DELAY = 40L;
    private static final long SPIRAL_DURATION = 14L;
    private static final long ORBIT_DURATION = 60L;
    private static final long CONVERGE_DURATION = 5L;

    private final VAltarsPlugin plugin;
    private final Map<String, Animation> animations = new HashMap<>();

    public RitualPresentation(VAltarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(AltarDefinition altar, RitualSession session, List<BlockKey> origins,
                     Runnable completion, Consumer<Throwable> failure) {
        if (origins.size() != session.visualItems().size()) {
            throw new IllegalArgumentException("Visual item/source mismatch");
        }
        Location center = requireLocation(altar.center());
        Animation animation = new Animation(altar.name(), completion, failure);
        if (animations.putIfAbsent(key(altar.name()), animation) != null) {
            throw new IllegalStateException("Animation already active for " + altar.name());
        }
        try {
            List<ItemStack> items = session.visualItems();
            for (int i = 0; i < items.size(); i++) {
                Location source = requireLocation(origins.get(i)).add(0.5, 1.0, 0.5);
                ItemDisplay visual = source.getWorld().spawn(source, ItemDisplay.class);
                visual.setItemStack(items.get(i));
                visual.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                visual.setTeleportDuration(1);
                visual.setPersistent(false);
                animation.visuals.add(visual);
                animation.starts.put(visual, source.clone());
            }
            playSound(center, session.settings().startSound());
            animation.task = new BukkitRunnable() {
                private long tick;

                @Override
                public void run() {
                    try {
                        renderFrame(animation, center, session.settings(), tick++);
                        if (tick > PRE_DELAY + SPIRAL_DURATION + ORBIT_DURATION + CONVERGE_DURATION) {
                            animation.succeed();
                        }
                    } catch (Throwable throwable) {
                        animation.fail(throwable);
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
        } catch (Throwable throwable) {
            animation.fail(throwable);
        }
    }

    public void cancel(String altarName, Throwable cause) {
        Animation animation = animations.get(key(altarName));
        if (animation != null) animation.fail(cause);
    }

    public void cancelAll(Throwable cause) {
        for (Animation animation : new ArrayList<>(animations.values())) animation.fail(cause);
    }

    public void particle(Location location, String configured, int count, double spread) {
        Particle particle = resolveParticle(configured);
        if (particle != null && location != null && location.getWorld() != null) {
            location.getWorld().spawnParticle(particle, location, count, spread, spread, spread, 0.01);
        }
    }

    public void playSound(Location location, String configured) {
        if (location == null || location.getWorld() == null || configured == null || configured.isBlank()) return;
        String[] parts = configured.split(",", -1);
        try {
            Sound sound = resolveSound(parts[0]);
            if (sound == null) throw new IllegalArgumentException("Unknown sound " + parts[0].trim());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid ritual sound '" + configured + "': " + exception.getMessage());
        }
    }

    static Sound resolveSound(String configured) {
        String token = configured.trim();
        try {
            Object constant = Sound.class.getField(token.toUpperCase(Locale.ROOT)).get(null);
            if (constant instanceof Sound sound) return sound;
        } catch (ReflectiveOperationException ignored) {
            // Fall through to an exact namespaced sound key.
        }
        NamespacedKey key = NamespacedKey.fromString(token.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.SOUNDS.get(key);
    }

    private void renderFrame(Animation animation, Location center, RitualSettings settings, long tick) {
        Location orbitCenter = center.clone().add(0.5, 4.0, 0.5);
        Location finalPoint = center.clone().add(0.5, 5.0, 0.5);
        if (tick < PRE_DELAY) {
            renderRings(center, settings, tick);
            if (tick % 25 == 0) playSound(center, settings.ambientSound());
            return;
        }

        long stage = tick - PRE_DELAY;
        if (stage == 0) playSound(center, settings.itemsFlySound());
        int count = Math.max(1, animation.visuals.size());
        int index = 0;
        for (ItemDisplay visual : animation.visuals) {
            if (!visual.isValid()) {
                index++;
                continue;
            }
            double baseAngle = Math.toRadians(index * (360.0 / count));
            Location target;
            if (stage < SPIRAL_DURATION) {
                double progress = (double) stage / SPIRAL_DURATION;
                Location orbit = orbit(orbitCenter, baseAngle);
                Location start = animation.starts.get(visual);
                target = start.clone().add(orbit.toVector().subtract(start.toVector()).multiply(progress));
            } else if (stage < SPIRAL_DURATION + ORBIT_DURATION) {
                double progress = (double) (stage - SPIRAL_DURATION) / ORBIT_DURATION;
                target = orbit(orbitCenter, baseAngle + Math.toRadians(progress * 540.0));
            } else {
                double progress = Math.min(1.0,
                        (double) (stage - SPIRAL_DURATION - ORBIT_DURATION) / CONVERGE_DURATION);
                Location orbit = orbit(orbitCenter, baseAngle + Math.toRadians(540.0));
                target = orbit.clone().add(finalPoint.toVector().subtract(orbit.toVector()).multiply(progress));
            }
            visual.teleport(target);
            particle(target, settings.trailParticle(), 1, 0.15);
            if (tick % 3 == 0) particle(target, settings.secondaryTrailParticle(), 1, 0.0);
            index++;
        }
        if (stage == SPIRAL_DURATION + ORBIT_DURATION) playSound(center, settings.convergeSound());
        if (stage == SPIRAL_DURATION + ORBIT_DURATION + CONVERGE_DURATION) burst(finalPoint, settings);
    }

    private void renderRings(Location center, RitualSettings settings, long tick) {
        Particle particle = resolveParticle(settings.ringParticle());
        if (particle == null) return;
        Location ringCenter = center.clone().add(0.5, 1.0 + settings.ritualRingOffset(), 0.5);
        double flow = tick / 20.0 * Math.PI * 2;
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 12) {
            double x = Math.cos(angle + flow) * 0.8;
            double z = Math.sin(angle + flow) * 0.8;
            ringCenter.getWorld().spawnParticle(particle, ringCenter.clone().add(x, 0, z), 1, 0, 0, 0, 0);
            ringCenter.getWorld().spawnParticle(particle, ringCenter.clone().add(0, x, z), 1, 0, 0, 0, 0);
        }
    }

    private void burst(Location point, RitualSettings settings) {
        Particle particle = resolveParticle(settings.burstParticle());
        if (particle == null) return;
        playSound(point, settings.convergeSound());
        for (int i = 0; i < 100; i++) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Vector direction = new Vector(random.nextDouble(-1, 1), random.nextDouble(-1, 1),
                    random.nextDouble(-1, 1));
            if (direction.lengthSquared() == 0) continue;
            direction.normalize().multiply(0.4);
            point.getWorld().spawnParticle(particle, point, 0,
                    direction.getX(), direction.getY(), direction.getZ(), 1.0);
        }
    }

    private Particle resolveParticle(String configured) {
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("none")) return null;
        try {
            return Particle.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid ritual particle '" + configured + "'");
            return null;
        }
    }

    private Location orbit(Location center, double angle) {
        return center.clone().add(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);
    }

    private Location requireLocation(BlockKey block) {
        Location location = block == null ? null : block.location();
        if (location == null || location.getWorld() == null) throw new IllegalStateException("World is not loaded for " + block);
        return location;
    }

    private String key(String altarName) {
        return altarName.toLowerCase(Locale.ROOT);
    }

    private final class Animation {
        private final String altarName;
        private final Runnable completion;
        private final Consumer<Throwable> failure;
        private final List<ItemDisplay> visuals = new ArrayList<>();
        private final Map<ItemDisplay, Location> starts = new HashMap<>();
        private BukkitTask task;
        private boolean terminal;

        private Animation(String altarName, Runnable completion, Consumer<Throwable> failure) {
            this.altarName = altarName;
            this.completion = completion;
            this.failure = failure;
        }

        private void succeed() {
            if (!close()) return;
            try {
                completion.run();
            } catch (Throwable throwable) {
                failure.accept(throwable);
            }
        }

        private void fail(Throwable throwable) {
            if (!close()) return;
            failure.accept(throwable);
        }

        private boolean close() {
            if (terminal) return false;
            terminal = true;
            if (task != null) task.cancel();
            visuals.forEach(Entity::remove);
            animations.remove(key(altarName), this);
            return true;
        }
    }
}
