/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.ritual;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RitualSession {
    public enum State { ACTIVE, COMMITTED, ROLLED_BACK }

    private final String altarName;
    private final UUID activator;
    private final String activatorName;
    private final String bossName;
    private final RitualSettings settings;
    private final List<RefundEntry> refunds;
    private final List<ItemStack> visualItems;
    private final Map<UUID, Integer> contributorPoints;
    private State state = State.ACTIVE;

    public RitualSession(
            String altarName,
            UUID activator,
            String activatorName,
            String bossName,
            RitualSettings settings,
            List<RefundEntry> refunds,
            List<ItemStack> visualItems,
            Map<UUID, Integer> contributorPoints
    ) {
        this.altarName = Objects.requireNonNull(altarName, "altarName");
        this.activator = Objects.requireNonNull(activator, "activator");
        this.activatorName = Objects.requireNonNull(activatorName, "activatorName");
        this.bossName = Objects.requireNonNull(bossName, "bossName");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.refunds = List.copyOf(refunds);
        this.visualItems = visualItems.stream().map(ItemStack::clone).toList();
        this.contributorPoints = Map.copyOf(contributorPoints);
    }

    public boolean commit() {
        if (state != State.ACTIVE) return false;
        state = State.COMMITTED;
        return true;
    }

    public List<RefundEntry> rollback() {
        if (state != State.ACTIVE) return List.of();
        state = State.ROLLED_BACK;
        return refunds;
    }

    public String altarName() { return altarName; }
    public UUID activator() { return activator; }
    public String activatorName() { return activatorName; }
    public String bossName() { return bossName; }
    public RitualSettings settings() { return settings; }
    public State state() { return state; }
    public Map<UUID, Integer> contributorPoints() { return contributorPoints; }

    public List<RefundEntry> refunds() {
        return refunds;
    }

    public List<ItemStack> visualItems() {
        return visualItems.stream().map(ItemStack::clone).toList();
    }
}
