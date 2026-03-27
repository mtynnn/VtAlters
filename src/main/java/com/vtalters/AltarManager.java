/*
 * VtAlters - Plugin for summoning bosses via ritual altars.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */

package com.vtalters;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.api.exceptions.InvalidMobTypeException;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Core altar system manager.
 *
 * Responsibilities:
 *  - Load and maintain altars from altars.yml.
 *  - Handle item placement and retrieval on pedestals.
 *  - Check whether an altar is ready for summoning.
 *  - Run the multi-phase ritual animation.
 *  - Spawn the MythicMobs boss when the animation finishes.
 *  - Transparent support for Nexo custom items alongside standard Bukkit items.
 */
public class AltarManager {

    private final VtAlters plugin;
    private final LanguageManager lang;
    private final NexoHook nexo;

    /** Center-block location → Altar */
    private final Map<Location, Altar> altarMap = new HashMap<>();
    /** Pedestal block location → displayed Item entity */
    private final Map<Location, Item> placedItemsDisplay = new HashMap<>();
    /** Pedestal block location → UUID of the player who placed the item */
    private final Map<Location, UUID> itemPlacers = new HashMap<>();
    /** All running BukkitTasks (particle loops, animations) */
    private final List<BukkitTask> activeTasks = new ArrayList<>();
    /** Per-pedestal peripheral particle task */
    private final Map<Location, BukkitTask> peripheralParticleTasks = new HashMap<>();
    /** Altars currently running a summoning animation */
    private final Set<Altar> summoningAltars = new HashSet<>();
    /** Every block belonging to any altar (used for protection) */
    private final Set<Location> allAltarBlocks = new HashSet<>();

    /** Offset between ArmorStand feet and its displayed helmet item */
    private static final double ARMOR_STAND_HEAD_OFFSET = 0.75;

    public AltarManager(VtAlters plugin) {
        this.plugin = plugin;
        this.lang   = plugin.getLanguageManager();
        this.nexo   = plugin.getNexoHook();
        loadAltars();
        startReadyAltarEffectTask();
    }

    // =========================================================================
    // ALTAR LOADING
    // =========================================================================

