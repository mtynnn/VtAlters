/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.domain;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class ItemSpec {
    private final ItemStack vanilla;
    private final String nexoId;

    private ItemSpec(ItemStack vanilla, String nexoId) {
        this.vanilla = vanilla == null ? null : one(vanilla);
        this.nexoId = nexoId;
        if ((this.vanilla == null) == (nexoId == null || nexoId.isBlank())) {
            throw new IllegalArgumentException("An item specification must contain exactly one item type");
        }
    }

    public static ItemSpec vanilla(ItemStack item) {
        return new ItemSpec(Objects.requireNonNull(item, "item"), null);
    }

    public static ItemSpec nexo(String id) {
        return new ItemSpec(null, Objects.requireNonNull(id, "id"));
    }

    public boolean isNexo() {
        return nexoId != null;
    }

    public String nexoId() {
        return nexoId;
    }

    public ItemStack vanilla() {
        return vanilla == null ? null : vanilla.clone();
    }

    public boolean sameType(ItemSpec other) {
        Objects.requireNonNull(other, "other");
        if (isNexo() || other.isNexo()) {
            return isNexo() && other.isNexo() && nexoId.equals(other.nexoId);
        }
        return vanilla.isSimilar(other.vanilla);
    }

    private static ItemStack one(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }
}
