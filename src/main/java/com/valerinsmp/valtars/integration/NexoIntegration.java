/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.integration;

import com.nexomc.nexo.api.NexoItems;
import com.valerinsmp.valtars.VAltarsPlugin;
import com.valerinsmp.valtars.domain.ItemSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public final class NexoIntegration {
    private final VAltarsPlugin plugin;

    public NexoIntegration(VAltarsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        requirePrimaryThread();
        return Bukkit.getPluginManager().isPluginEnabled("Nexo");
    }

    public String id(ItemStack item) {
        requirePrimaryThread();
        if (!available() || item == null || item.getType().isAir()) return null;
        try {
            return NexoItems.idFromItem(item);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Nexo item lookup failed: " + exception.getMessage());
            return null;
        }
    }

    public boolean matches(ItemSpec expected, ItemStack actual) {
        requirePrimaryThread();
        if (expected == null || actual == null || actual.getType().isAir()) return false;
        String actualNexo = id(actual);
        if (expected.isNexo()) return actualNexo != null && expected.nexoId().equals(actualNexo);
        if (actualNexo != null) return false;
        return expected.vanilla().isSimilar(actual);
    }

    public Component displayComponent(ItemStack item) {
        requirePrimaryThread();
        if (item == null || item.getType().isAir()) return Component.text("?");
        String nexoId = id(item);
        return nexoId == null ? visibleName(item) : displayComponent(ItemSpec.nexo(nexoId));
    }

    public Component displayComponent(ItemSpec item) {
        requirePrimaryThread();
        if (item == null) return Component.text("?");
        if (!item.isNexo()) return visibleName(item.vanilla());
        if (!available()) return Component.text(item.nexoId());
        try {
            var builder = NexoItems.itemFromId(item.nexoId());
            if (builder != null) return visibleName(builder.build());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Nexo item display lookup failed for '" + item.nexoId()
                    + "': " + exception.getMessage());
        }
        return Component.text(item.nexoId());
    }

    private Component visibleName(ItemStack item) {
        return item.effectiveName();
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Nexo access must run on the primary thread");
    }
}
