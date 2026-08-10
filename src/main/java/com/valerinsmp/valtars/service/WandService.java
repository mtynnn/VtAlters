/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.service;

import com.valerinsmp.valtars.VAltarsPlugin;
import com.valerinsmp.valtars.domain.BlockKey;
import com.valerinsmp.valtars.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WandService {
    private final MessageService messages;
    private final NamespacedKey wandKey;
    private final Map<UUID, BlockKey> selections = new HashMap<>();

    public WandService(VAltarsPlugin plugin, MessageService messages) {
        this.messages = messages;
        this.wandKey = new NamespacedKey(plugin, "setup_wand");
    }

    public void give(Player player) {
        ItemStack wand = create();
        player.getInventory().addItem(wand).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        messages.send(player, "wand.given");
    }

    public boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public void select(Player player, Location location) {
        BlockKey selected = BlockKey.from(location);
        if (selected.equals(selections.put(player.getUniqueId(), selected))) return;
        messages.send(player, "wand.selection",
                Placeholder.unparsed("x", String.valueOf(selected.x())),
                Placeholder.unparsed("y", String.valueOf(selected.y())),
                Placeholder.unparsed("z", String.valueOf(selected.z())));
    }

    public BlockKey selection(Player player) {
        return selections.get(player.getUniqueId());
    }

    private ItemStack create() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        TextColor primary = TextColor.fromHexString("#FFD166");
        meta.displayName(Component.text("vAltars | Varita", primary, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Clic izquierdo o derecho", primary).decoration(TextDecoration.ITALIC, false),
                Component.text("para seleccionar un bloque.", primary).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
