/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.ritual;

import com.valerinsmp.valtars.domain.BlockKey;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

public record RefundEntry(UUID id, UUID owner, BlockKey source, ItemStack item) {
    public RefundEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(item, "item");
        item = item.clone();
    }

    public static RefundEntry create(UUID owner, BlockKey source, ItemStack item) {
        return new RefundEntry(UUID.randomUUID(), owner, source, item);
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }
}
