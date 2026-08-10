/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.domain;

import java.util.List;
import java.util.Objects;

public record AltarDefinition(
        String name,
        String bossName,
        BlockKey center,
        ItemSpec activationItem,
        int activationAmount,
        List<ItemRequirement> requirements,
        List<BlockKey> pedestals
) {
    public AltarDefinition {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Altar name cannot be blank");
        bossName = bossName == null || bossName.isBlank() ? "DefaultBoss" : bossName;
        if (activationItem == null) activationAmount = 1;
        else if (activationAmount <= 0) throw new IllegalArgumentException("Activation amount must be positive");
        requirements = List.copyOf(requirements == null ? List.of() : requirements);
        pedestals = List.copyOf(pedestals == null ? List.of() : pedestals);
    }

    public AltarDefinition(String name, String bossName, BlockKey center, ItemSpec activationItem,
                           List<ItemRequirement> requirements, List<BlockKey> pedestals) {
        this(name, bossName, center, activationItem, 1, requirements, pedestals);
    }

    public static AltarDefinition empty(String name) {
        return new AltarDefinition(name, "DefaultBoss", null, null, 1, List.of(), List.of());
    }

    public int requiredItemCount() {
        return requirements.stream().mapToInt(ItemRequirement::amount).sum();
    }

    public int requiredPedestalCount() {
        return requirements.size();
    }

    public AltarDefinition withBoss(String boss) {
        return new AltarDefinition(name, boss, center, activationItem, activationAmount, requirements, pedestals);
    }

    public AltarDefinition withCenter(BlockKey newCenter) {
        return new AltarDefinition(name, bossName, newCenter, activationItem, activationAmount, requirements, pedestals);
    }

    public AltarDefinition withActivationItem(ItemSpec item) {
        return withActivationItem(item, 1);
    }

    public AltarDefinition withActivationItem(ItemSpec item, int amount) {
        return new AltarDefinition(name, bossName, center, item, amount, requirements, pedestals);
    }

    public AltarDefinition withRequirements(List<ItemRequirement> items) {
        return new AltarDefinition(name, bossName, center, activationItem, activationAmount, items, pedestals);
    }

    public AltarDefinition withPedestals(List<BlockKey> locations) {
        return new AltarDefinition(name, bossName, center, activationItem, activationAmount, requirements, locations);
    }
}
