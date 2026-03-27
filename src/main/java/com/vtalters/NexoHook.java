/*
 * VtAlters - Plugin for summoning bosses via ritual altars.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */

package com.vtalters;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

/**
 * Optional bridge to the Nexo custom-item plugin.
 *
 * All Nexo API calls are isolated here so that if Nexo is not installed the
 * rest of the plugin continues to work without any ClassNotFoundExceptions.
 *
 * Usage:
 *   nexoHook.isAvailable()          → true when Nexo is loaded on the server
 *   nexoHook.isNexoItem(stack)      → true when the ItemStack is a Nexo item
 *   nexoHook.getNexoId(stack)       → "my_custom_item" or null
 *   nexoHook.buildNexoItem(id)      → ItemStack for that Nexo ID, or null
 *   nexoHook.itemsMatch(a, b)       → comparison aware of Nexo IDs
 *   nexoHook.getDisplayName(stack)  → human-readable name for messages
 */
public class NexoHook {

    private final boolean available;

    public NexoHook() {
        boolean loaded;
        try {
            Class.forName("com.nexomc.nexo.api.NexoItems");
            loaded = true;
        } catch (ClassNotFoundException e) {
            loaded = false;
        }
        this.available = loaded;
    }

    /** Returns true when the Nexo plugin is present and usable. */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the Nexo item ID for the given ItemStack, or null if it is not
     * a Nexo item or if Nexo is not installed.
     */
    public String getNexoId(ItemStack item) {
        if (!available || item == null) return null;
        try {
            return NexoItems.idFromItem(item);
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns true when the ItemStack belongs to Nexo's custom item registry. */
    public boolean isNexoItem(ItemStack item) {
        return getNexoId(item) != null;
    }

    /**
     * Builds and returns the ItemStack for a Nexo item ID.
     * Returns null when the ID does not exist or Nexo is not available.
     */
    public ItemStack buildNexoItem(String nexoId) {
        if (!available || nexoId == null || nexoId.isEmpty()) return null;
        try {
            ItemBuilder builder = NexoItems.itemFromId(nexoId);
            return builder != null ? builder.build() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Compares two ItemStacks with Nexo awareness.
     *
     * Rules (in order):
     *  1. Both are Nexo items  → compare by Nexo ID.
     *  2. Only one is Nexo     → always false.
     *  3. Neither is Nexo      → fall back to Bukkit's isSimilar().
     */
    public boolean itemsMatch(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;

        if (available) {
            String idA = getNexoId(a);
            String idB = getNexoId(b);

            if (idA != null && idB != null) return idA.equals(idB);
            if (idA != null || idB != null) return false;
        }

        return a.isSimilar(b);
    }

    /**
     * Returns a human-readable name for an item suitable for chat messages.
     * Priority: Nexo ID → display name → material name.
     */
    public String getDisplayName(ItemStack item) {
        if (item == null) return "Unknown";

        if (available) {
            String nexoId = getNexoId(item);
            if (nexoId != null) return "[Nexo] " + nexoId;
        }

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            // Use legacy display name string; Component API not available on Paper 1.17 target
            @SuppressWarnings("deprecation")
            String displayName = item.getItemMeta().getDisplayName();
            return displayName;
        }

        return item.getType().name().replace('_', ' ').toLowerCase();
    }
}
