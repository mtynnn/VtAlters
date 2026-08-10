/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.listener;

import com.valerinsmp.valtars.VAltarsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class AltarListener implements Listener {
    private final VAltarsPlugin plugin;

    public AltarListener(VAltarsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getHand() != EquipmentSlot.HAND) return;
        if (plugin.wands().isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                plugin.wands().select(event.getPlayer(), event.getClickedBlock().getLocation());
            }
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && plugin.altars().isAltarBlock(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            plugin.altars().handleBlockClick(event.getPlayer(), event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.wands().isWand(event.getPlayer().getInventory().getItemInMainHand())
                || plugin.altars().isAltarBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.altars().isAltarBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.altars().isAltarBlock(block.getLocation()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.refunds().deliver(event.getPlayer());
    }
}