    @SuppressWarnings("unchecked")
    public void loadAltars() {
        altarMap.clear();
        allAltarBlocks.clear();

        ConfigurationSection altarsSection =
                plugin.getDataManager().getConfig().getConfigurationSection("altars");
        if (altarsSection == null) return;

        for (String altarName : altarsSection.getKeys(false)) {
            ConfigurationSection sec = altarsSection.getConfigurationSection(altarName);
            if (sec == null) continue;

            Altar altar = new Altar(altarName);

            // --- Center block ---
            Location center = Altar.locationFromString(sec.getString("center"));
            if (center == null && !Objects.equals(sec.getString("center", "not_set"), "not_set")) {
                plugin.getErrorHandler().logError(
                    "Invalid center location for altar '" + altarName + "' in altars.yml. " +
                    "It may be a typo or a world that no longer exists.",
                    "Altar Data Error");
            }
            altar.setCenterLocation(center);
            if (center != null) allAltarBlocks.add(center.getBlock().getLocation());

            // --- Boss name ---
            altar.setBossName(sec.getString("boss-name", "DefaultBoss"));

            // --- Central (activation) item ---
            // Nexo item takes priority over a plain Bukkit ItemStack
            String nexoCentralId = sec.getString("central-item-nexo-id", null);
            if (nexoCentralId != null && !nexoCentralId.isEmpty()) {
                altar.setCentralItemNexoId(nexoCentralId);
                if (nexo.isAvailable()) {
                    ItemStack nexoStack = nexo.buildNexoItem(nexoCentralId);
                    if (nexoStack != null) {
                        altar.setCentralItem(nexoStack);
                    } else {
                        plugin.getErrorHandler().logError(
                            "Nexo ID '" + nexoCentralId + "' for central-item of altar '" + altarName + "' does not exist in Nexo.",
                            "Altar Data Error");
                    }
                }
            } else if (sec.isConfigurationSection("central-item")) {
                try {
                    altar.setCentralItem(ItemStack.deserialize(
                            sec.getConfigurationSection("central-item").getValues(true)));
                } catch (Exception e) {
                    plugin.getErrorHandler().logError(
                        "Failed to deserialize central-item for altar '" + altarName + "'. Error: " + e.getMessage(),
                        "Altar Data Error");
                }
            }

            // --- Required items (Bukkit) ---
            Map<ItemStack, Integer> requiredItems = new HashMap<>();
            List<Map<?, ?>> itemsList = sec.getMapList("required-items");
            for (Map<?, ?> itemMap : itemsList) {
                if (itemMap.containsKey("item") && itemMap.containsKey("amount")) {
                    try {
                        ItemStack item = ItemStack.deserialize((Map<String, Object>) itemMap.get("item"));
                        int amount = (Integer) itemMap.get("amount");
                        Optional<ItemStack> existing = requiredItems.keySet().stream()
                                .filter(k -> k.isSimilar(item)).findFirst();
                        if (existing.isPresent()) {
                            requiredItems.put(existing.get(), requiredItems.get(existing.get()) + amount);
                        } else {
                            requiredItems.put(item, amount);
                        }
                    } catch (Exception e) {
                        plugin.getErrorHandler().logError(
                            "Failed to deserialize a required-item for altar '" + altarName + "'. Error: " + e.getMessage(),
                            "Altar Data Error");
                    }
                }
            }

            // --- Required items (Nexo) ---
            Map<String, Integer> nexoRequiredIds = new HashMap<>();
            ConfigurationSection nexoItemsSec = sec.getConfigurationSection("required-items-nexo");
            if (nexoItemsSec != null) {
                for (String nexoId : nexoItemsSec.getKeys(false)) {
                    int amount = nexoItemsSec.getInt(nexoId, 1);
                    nexoRequiredIds.put(nexoId, amount);
                    // Build the ItemStack now so it can be displayed on pedestals
                    if (nexo.isAvailable()) {
                        ItemStack nexoStack = nexo.buildNexoItem(nexoId);
                        if (nexoStack != null) {
                            requiredItems.put(nexoStack, amount);
                        } else {
                            plugin.getErrorHandler().logError(
                                "Nexo ID '" + nexoId + "' in required-items-nexo for altar '" + altarName + "' does not exist.",
                                "Altar Data Error");
                        }
                    }
                }
            }

            altar.setRequiredItems(requiredItems);
            altar.setRequiredItemNexoIds(nexoRequiredIds);

            // --- Pedestal locations ---
            List<Location> pedestals = new ArrayList<>();
            for (String locStr : sec.getStringList("pedestal-locations")) {
                Location loc = Altar.locationFromString(locStr);
                if (loc != null) {
                    pedestals.add(loc);
                    allAltarBlocks.add(loc.getBlock().getLocation());
                } else {
                    plugin.getErrorHandler().logError(
                        "Invalid pedestal location '" + locStr + "' for altar '" + altarName + "'.",
                        "Altar Data Error");
                }
            }
            altar.setPedestalLocations(pedestals);

            if (altar.getCenterLocation() != null) {
                altarMap.put(altar.getCenterLocation().getBlock().getLocation(), altar);
            }
        }
    }

    // =========================================================================
    // ITEM PLACEMENT & RETRIEVAL
    // =========================================================================

    private void placeItem(Player player, Location loc, ItemStack item) {
        Location blockLoc = loc.getBlock().getLocation();

        // Validate: the item must be one of the altar's required items
        Altar altar = getAltarAt(loc);
        if (altar != null && !altar.getRequiredItems().isEmpty()) {
            if (!isRequiredItem(altar, item)) {
                // Build a hint with the name of the first required item the altar needs
                String hint = getFirstMissingRequiredItemName(altar);
                lang.sendMessage(player, "altar-interaction.wrong-pedestal-item",
                        "%item%", nexo.getDisplayName(item),
                        "%hint%", hint);
                return;
            }
        }

        double pedestalHeight = plugin.getConfig().getDouble("effects.heights.pedestal", 1.2);
        Location displayLoc = blockLoc.clone().add(0.5, pedestalHeight - 0.2, 0.5);

        ItemStack single = item.clone();
        single.setAmount(1);

        // Replace any existing item on this pedestal
        if (placedItemsDisplay.containsKey(blockLoc)) {
            retrieveItem(player, blockLoc);
        }

        Item dropped = blockLoc.getWorld().dropItem(displayLoc, single);
        dropped.setPickupDelay(Integer.MAX_VALUE);
        dropped.setGravity(false);
        dropped.setVelocity(new Vector(0, 0, 0));
        placedItemsDisplay.put(blockLoc, dropped);
        itemPlacers.put(blockLoc, player.getUniqueId());

        if (altar != null && isRequiredItem(altar, item)) {
            startPeripheralParticle(loc);
        }

        player.getInventory().getItemInMainHand().setAmount(item.getAmount() - 1);
        loc.getWorld().playSound(loc, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1f);
    }

