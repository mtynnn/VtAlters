/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.ritual;

import com.valerinsmp.valtars.VAltarsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Durable claim/ack delivery. The claim is saved before inventory mutation and the
 * tagged inventory is saved before acknowledging the mailbox.
 */
public final class RefundService {
    private final VAltarsPlugin plugin;
    private final RefundMailbox mailbox;
    private final NamespacedKey refundIdKey;

    public RefundService(VAltarsPlugin plugin, RefundMailbox mailbox) {
        this.plugin = plugin;
        this.mailbox = mailbox;
        this.refundIdKey = new NamespacedKey(plugin, "refund_id");
    }

    public void queue(Collection<RefundEntry> entries) {
        requirePrimaryThread();
        if (entries.isEmpty()) return;
        mailbox.enqueue(entries);
        entries.stream().map(RefundEntry::owner).distinct().forEach(owner -> {
            try {
                Player player = Bukkit.getPlayer(owner);
                if (player != null && player.isOnline()) deliver(player);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Refunds for " + owner
                        + " are durable but immediate delivery failed: " + exception.getMessage());
            }
        });
    }

    public void deliver(Player player) {
        requirePrimaryThread();
        List<RefundMailbox.PendingRefund> pending = mailbox.forOwner(player.getUniqueId());
        cleanupConfirmedTags(player, pending);
        for (RefundMailbox.PendingRefund refund : pending) deliver(player, refund);
    }

    public int pendingCount() {
        return mailbox.size();
    }

    private void deliver(Player player, RefundMailbox.PendingRefund pending) {
        RefundEntry entry = pending.entry();
        try {
            int previouslyDelivered = taggedAmount(player, entry.id());
            if (previouslyDelivered > 0) {
                acknowledgeAndUntag(player, entry, previouslyDelivered);
                return;
            }
            if (pending.state() == RefundMailbox.State.CLAIMED) mailbox.pending(entry.id());

            mailbox.claim(entry.id());
            ItemStack tagged = tagged(entry.item(), entry.id());
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(tagged);
            player.saveData();
            int accepted = acceptedAmount(entry.item().getAmount(), leftovers);
            int persisted = taggedAmount(player, entry.id());
            if (persisted != accepted) {
                throw new IllegalStateException("Inventory accepted " + accepted
                        + " items but persisted tag count is " + persisted);
            }
            if (persisted == 0) {
                mailbox.pending(entry.id());
                return;
            }
            acknowledgeAndUntag(player, entry, persisted);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Refund " + entry.id() + " remains durable for retry after delivery failure: "
                    + exception.getMessage());
        }
    }

    static int acceptedAmount(int requested, Map<Integer, ItemStack> leftovers) {
        int rejected = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        return Math.max(0, requested - rejected);
    }

    private void cleanupConfirmedTags(Player player, List<RefundMailbox.PendingRefund> pending) {
        Set<UUID> durableIds = new HashSet<>();
        pending.forEach(refund -> durableIds.add(refund.entry().id()));
        boolean changed = false;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            UUID id = refundId(item, refundIdKey);
            if (id != null && !durableIds.contains(id)) {
                clearRefundId(item, refundIdKey);
                changed = true;
            }
        }
        if (changed) player.saveData();
    }

    private void acknowledgeAndUntag(Player player, RefundEntry entry, int delivered) {
        mailbox.acknowledge(entry.id(), delivered);
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (entry.id().equals(refundId(item))) {
                clearRefundId(item, refundIdKey);
            }
        }
        player.saveData();
    }

    private ItemStack tagged(ItemStack source, UUID id) {
        return tagged(source, id, refundIdKey);
    }

    static ItemStack tagged(ItemStack source, UUID id, NamespacedKey key) {
        ItemStack tagged = source.clone();
        ItemMeta meta = tagged.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id.toString());
        tagged.setItemMeta(meta);
        return tagged;
    }

    private int taggedAmount(Player player, UUID id) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (id.equals(refundId(item))) amount += item.getAmount();
        }
        return amount;
    }

    private UUID refundId(ItemStack item) {
        return refundId(item, refundIdKey);
    }

    static UUID refundId(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static void clearRefundId(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(key);
        item.setItemMeta(meta);
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Refund delivery must run on the primary thread");
    }
}
