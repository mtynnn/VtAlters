/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.gui;

import com.valerinsmp.valtars.VAltarsPlugin;
import com.valerinsmp.valtars.domain.AltarDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AltarBrowser implements Listener {
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS = 45;
    private static final int CLOSE = 49;
    private static final int NEXT = 53;

    private final VAltarsPlugin plugin;

    public AltarBrowser(VAltarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int requestedPage) {
        List<String> names = List.copyOf(plugin.altars().names());
        int pages = pageCount(names.size());
        int page = clampPage(names.size(), requestedPage);
        BrowserHolder holder = new BrowserHolder(page, pages);
        Inventory inventory = Bukkit.createInventory(holder, 54, gui(plugin.messages().component("gui.title",
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages)))));
        holder.bind(inventory);

        names.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE).forEach(name -> {
            int slot = holder.altars.size();
            AltarDefinition altar = plugin.altars().altar(name);
            holder.altars.put(slot, name);
            inventory.setItem(slot, altarIcon(altar));
        });
        if (page > 1) inventory.setItem(PREVIOUS, control(Material.ARROW, "gui.previous"));
        inventory.setItem(CLOSE, control(Material.BARRIER, "gui.close"));
        if (page < pages) inventory.setItem(NEXT, control(Material.ARROW, "gui.next"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof BrowserHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT
                || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        String altar = holder.altars.get(event.getRawSlot());
        if (altar != null) {
            Bukkit.getScheduler().runTask(plugin, () -> teleport(player, altar));
        } else if (event.getRawSlot() == PREVIOUS && holder.page > 1) {
            Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.page - 1));
        } else if (event.getRawSlot() == NEXT && holder.page < holder.pages) {
            Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.page + 1));
        } else if (event.getRawSlot() == CLOSE) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof BrowserHolder) event.setCancelled(true);
    }

    private void teleport(Player player, String name) {
        AltarDefinition altar = plugin.altars().altar(name);
        Location target = altar == null || altar.center() == null ? null : altar.center().location();
        if (target == null) {
            plugin.messages().send(player, "gui.world-unavailable", Placeholder.unparsed("name", name));
            return;
        }
        target.add(0.5, 1.0, 0.5).setRotation(player.getYaw(), player.getPitch());
        player.closeInventory();
        if (!player.teleport(target)) {
            plugin.messages().send(player, "gui.teleport-failed", Placeholder.unparsed("name", name));
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        plugin.messages().send(player, "gui.teleported", Placeholder.unparsed("name", name));
    }

    private ItemStack altarIcon(AltarDefinition altar) {
        boolean located = altar != null && altar.center() != null;
        ItemStack icon = new ItemStack(located ? Material.RESPAWN_ANCHOR : Material.BARRIER);
        ItemMeta meta = icon.getItemMeta();
        String name = altar == null ? "?" : altar.name();
        meta.displayName(gui(plugin.messages().component("gui.altar-name", Placeholder.unparsed("name", name))));
        if (altar != null) {
            Component state = plugin.messages().component(plugin.altars().busy(name) ? "gui.state-busy" : "gui.state-available");
            String world = located ? altar.center().world() : "?";
            String coordinates = located ? altar.center().x() + ", " + altar.center().y() + ", " + altar.center().z() : "?";
            meta.lore(plugin.messages().lines("gui.altar-lore",
                    Placeholder.unparsed("boss", altar.bossName()),
                    Placeholder.unparsed("world", world),
                    Placeholder.unparsed("coordinates", coordinates),
                    Placeholder.unparsed("pedestals", String.valueOf(altar.pedestals().size())),
                    Placeholder.unparsed("activation", altar.activationItem() == null
                            ? "-" : String.valueOf(altar.activationAmount())),
                    Placeholder.component("state", state)).stream().map(this::gui).toList());
        }
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack control(Material material, String key) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(gui(plugin.messages().component(key)));
        item.setItemMeta(meta);
        return item;
    }

    private Component gui(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    static int pageCount(int altarCount) {
        return Math.max(1, (altarCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static int clampPage(int altarCount, int requestedPage) {
        return Math.max(1, Math.min(requestedPage, pageCount(altarCount)));
    }

    private static final class BrowserHolder implements InventoryHolder {
        private final int page;
        private final int pages;
        private final Map<Integer, String> altars = new HashMap<>();
        private Inventory inventory;

        private BrowserHolder(int page, int pages) {
            this.page = page;
            this.pages = pages;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return Objects.requireNonNull(inventory, "inventory not bound");
        }
    }
}