    /**
     * Returns the display name of the first required item that still needs
     * to be placed on this altar, as a hint for the wrong-item message.
     * Prefers items not yet placed on any pedestal.
     */
    private String getFirstMissingRequiredItemName(Altar altar) {
        // Collect items already placed
        List<ItemStack> placed = placedItemsDisplay.values().stream()
                .map(Item::getItemStack).collect(Collectors.toList());

        // Check standard required items first
        for (Map.Entry<ItemStack, Integer> req : altar.getRequiredItems().entrySet()) {
            ItemStack reqItem = req.getKey();
            long alreadyPlaced = placed.stream().filter(p -> nexo.itemsMatch(p, reqItem)).count();
            if (alreadyPlaced < req.getValue()) {
                return nexo.getDisplayName(reqItem);
            }
        }

        // Fallback: return name of any required item
        if (!altar.getRequiredItems().isEmpty()) {
            return nexo.getDisplayName(altar.getRequiredItems().keySet().iterator().next());
        }
        if (!altar.getRequiredItemNexoIds().isEmpty()) {
            return "[Nexo] " + altar.getRequiredItemNexoIds().keySet().iterator().next();
        }
        return "?";
    }

    /**
     * Returns true if the given item matches any required item of the altar,
     * checking Nexo IDs first when applicable.
     */
    private boolean isRequiredItem(Altar altar, ItemStack item) {
        if (nexo.isAvailable() && nexo.isNexoItem(item)) {
            String id = nexo.getNexoId(item);
            if (altar.getRequiredItemNexoIds().containsKey(id)) return true;
        }
        return altar.getRequiredItems().keySet().stream()
                .anyMatch(req -> nexo.itemsMatch(req, item));
    }

    private void retrieveItem(Player player, Location loc) {
        Location blockLoc = loc.getBlock().getLocation();
        Item dropped = placedItemsDisplay.get(blockLoc);
        if (dropped == null) return;

        boolean preventTheft = plugin.getConfig().getBoolean("altar.prevent-item-theft", true);
        if (preventTheft && itemPlacers.containsKey(blockLoc)) {
            if (!player.getUniqueId().equals(itemPlacers.get(blockLoc))) {
                lang.sendMessage(player, "altar-interaction.not-your-item");
                return;
            }
        }

        ItemStack item = dropped.getItemStack();
        if (item != null) player.getInventory().addItem(item);

        dropped.remove();
        placedItemsDisplay.remove(blockLoc);
        itemPlacers.remove(blockLoc);
        stopPeripheralParticle(blockLoc);
        loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
    }

    // =========================================================================
    // PARTICLE EFFECTS
    // =========================================================================

