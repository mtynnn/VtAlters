/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.service;

import com.valerinsmp.valtars.VAltarsPlugin;
import com.valerinsmp.valtars.config.AltarRepository;
import com.valerinsmp.valtars.config.ConfigService;
import com.valerinsmp.valtars.domain.AltarDefinition;
import com.valerinsmp.valtars.domain.BlockKey;
import com.valerinsmp.valtars.domain.ItemRequirement;
import com.valerinsmp.valtars.domain.ItemSpec;
import com.valerinsmp.valtars.integration.MythicMobsIntegration;
import com.valerinsmp.valtars.integration.NexoIntegration;
import com.valerinsmp.valtars.message.MessageService;
import com.valerinsmp.valtars.ritual.RefundEntry;
import com.valerinsmp.valtars.ritual.RefundService;
import com.valerinsmp.valtars.ritual.RitualPresentation;
import com.valerinsmp.valtars.ritual.RitualSession;
import com.valerinsmp.valtars.ritual.RitualSessions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

public final class AltarManager {
    public enum MutationResult { SAVED, NOT_FOUND, ALREADY_EXISTS, BUSY, CONFLICT, INVALID }

    private final VAltarsPlugin plugin;
    private final AltarRepository repository;
    private final ConfigService config;
    private final MessageService messages;
    private final NexoIntegration nexo;
    private final MythicMobsIntegration mythic;
    private final RefundService refunds;
    private final RitualPresentation presentation;
    private final RitualSessions sessions = new RitualSessions();
    private final Map<String, AltarDefinition> altars = new LinkedHashMap<>();
    private final Map<BlockKey, String> altarBlocks = new HashMap<>();
    private final Map<BlockKey, Placement> placements = new LinkedHashMap<>();
    private final Map<String, Long> placementDeadlines = new HashMap<>();
    private final Map<BlockKey, BukkitTask> pedestalEffects = new HashMap<>();
    private final Map<String, TextDisplay> requirementHolograms = new HashMap<>();
    private BukkitTask readyEffect;

    public AltarManager(VAltarsPlugin plugin, AltarRepository repository, ConfigService config,
                        MessageService messages, NexoIntegration nexo, MythicMobsIntegration mythic,
                        RefundService refunds, RitualPresentation presentation) {
        this.plugin = plugin;
        this.repository = repository;
        this.config = config;
        this.messages = messages;
        this.nexo = nexo;
        this.mythic = mythic;
        this.refunds = refunds;
        this.presentation = presentation;
        altars.putAll(repository.load());
        rebuildBlockIndex();
        altars.values().forEach(this::refreshRequirementHologram);
        startReadyEffect();
    }

    public boolean isAltarBlock(Location location) {
        requirePrimaryThread();
        return altarBlocks.containsKey(BlockKey.from(location));
    }

