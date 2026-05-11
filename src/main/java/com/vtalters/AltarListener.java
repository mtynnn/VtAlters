/*
 * VtAlters - A plugin for summoning bosses via altars.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */

package com.vtalters;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class AltarListener implements Listener {

    private final VtAlters plugin;
    private final WandManager wandManager;

    public AltarListener(VtAlters plugin) {
        this.plugin = plugin;
        this.wandManager = plugin.getWandManager();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();

        if (wandManager.isWand(player.getInventory().getItemInMainHand())) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true); 
                wandManager.setSelection(player, event.getClickedBlock().getLocation());
            }
            return; 
        }

        AltarManager altarManager = plugin.getAltarManager();

        if (altarManager.isAltarBlock(event.getClickedBlock().getLocation())) {
             if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getHand() == EquipmentSlot.HAND) {
                altarManager.handleBlockClick(player, event.getClickedBlock());
                event.setCancelled(true);
             }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (wandManager.isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getAltarManager().isAltarBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.getAltarManager().isAltarBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.getAltarManager().isAltarBlock(block.getLocation()));
    }
}