    private void startPeripheralParticle(Location loc) {
        Location blockLoc = loc.getBlock().getLocation();
        stopPeripheralParticle(blockLoc);

        double height = plugin.getConfig().getDouble("effects.heights.pedestal", 1.2);
        Particle particle = getParticle("effects.particles.pedestal-ready", "END_ROD");
        if (particle == null) return;

        BukkitTask task = new BukkitRunnable() {
            private double angle = 0;
            private final double radius = 0.8;

            @Override
            public void run() {
                angle += Math.PI / 16;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                loc.getWorld().spawnParticle(particle,
                        blockLoc.clone().add(0.5 + x, height, 0.5 + z), 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 2L);
        peripheralParticleTasks.put(blockLoc, task);
    }

    private void stopPeripheralParticle(Location loc) {
        BukkitTask task = peripheralParticleTasks.remove(loc.getBlock().getLocation());
        if (task != null) task.cancel();
    }

    private void startReadyAltarEffectTask() {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Particle p = getParticle("effects.particles.altar-ready", "SOUL_FIRE_FLAME");
                if (p == null) { this.cancel(); return; }
                double height = plugin.getConfig().getDouble("effects.heights.ready-particle", 1.2);
                for (Altar altar : altarMap.values()) {
                    if (isAltarReady(altar) && !summoningAltars.contains(altar)) {
                        Location center = altar.getCenterLocation();
                        if (center != null)
                            center.getWorld().spawnParticle(p, center.clone().add(0.5, height, 0.5), 5, 0.3, 0.3, 0.3, 0.01);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        activeTasks.add(task);
    }

    private void spawnSummoningRings(Location center) {
        Particle ringParticle = getParticle("effects.particles.ritual-ring", "SOUL_FIRE_FLAME");
        if (ringParticle == null) return;

        Location itemCenter = center.clone().add(0.5, 1.0, 0.5);
        double ringYOffset = plugin.getConfig().getDouble("effects.heights.ritual-ring-offset", 0.0);
        double radius = 0.8;
        int duration = 40;

        final Vector xAxis = new Vector(1, 0, 0);
        final Vector yAxis = new Vector(0, 1, 0);
        final Vector zAxis = new Vector(0, 0, 1);
        final double sysAngle = Math.PI / 4.0;
        final double cos = Math.cos(sysAngle);
        final double sin = Math.sin(sysAngle);

        BukkitTask t = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks > duration) { this.cancel(); return; }
                Location pCenter = itemCenter.clone().add(0, ringYOffset, 0);
                double flow = (double) ticks / 20.0 * 2 * Math.PI;
                if (ticks % 25 == 0) playSoundFromConfig(pCenter, "effects.sounds.ritual-ambient-loop");

                for (double t2 = 0; t2 < 2 * Math.PI; t2 += Math.PI / 16) {
                    double ct = Math.cos(t2 + flow), st = Math.sin(t2 + flow);
                    Vector p1 = xAxis.clone().multiply(radius * ct).add(yAxis.clone().multiply(radius * st));
                    Vector p2 = zAxis.clone().multiply(radius * ct).add(yAxis.clone().multiply(radius * st));
                    double p1xr = p1.getX() * cos - p1.getZ() * sin, p1zr = p1.getX() * sin + p1.getZ() * cos;
                    p1.setX(p1xr); p1.setZ(p1zr);
                    double p2xr = p2.getX() * cos - p2.getZ() * sin, p2zr = p2.getX() * sin + p2.getZ() * cos;
                    p2.setX(p2xr); p2.setZ(p2zr);
                    pCenter.getWorld().spawnParticle(ringParticle, pCenter.clone().add(p1), 1, 0, 0, 0, 0);
                    pCenter.getWorld().spawnParticle(ringParticle, pCenter.clone().add(p2), 1, 0, 0, 0, 0);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        activeTasks.add(t);
    }

    // =========================================================================
    // SUMMONING ANIMATION
    // =========================================================================

    private ArmorStand createVisualArmorStand(Location loc, ItemStack stack) {
        return loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setGravity(false);
            as.setMarker(true);
            as.setVisible(false);
            as.setSmall(true);
            if (as.getEquipment() != null) as.getEquipment().setHelmet(stack.clone());
        });
    }

    private void startSummoningAnimation(Altar altar, Player player, ItemStack centralItem) {
        Location center = altar.getCenterLocation();
        if (center == null || center.getWorld() == null) return;

        summoningAltars.add(altar);
        lang.sendMessage(player, "altar-interaction.ritual-start");
        playSoundFromConfig(center, "effects.sounds.ritual-start");
        spawnSummoningRings(center);

        Location finalPoint   = center.clone().add(0.5, 5, 0.5);
        Location orbitPoint   = center.clone().add(0.5, 4, 0.5);
        double   orbitRadius  = 2.0;

        List<Item> ceremonyItems = new ArrayList<>(placedItemsDisplay.values());
        placedItemsDisplay.clear();
        itemPlacers.clear();
        altar.getPedestalLocations().stream().filter(Objects::nonNull).forEach(this::stopPeripheralParticle);

        // Add a visible central item entity
        Item centralDisplay = center.getWorld().dropItem(center.clone().add(0.5, 1.0, 0.5), centralItem);
        centralDisplay.setPickupDelay(Integer.MAX_VALUE);
        centralDisplay.setGravity(false);
        centralDisplay.setVelocity(new Vector(0, 0, 0));
        ceremonyItems.add(centralDisplay);

        new BukkitRunnable() {
            private final long PRE_DELAY      = 40L;
            private final long SPIRAL_DUR     = 14L;
            private final long ORBIT_DUR      = 60L;
            private final long CONVERGE_DUR   = 5L;

            private long ticks = 0;
            private boolean finished = false;
            private Map<ArmorStand, Location> flyingEntities  = null;
            private Map<ArmorStand, Location> orbitLocations  = null;

            @Override
            public void run() {
                if (finished) { this.cancel(); return; }

                // Phase 0: pre-animation delay
                if (ticks < PRE_DELAY) {
                    if (ticks == 0) center.getWorld().playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1f);
                    ticks++;
                    return;
                }

                // Initialize flying armor stands
                if (flyingEntities == null) {
                    playSoundFromConfig(center, "effects.sounds.ritual-items-fly");
                    flyingEntities = new HashMap<>();
                    orbitLocations = new HashMap<>();
                    int i = 0;
                    double step = 360.0 / ceremonyItems.size();
                    for (Item item : ceremonyItems) {
                        if (!item.isValid()) continue;
                        Location startLoc = item.getLocation().clone();
                        ArmorStand as = createVisualArmorStand(
                                startLoc.clone().subtract(0, ARMOR_STAND_HEAD_OFFSET, 0),
                                item.getItemStack().clone());
                        flyingEntities.put(as, startLoc);
                        double ang = Math.toRadians(i * step);
                        orbitLocations.put(as, new Location(center.getWorld(),
                                orbitPoint.getX() + orbitRadius * Math.cos(ang),
                                orbitPoint.getY(),
                                orbitPoint.getZ() + orbitRadius * Math.sin(ang)));
                        item.remove();
                        i++;
                    }
                }

                long stage = ticks - PRE_DELAY;

                // Phase 1: spiral to orbit
                if (stage < SPIRAL_DUR) {
                    double prog = (double) stage / SPIRAL_DUR;
                    for (Map.Entry<ArmorStand, Location> e : flyingEntities.entrySet()) {
                        ArmorStand as = e.getKey(); Location start = e.getValue();
                        Location orbit = orbitLocations.get(as);
                        if (!as.isValid() || orbit == null) continue;
                        Location vis = start.clone().add(orbit.toVector().subtract(start.toVector()).multiply(prog));
                        as.teleport(vis.clone().subtract(0, ARMOR_STAND_HEAD_OFFSET, 0));
                        spawnTrailParticles(as, vis);
                    }
                }
                // Phase 2: orbit around the center
                else if (stage < SPIRAL_DUR + ORBIT_DUR) {
                    double prog  = (double)(stage - SPIRAL_DUR) / ORBIT_DUR;
                    double offset = prog * 540.0;
                    int i = 0; double step = 360.0 / flyingEntities.size();
                    for (ArmorStand as : flyingEntities.keySet()) {
                        if (!as.isValid()) continue;
                        double ang = Math.toRadians(i * step + offset);
                        Location vis = new Location(center.getWorld(),
                                orbitPoint.getX() + orbitRadius * Math.cos(ang),
                                orbitPoint.getY(),
                                orbitPoint.getZ() + orbitRadius * Math.sin(ang));
                        as.teleport(vis.clone().subtract(0, ARMOR_STAND_HEAD_OFFSET, 0));
                        spawnTrailParticles(as, vis);
                        i++;
                    }
                }
                // Phase 3: converge to final point
                else if (stage < SPIRAL_DUR + ORBIT_DUR + CONVERGE_DUR) {
                    double prog = (double)(stage - SPIRAL_DUR - ORBIT_DUR) / CONVERGE_DUR;
                    for (Map.Entry<ArmorStand, Location> e : flyingEntities.entrySet()) {
                        ArmorStand as = e.getKey(); Location orbit = orbitLocations.get(as);
                        if (!as.isValid() || orbit == null) continue;
                        Location vis = orbit.clone().add(finalPoint.toVector().subtract(orbit.toVector()).multiply(prog));
                        as.teleport(vis.clone().subtract(0, ARMOR_STAND_HEAD_OFFSET, 0));
                        spawnTrailParticles(as, vis);
                    }
                }
                // Phase 4: finish
                else {
                    finished = true;
                    flyingEntities.keySet().forEach(as -> { if (as.isValid()) as.teleport(finalPoint.clone().subtract(0, ARMOR_STAND_HEAD_OFFSET, 0)); });
                    spawnConvergenceBurst(finalPoint);
                    summonBoss(altar, player);
                    new BukkitRunnable() {
                        @Override public void run() {
                            flyingEntities.keySet().forEach(as -> { if (as.isValid()) as.remove(); });
                            summoningAltars.remove(altar);
                        }
                    }.runTaskLater(plugin, 1L);
                    this.cancel();
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnTrailParticles(ArmorStand as, Location vis) {
        Particle t1 = getParticle("effects.particles.animation-trail", "ENCHANTMENT_TABLE");
        Particle t2 = getParticle("effects.particles.animation-trail-secondary", "END_ROD");
        if (t1 != null) as.getWorld().spawnParticle(t1, vis.clone().add(0, 0.2, 0), 1, 0.5, 0.5, 0.5, 0);
        if (t2 != null && as.getTicksLived() % 3 == 0)
            as.getWorld().spawnParticle(t2, vis.clone().add(0, 0.2, 0), 1, 0, 0, 0, 0);
    }

    private void spawnConvergenceBurst(Location loc) {
        playSoundFromConfig(loc, "effects.sounds.ritual-converge");
        Particle burst = getParticle("effects.particles.convergence-burst", "END_ROD");
        if (burst == null) return;
        for (int i = 0; i < 150; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vector v = new Vector(r.nextDouble(-1,1), r.nextDouble(-1,1), r.nextDouble(-1,1)).normalize().multiply(0.4);
            loc.getWorld().spawnParticle(burst, loc, 0, v.getX(), v.getY(), v.getZ(), 1.0);
        }
    }

    // =========================================================================
    // BOSS SUMMONING
    // =========================================================================

    private void summonBoss(Altar altar, Player player) {
        Location center = altar.getCenterLocation();
        if (center == null || center.getWorld() == null) return;

        lang.sendMessage(player, "altar-interaction.boss-spawned");
        Location spawnLoc = center.clone().add(0.5, 3, 0.5);
        String bossName = altar.getBossName();

        if (bossName.equalsIgnoreCase("DefaultBoss")) {
            spawnLoc.getWorld().spawn(spawnLoc, Zombie.class);
            plugin.getLogger().info("Altar '" + altar.getName() + "' summoned a default Zombie.");
        } else {
            try {
                MythicBukkit.inst().getAPIHelper().spawnMythicMob(bossName, spawnLoc, 1);
            } catch (InvalidMobTypeException e) {
                plugin.getErrorHandler().logError(
                    "Invalid MythicMob name '" + bossName + "' for altar '" + altar.getName() + "'. " +
                    "Check your MythicMobs files and altar configuration.",
                    "Altar Data Error");
                lang.sendMessage(player, "altar-interaction.error-invalid-boss", "%boss%", bossName);
                summoningAltars.remove(altar);
                return;
            }
        }

        playSoundFromConfig(center, "effects.sounds.summon-spawn");

        if (plugin.getConfig().getBoolean("altar.broadcast-summon.enabled", true)) {
            String msg = lang.getMessage("altar-interaction.boss-summon-broadcast");
            String displayName = bossName.equalsIgnoreCase("DefaultBoss") ? "Zombie" : bossName;
            final String finalMsg = msg.replace("%boss%", displayName).replace("%player%", player.getName());
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(finalMsg));
        }
    }

    // =========================================================================
    // BLOCK INTERACTION ENTRY POINT
    // =========================================================================

    public void handleBlockClick(Player player, Block clickedBlock) {
        if (clickedBlock == null) return;
        Location blockLoc = clickedBlock.getLocation();
        Altar altar = getAltarAt(blockLoc);
        if (altar == null) return;

        if (summoningAltars.contains(altar)) {
            lang.sendMessage(player, "altar-interaction.summoning");
            return;
        }

        // If there is an item on the clicked pedestal, try to retrieve it
        if (placedItemsDisplay.containsKey(blockLoc)) {
            retrieveItem(player, blockLoc);
            return;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getType().isAir()) return;

        if (isSameBlock(blockLoc, altar.getCenterLocation())) {
            // Clicked center block — attempt to activate
            boolean matches = matchesCentralItem(altar, inHand);
            if (matches) {
                if (isAltarReady(altar)) {
                    ItemStack toConsume = inHand.clone();
                    toConsume.setAmount(1);
                    inHand.setAmount(inHand.getAmount() - 1);
                    startSummoningAnimation(altar, player, toConsume);
                } else {
                    lang.sendMessage(player, "altar-interaction.not-ready");
                }
            } else {
                lang.sendMessage(player, "altar-interaction.wrong-item");
            }
        } else if (altar.getPedestalLocations().contains(blockLoc)) {
            placeItem(player, blockLoc, inHand);
        }
    }

    /**
     * Checks whether the item in the player's hand matches the altar's activation item,
     * preferring Nexo ID comparison when applicable.
     */
    private boolean matchesCentralItem(Altar altar, ItemStack inHand) {
        String nexoCentralId = altar.getCentralItemNexoId();
        if (nexoCentralId != null && nexo.isAvailable()) {
            return nexoCentralId.equals(nexo.getNexoId(inHand));
        }
        ItemStack template = altar.getCentralItem();
        if (template == null) return false;
        return nexo.itemsMatch(inHand, template);
    }

    // =========================================================================
    // ALTAR READINESS CHECK
    // =========================================================================

    private boolean isAltarReady(Altar altar) {
        boolean noStandard = altar.getRequiredItems().isEmpty();
        boolean noNexo     = altar.getRequiredItemNexoIds().isEmpty();
        if (noStandard && noNexo) return true;

        List<ItemStack> placed = placedItemsDisplay.values().stream()
                .map(Item::getItemStack).collect(Collectors.toList());

        // Check standard Bukkit required items
        for (Map.Entry<ItemStack, Integer> req : altar.getRequiredItems().entrySet()) {
            ItemStack reqItem = req.getKey();
            int needed = req.getValue();
            if (nexo.isAvailable() && nexo.isNexoItem(reqItem)) {
                String id = nexo.getNexoId(reqItem);
                long count = placed.stream().filter(p -> id != null && id.equals(nexo.getNexoId(p))).count();
                if (count < needed) return false;
            } else {
                long count = placed.stream().filter(p -> nexo.itemsMatch(p, reqItem)).count();
                if (count < needed) return false;
            }
        }

        // Check Nexo-only required items
        if (nexo.isAvailable()) {
            for (Map.Entry<String, Integer> req : altar.getRequiredItemNexoIds().entrySet()) {
                long count = placed.stream().filter(p -> req.getKey().equals(nexo.getNexoId(p))).count();
                if (count < req.getValue()) return false;
            }
        }

        return true;
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    public Altar getAltarAt(Location loc) {
        Location bl = loc.getBlock().getLocation();
        for (Altar altar : altarMap.values()) {
            if (isSameBlock(bl, altar.getCenterLocation())) return altar;
            if (altar.getPedestalLocations().contains(bl)) return altar;
        }
        return null;
    }

    public boolean isAltarBlock(Location loc) {
        return allAltarBlocks.contains(loc.getBlock().getLocation());
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (!Objects.equals(a.getWorld(), b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private void playSoundFromConfig(Location loc, String path) {
        String s = plugin.getConfig().getString(path);
        if (s == null || s.isEmpty()) return;
        String[] parts = s.split(",");
        try {
            Sound sound = Sound.valueOf(parts[0].trim().toUpperCase());
            float vol   = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
            loc.getWorld().playSound(loc, sound, vol, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getErrorHandler().logError(
                "Invalid sound name in config.yml at '" + path + "': " + s, "Configuration Error");
        }
    }

    private Particle getParticle(String path, String def) {
        String name = plugin.getConfig().getString(path, def);
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("none")) return null;
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getErrorHandler().logError(
                "Invalid particle name in config.yml at '" + path + "': " + name, "Configuration Error");
            return null;
        }
    }

    public void shutdown() {
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        peripheralParticleTasks.values().forEach(BukkitTask::cancel);
        peripheralParticleTasks.clear();
        new ArrayList<>(placedItemsDisplay.values()).forEach(Entity::remove);
        placedItemsDisplay.clear();
        summoningAltars.clear();
        itemPlacers.clear();
    }
}