    public void handleBlockClick(Player player, Block clicked) {
        requirePrimaryThread();
        BlockKey block = BlockKey.from(clicked.getLocation());
        AltarDefinition altar = altarAt(block);
        if (altar == null) return;
        if (sessions.contains(altar.name())) {
            messages.send(player, "altar-interaction.summoning");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Placement placement = placements.get(block);
        if (placement != null) {
            ItemRequirement requirement = requirementAt(altar, block);
            if (hand != null && !hand.getType().isAir()
                    && requirement != null
                    && nexo.matches(requirement.item(), placement.item())
                    && nexo.matches(requirement.item(), hand)
                    && placement.item().getAmount() < requirement.amount()) {
                topUp(player, altar, requirement, placement, hand);
                return;
            }
            retrieve(player, block);
            return;
        }

        if (hand == null || hand.getType().isAir()) return;
        if (block.equals(altar.center())) activate(player, altar, hand);
        else if (altar.pedestals().contains(block)) place(player, altar, block, hand);
    }

    public Collection<String> names() {
        return altars.values().stream().map(AltarDefinition::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public AltarDefinition altar(String name) {
        return altars.get(key(name));
    }

    public boolean busy(String name) {
        return sessions.contains(name) || placements.values().stream().anyMatch(p -> p.altarName().equalsIgnoreCase(name));
    }

    public MutationResult create(String name) {
        requirePrimaryThread();
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,32}")) return MutationResult.INVALID;
        if (altars.containsKey(key(name))) return MutationResult.ALREADY_EXISTS;
        return persist(AltarDefinition.empty(name));
    }

    public MutationResult delete(String name) {
        requirePrimaryThread();
        AltarDefinition altar = altar(name);
        if (altar == null) return MutationResult.NOT_FOUND;
        if (busy(name)) return MutationResult.BUSY;
        repository.delete(altar.name());
        removeRequirementHologram(altar.name());
        altars.remove(key(name));
        rebuildBlockIndex();
        return MutationResult.SAVED;
    }

    public MutationResult setCenter(String name, BlockKey center) {
        return mutate(name, altar -> {
            if (altar.pedestals().contains(center) || claimedByAnother(altar.name(), center)) return null;
            double maximum = config.snapshot().maxPedestalRadius();
            if (altar.pedestals().stream().anyMatch(p -> center.distanceSquared(p) > maximum * maximum)) return null;
            return altar.withCenter(center);
        });
    }

    public MutationResult setBoss(String name, String boss) {
        if (boss == null || boss.isBlank()) return MutationResult.INVALID;
        return mutate(name, altar -> altar.withBoss(boss));
    }

    public MutationResult setActivationItem(String name, ItemStack item, int amount) {
        if (item == null || item.getType().isAir() || amount <= 0 || amount > item.getMaxStackSize()) {
            return MutationResult.INVALID;
        }
        ItemSpec spec = optionalNexo(item).map(ItemSpec::nexo).orElseGet(() -> ItemSpec.vanilla(item));
        return mutate(name, altar -> altar.withActivationItem(spec, amount));
    }

    public MutationResult addPedestal(String name, BlockKey pedestal) {
        return mutate(name, altar -> {
            if (altar.center() == null || altar.center().equals(pedestal)
                    || altar.pedestals().contains(pedestal) || claimedByAnother(altar.name(), pedestal)) return null;
            double maximum = config.snapshot().maxPedestalRadius();
            if (altar.center().distanceSquared(pedestal) > maximum * maximum) return null;
            List<BlockKey> updated = new ArrayList<>(altar.pedestals());
            updated.add(pedestal);
            return altar.withPedestals(updated);
        });
    }

    public MutationResult removePedestal(String name, BlockKey pedestal) {
        return mutate(name, altar -> {
            if (!altar.pedestals().contains(pedestal)
                    || altar.requiredPedestalCount() >= altar.pedestals().size()) return null;
            List<BlockKey> updated = new ArrayList<>(altar.pedestals());
            updated.remove(pedestal);
            return altar.withPedestals(updated);
        });
    }

    public MutationResult clearPedestals(String name) {
        return mutate(name, altar -> altar.requiredItemCount() == 0 ? altar.withPedestals(List.of()) : null);
    }

    public MutationResult addRequirement(String name, ItemStack item, int amount) {
        if (item == null || item.getType().isAir() || amount <= 0 || amount > item.getMaxStackSize()) {
            return MutationResult.INVALID;
        }
        ItemSpec spec = optionalNexo(item).map(ItemSpec::nexo).orElseGet(() -> ItemSpec.vanilla(item));
        return mutate(name, altar -> {
            if (altar.requiredPedestalCount() >= altar.pedestals().size()) return null;
            List<ItemRequirement> updated = new ArrayList<>(altar.requirements());
            updated.add(new ItemRequirement(spec, amount));
            return altar.withRequirements(updated);
        });
    }

    public MutationResult removeRequirement(String name, ItemStack item) {
        if (item == null || item.getType().isAir()) return MutationResult.INVALID;
        return mutate(name, altar -> {
            List<ItemRequirement> updated = new ArrayList<>(altar.requirements());
            for (int index = updated.size() - 1; index >= 0; index--) {
                if (!nexo.matches(updated.get(index).item(), item)) continue;
                updated.remove(index);
                return altar.withRequirements(updated);
            }
            return null;
        });
    }

    public MutationResult clearRequirements(String name) {
        return mutate(name, altar -> altar.requirements().isEmpty() ? null : altar.withRequirements(List.of()));
    }

    public void shutdown() {
        requirePrimaryThread();
        if (readyEffect != null) readyEffect.cancel();
        pedestalEffects.values().forEach(BukkitTask::cancel);
        pedestalEffects.clear();
        presentation.cancelAll(new IllegalStateException("Plugin disabled during ritual"));
        for (RitualSession session : sessions.snapshot()) rollback(session, "disable");
        returnPlacements();
        requirementHolograms.values().forEach(Entity::remove);
        requirementHolograms.clear();
    }

    private void place(Player player, AltarDefinition altar, BlockKey block, ItemStack hand) {
        ItemRequirement requirement = requirementAt(altar, block);
        if (requirement == null || !nexo.matches(requirement.item(), hand)) {
            messages.send(player, "altar-interaction.wrong-pedestal-item",
                    Placeholder.component("item", nexo.displayComponent(hand)),
                    Placeholder.component("hint", requirement == null ? firstMissingHint(altar)
                            : nexo.displayComponent(requirement.item())));
            return;
        }
        Location base = block.location();
        if (base == null) {
            messages.send(player, "altar-interaction.world-unavailable");
            return;
        }
        int accepted = amountToPlace(hand.getAmount(), requirement.amount(), 0, hand.getMaxStackSize());
        ItemStack offered = amount(hand, accepted);
        Location displayLocation = base.clone().add(0.5, config.snapshot().ritual().pedestalHeight() - 0.2, 0.5);
        Item display = base.getWorld().dropItem(displayLocation, offered);
        display.setPickupDelay(Integer.MAX_VALUE);
        display.setGravity(false);
        display.setVelocity(new Vector());
        placements.put(block, new Placement(altar.name(), player.getUniqueId(), block, offered, display));
        placementDeadlines.put(key(altar.name()), deadlineAfter(System.nanoTime(), config.snapshot().placementIdleSeconds()));
        startPedestalEffect(block);
        hand.setAmount(hand.getAmount() - accepted);
        presentation.playSound(base, "BLOCK_END_PORTAL_FRAME_FILL,1,1");
        refreshRequirementHologram(altar);
    }

    private void topUp(Player player, AltarDefinition altar, ItemRequirement requirement,
                       Placement placement, ItemStack hand) {
        if (!placement.owner().equals(player.getUniqueId())) {
            messages.send(player, "altar-interaction.not-your-item");
            return;
        }
        ItemStack combined = placement.item();
        int accepted = amountToPlace(hand.getAmount(), requirement.amount() - combined.getAmount(),
                combined.getAmount(), combined.getMaxStackSize());
        if (accepted <= 0) {
            messages.send(player, "altar-interaction.pedestal-full");
            return;
        }
        combined.setAmount(combined.getAmount() + accepted);
        placement.display().setItemStack(combined);
        placements.put(placement.block(), new Placement(placement.altarName(), placement.owner(),
                placement.block(), combined, placement.display()));
        placementDeadlines.put(key(altar.name()), deadlineAfter(System.nanoTime(), config.snapshot().placementIdleSeconds()));
        hand.setAmount(hand.getAmount() - accepted);
        Location location = placement.block().location();
        if (location != null) presentation.playSound(location, "BLOCK_END_PORTAL_FRAME_FILL,1,1");
        refreshRequirementHologram(altar);
    }

    private void retrieve(Player player, BlockKey block) {
        Placement placement = placements.get(block);
        if (placement == null) return;
        if (config.snapshot().preventItemTheft() && !placement.owner().equals(player.getUniqueId())) {
            messages.send(player, "altar-interaction.not-your-item");
            return;
        }
        RefundEntry refund = RefundEntry.create(placement.owner(), block, placement.item());
        refunds.queue(List.of(refund));
        removePlacement(placement);
        Location location = block.location();
        if (location != null) presentation.playSound(location, "ENTITY_ITEM_PICKUP,1,1");
        if (refunds.pendingCount() > 0) messages.send(player, "altar-interaction.refund-pending");
    }

    private void activate(Player player, AltarDefinition altar, ItemStack hand) {
        if (altar.center() == null || altar.activationItem() == null || !nexo.matches(altar.activationItem(), hand)) {
            messages.send(player, "altar-interaction.wrong-item");
            return;
        }
        if (hand.getAmount() < altar.activationAmount()) {
            messages.send(player, "altar-interaction.not-enough-activation",
                    Placeholder.unparsed("amount", String.valueOf(altar.activationAmount())));
            return;
        }
        if (!ready(altar)) {
            messages.send(player, "altar-interaction.not-ready");
            return;
        }

        List<Placement> contributions = placements.values().stream()
                .filter(placement -> placement.altarName().equalsIgnoreCase(altar.name()))
                .sorted(Comparator.comparing(placement -> placement.block().toString())).toList();
        ItemStack central = amount(hand, altar.activationAmount());
        List<RefundEntry> refundEntries = new ArrayList<>();
        List<ItemStack> visualItems = new ArrayList<>();
        List<BlockKey> visualOrigins = new ArrayList<>();
        Map<UUID, Integer> points = new HashMap<>();

        refundEntries.add(RefundEntry.create(player.getUniqueId(), altar.center(), central));
        visualItems.add(central);
        visualOrigins.add(altar.center());
        points.merge(player.getUniqueId(), 2, Integer::sum);
        for (Placement placement : contributions) {
            refundEntries.add(RefundEntry.create(placement.owner(), placement.block(), placement.item()));
            visualItems.add(placement.item());
            visualOrigins.add(placement.block());
            points.merge(placement.owner(), placement.item().getAmount(), Integer::sum);
        }

        RitualSession session = new RitualSession(altar.name(), player.getUniqueId(), player.getName(),
                altar.bossName(), config.snapshot().ritual(), refundEntries, visualItems, points);
        if (!sessions.begin(session)) {
            messages.send(player, "altar-interaction.summoning");
            return;
        }

        try {
            hand.setAmount(hand.getAmount() - altar.activationAmount());
            contributions.forEach(this::removePlacement);
            messages.send(player, "altar-interaction.ritual-start");
            presentation.play(altar, session, visualOrigins,
                    () -> complete(altar, session), throwable -> fail(session, throwable));
        } catch (Throwable throwable) {
            fail(session, throwable);
        }
    }

    private void complete(AltarDefinition altar, RitualSession session) {
        if (session.state() != RitualSession.State.ACTIVE || sessions.get(altar.name()) != session) return;
        Location center = altar.center().location();
        if (center == null) {
            fail(session, new IllegalStateException("Altar world unloaded before spawn"));
            return;
        }
        LivingEntity boss;
        try {
            boss = mythic.spawn(session.bossName(), center.clone().add(0.5, 3, 0.5));
        } catch (Throwable throwable) {
            fail(session, throwable);
            return;
        }

        // Commit occurs only after MythicMobs returned a valid, live entity.
        if (!session.commit()) {
            boss.remove();
            return;
        }
        sessions.remove(altar.name());
        refreshRequirementHologram(altar);

        try {
            attachRitualData(boss, altar, session.contributorPoints());
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Boss spawned but ritual metadata failed for '" + altar.name() + "': " + throwable.getMessage());
        }
        try {
            presentation.playSound(center, session.settings().spawnSound());
            Player activator = Bukkit.getPlayer(session.activator());
            if (activator != null) messages.send(activator, "altar-interaction.boss-spawned");
            if (session.settings().broadcastSummon()) broadcast(session);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Committed ritual presentation failed for '" + altar.name() + "': " + throwable.getMessage());
        }
    }

    private void fail(RitualSession session, Throwable throwable) {
        if (session.state() != RitualSession.State.ACTIVE) {
            plugin.getLogger().warning("Committed ritual cleanup failed for altar '" + session.altarName() + "': "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            return;
        }
        plugin.getLogger().warning("Ritual for altar '" + session.altarName() + "' rolled back: "
                + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        rollback(session, "failure");
        Player activator = Bukkit.getPlayer(session.activator());
        if (activator != null) {
            messages.send(activator, "altar-interaction.error-invalid-boss",
                    Placeholder.unparsed("boss", session.bossName()));
        }
    }

    private void rollback(RitualSession session, String reason) {
        if (session.state() != RitualSession.State.ACTIVE) return;
        try {
            refunds.queue(session.refunds());
            session.rollback();
            sessions.remove(session.altarName());
            refreshRequirementHologram(altar(session.altarName()));
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not persist rollback for altar '" + session.altarName()
                    + "' during " + reason + "; session remains ACTIVE for retry: " + exception.getMessage());
        }
    }

    private void returnPlacements() {
        List<Placement> snapshot = new ArrayList<>(placements.values());
        List<RefundEntry> entries = snapshot.stream()
                .map(placement -> RefundEntry.create(placement.owner(), placement.block(), placement.item())).toList();
        if (!entries.isEmpty()) refunds.queue(entries);
        snapshot.forEach(this::removePlacement);
        placementDeadlines.clear();
    }

    private void expireAbandonedPlacements() {
        long now = System.nanoTime();
        for (AltarDefinition altar : altars.values()) {
            List<Placement> contributions = placements.values().stream()
                    .filter(placement -> placement.altarName().equalsIgnoreCase(altar.name())).toList();
            if (contributions.isEmpty() || sessions.contains(altar.name())) continue;
            long deadline = placementDeadlines.getOrDefault(key(altar.name()), now);
            boolean contributorNearby = contributions.stream().anyMatch(this::ownerNearby);
            if (!shouldExpire(now, deadline, contributorNearby)) continue;

            try {
                refunds.queue(contributions.stream()
                        .map(placement -> RefundEntry.create(placement.owner(), placement.block(), placement.item()))
                        .toList());
                contributions.forEach(this::removePlacement);
                contributions.stream().map(Placement::owner).distinct()
                        .map(Bukkit::getPlayer).filter(Objects::nonNull).filter(Player::isOnline)
                        .forEach(player -> messages.send(player, "altar-interaction.placements-expired"));
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Could not persist expired pedestal items for altar '"
                        + altar.name() + "'; placements remain: " + exception.getMessage());
            }
        }
    }

    private boolean ownerNearby(Placement placement) {
        Player owner = Bukkit.getPlayer(placement.owner());
        Location pedestal = placement.block().location();
        if (owner == null || !owner.isOnline() || pedestal == null
                || owner.getWorld() != pedestal.getWorld()) return false;
        double maximum = config.snapshot().placementMaxPlayerDistance();
        return owner.getLocation().distanceSquared(pedestal) <= maximum * maximum;
    }

    static boolean shouldExpire(long now, long deadline, boolean contributorNearby) {
        return now >= deadline || !contributorNearby;
    }

    static long deadlineAfter(long now, int seconds) {
        return now + TimeUnit.SECONDS.toNanos(seconds);
    }

    static int amountToPlace(int handAmount, int missingAmount, int existingAmount, int maxStackSize) {
        return Math.max(0, Math.min(Math.min(handAmount, missingAmount), maxStackSize - existingAmount));
    }

    static ItemRequirement requirementAt(AltarDefinition altar, BlockKey pedestal) {
        int index = altar.pedestals().indexOf(pedestal);
        return index >= 0 && index < altar.requirements().size() ? altar.requirements().get(index) : null;
    }

    static int firstIncompleteIndex(AltarDefinition altar, IntUnaryOperator placedAmount) {
        for (int index = 0; index < altar.requirements().size(); index++) {
            if (placedAmount.applyAsInt(index) < altar.requirements().get(index).amount()) return index;
        }
        return -1;
    }

    private boolean ready(AltarDefinition altar) {
        if (altar.center() == null || altar.activationItem() == null || altar.requirements().isEmpty()) return false;
        for (int index = 0; index < altar.requirements().size(); index++) {
            ItemRequirement requirement = altar.requirements().get(index);
            Placement placement = placements.get(altar.pedestals().get(index));
            if (placement == null || !placement.altarName().equalsIgnoreCase(altar.name())
                    || !nexo.matches(requirement.item(), placement.item())
                    || placement.item().getAmount() < requirement.amount()) return false;
        }
        return true;
    }

    private Component firstMissingHint(AltarDefinition altar) {
        for (int index = 0; index < altar.requirements().size(); index++) {
            ItemRequirement requirement = altar.requirements().get(index);
            Placement placement = placements.get(altar.pedestals().get(index));
            if (placement == null || !nexo.matches(requirement.item(), placement.item())
                    || placement.item().getAmount() < requirement.amount()) return nexo.displayComponent(requirement.item());
        }
        return Component.text("?");
    }

    private Optional<String> optionalNexo(ItemStack item) {
        return Optional.ofNullable(nexo.id(item));
    }

    private MutationResult mutate(String name, UnaryOperator<AltarDefinition> mutation) {
        requirePrimaryThread();
        AltarDefinition current = altar(name);
        if (current == null) return MutationResult.NOT_FOUND;
        if (busy(name)) return MutationResult.BUSY;
        AltarDefinition updated = mutation.apply(current);
        return updated == null ? MutationResult.CONFLICT : persist(updated);
    }

    private MutationResult persist(AltarDefinition altar) {
        if (altar.requiredPedestalCount() > altar.pedestals().size()) return MutationResult.INVALID;
        repository.save(altar);
        altars.put(key(altar.name()), altar);
        rebuildBlockIndex();
        refreshRequirementHologram(altar);
        return MutationResult.SAVED;
    }

    private boolean claimedByAnother(String altarName, BlockKey block) {
        String owner = altarBlocks.get(block);
        return owner != null && !owner.equalsIgnoreCase(altarName);
    }

    private AltarDefinition altarAt(BlockKey block) {
        String name = altarBlocks.get(block);
        return name == null ? null : altar(name);
    }

    private void rebuildBlockIndex() {
        altarBlocks.clear();
        for (AltarDefinition altar : altars.values()) {
            if (altar.center() != null) altarBlocks.put(altar.center(), altar.name());
            for (BlockKey pedestal : altar.pedestals()) altarBlocks.put(pedestal, altar.name());
        }
    }

    private void startReadyEffect() {
        readyEffect = new BukkitRunnable() {
            @Override
            public void run() {
                expireAbandonedPlacements();
                var settings = config.snapshot().ritual();
                for (AltarDefinition altar : altars.values()) {
                    if (sessions.contains(altar.name()) || altar.center() == null || !ready(altar)) continue;
                    Location location = altar.center().location();
                    if (location != null) presentation.particle(location.add(0.5, settings.readyParticleHeight(), 0.5),
                            settings.readyParticle(), 5, 0.3);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startPedestalEffect(BlockKey block) {
        stopPedestalEffect(block);
        pedestalEffects.put(block, new BukkitRunnable() {
            private double angle;

            @Override
            public void run() {
                Location base = block.location();
                if (base == null || !placements.containsKey(block)) {
                    stopPedestalEffect(block);
                    return;
                }
                angle += Math.PI / 16;
                var settings = config.snapshot().ritual();
                presentation.particle(base.add(0.5 + Math.cos(angle) * 0.8, settings.pedestalHeight(),
                        0.5 + Math.sin(angle) * 0.8), settings.pedestalParticle(), 1, 0.0);
            }
        }.runTaskTimer(plugin, 0L, 2L));
    }

    private void stopPedestalEffect(BlockKey block) {
        BukkitTask effect = pedestalEffects.remove(block);
        if (effect != null) effect.cancel();
    }

    private void removePlacement(Placement placement) {
        placements.remove(placement.block(), placement);
        if (placements.values().stream().noneMatch(existing ->
                existing.altarName().equalsIgnoreCase(placement.altarName()))) {
            placementDeadlines.remove(key(placement.altarName()));
        }
        stopPedestalEffect(placement.block());
        if (placement.display().isValid()) placement.display().remove();
        refreshRequirementHologram(altar(placement.altarName()));
    }

    private void refreshRequirementHologram(AltarDefinition altar) {
        if (altar == null) return;
        removeRequirementHologram(altar.name());
        if (sessions.contains(altar.name())) return;
        int index = firstIncompleteIndex(altar, current -> {
            ItemRequirement requirement = altar.requirements().get(current);
            Placement placement = placements.get(altar.pedestals().get(current));
            return placement != null && nexo.matches(requirement.item(), placement.item())
                    ? placement.item().getAmount() : 0;
        });
        if (index >= 0) {
            ItemRequirement requirement = altar.requirements().get(index);
            BlockKey pedestal = altar.pedestals().get(index);
            Placement placement = placements.get(pedestal);
            boolean matching = placement != null && nexo.matches(requirement.item(), placement.item());
            int placed = matching ? placement.item().getAmount() : 0;
            Location base = pedestal.location();
            if (base == null || base.getWorld() == null) return;
            TextDisplay hologram = base.getWorld().spawn(base.clone().add(0.5,
                    config.snapshot().ritual().pedestalHeight() + 0.75, 0.5), TextDisplay.class);
            hologram.text(messages.component("altar-interaction.pedestal-hologram",
                    Placeholder.unparsed("amount", String.valueOf(requirement.amount() - placed)),
                    Placeholder.component("item", nexo.displayComponent(requirement.item()))));
            hologram.setBillboard(Display.Billboard.CENTER);
            hologram.setAlignment(TextDisplay.TextAlignment.CENTER);
            hologram.setShadowed(true);
            hologram.setSeeThrough(true);
            hologram.setDefaultBackground(false);
            hologram.setGravity(false);
            hologram.setPersistent(false);
            requirementHolograms.put(key(altar.name()), hologram);
        }
    }

    private void removeRequirementHologram(String altarName) {
        TextDisplay hologram = requirementHolograms.remove(key(altarName));
        if (hologram != null && hologram.isValid()) hologram.remove();
    }

    private void broadcast(RitualSession session) {
        Bukkit.getOnlinePlayers().forEach(player -> messages.send(player, "altar-interaction.boss-summon-broadcast",
                Placeholder.unparsed("player", session.activatorName()),
                Placeholder.unparsed("boss", session.bossName())));
    }

    private void attachRitualData(LivingEntity boss, AltarDefinition altar, Map<UUID, Integer> contributors) {
        boss.getPersistentDataContainer().set(new NamespacedKey(plugin, "ritual"), PersistentDataType.BYTE, (byte) 1);
        boss.getPersistentDataContainer().set(new NamespacedKey(plugin, "ritual_altar"),
                PersistentDataType.STRING, altar.name());
        String serialized = contributors.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((left, right) -> left + ";" + right).orElse("");
        boss.getPersistentDataContainer().set(new NamespacedKey(plugin, "ritual_contributors"),
                PersistentDataType.STRING, serialized);
    }

    private ItemStack amount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Altar access must run on the primary thread");
    }

    private record Placement(String altarName, UUID owner, BlockKey block, ItemStack item, Item display) {
        private Placement {
            Objects.requireNonNull(altarName);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(block);
            item = item.clone();
            Objects.requireNonNull(display);
        }

        @Override
        public ItemStack item() {
            return item.clone();
        }
    }
}
