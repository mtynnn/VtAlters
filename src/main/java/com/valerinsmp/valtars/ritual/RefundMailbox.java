/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.ritual;

import com.valerinsmp.valtars.domain.BlockKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RefundMailbox {
    public enum State { PENDING, CLAIMED }

    private final Path file;
    private final Map<UUID, PendingRefund> refunds = new LinkedHashMap<>();

    public RefundMailbox(Path file) {
        this.file = file;
        load();
    }

    public List<PendingRefund> forOwner(UUID owner) {
        return refunds.values().stream().filter(refund -> refund.entry().owner().equals(owner)).toList();
    }

    public int size() {
        return refunds.size();
    }

    public void enqueue(Collection<RefundEntry> entries) {
        if (entries.stream().allMatch(entry -> refunds.containsKey(entry.id()))) return;
        mutateAndSave(() -> entries.forEach(entry ->
                refunds.putIfAbsent(entry.id(), new PendingRefund(entry, State.PENDING))));
    }

    public void claim(UUID id) {
        PendingRefund current = require(id);
        mutateAndSave(() -> refunds.put(id, new PendingRefund(current.entry(), State.CLAIMED)));
    }

    public void pending(UUID id) {
        PendingRefund current = require(id);
        mutateAndSave(() -> refunds.put(id, new PendingRefund(current.entry(), State.PENDING)));
    }

    public void acknowledge(UUID id, int deliveredAmount) {
        if (deliveredAmount < 0) throw new IllegalArgumentException("Delivered amount cannot be negative");
        PendingRefund current = require(id);
        int remaining = current.entry().item().getAmount() - deliveredAmount;
        mutateAndSave(() -> {
            if (remaining <= 0) {
                refunds.remove(id);
            } else if (deliveredAmount == 0) {
                refunds.put(id, new PendingRefund(current.entry(), State.PENDING));
            } else {
                ItemStack item = current.entry().item();
                item.setAmount(remaining);
                // The accepted stack keeps the old durable claim ID. A fresh ID for
                // leftovers prevents an acknowledged partial delivery being counted twice after restart.
                RefundEntry remainder = RefundEntry.create(current.entry().owner(), current.entry().source(), item);
                refunds.remove(id);
                refunds.put(remainder.id(), new PendingRefund(remainder, State.PENDING));
            }
        });
    }

    private PendingRefund require(UUID id) {
        PendingRefund current = refunds.get(id);
        if (current == null) throw new IllegalArgumentException("Unknown refund " + id);
        return current;
    }

    private void mutateAndSave(Runnable mutation) {
        Map<UUID, PendingRefund> before = new LinkedHashMap<>(refunds);
        try {
            mutation.run();
            save();
        } catch (RuntimeException exception) {
            refunds.clear();
            refunds.putAll(before);
            throw exception;
        }
    }

    private void load() {
        refunds.clear();
        if (!Files.isRegularFile(file)) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("refunds");
        if (root == null) return;
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            try {
                UUID id = UUID.fromString(rawId);
                UUID owner = UUID.fromString(section.getString("owner", ""));
                BlockKey source = BlockKey.parse(section.getString("source"));
                ConfigurationSection itemSection = section.getConfigurationSection("item");
                Object rawItem = section.get("item");
                Map<String, Object> serialized = itemSection != null
                        ? itemSection.getValues(true)
                        : rawItem instanceof Map<?, ?> map ? stringMap(map) : null;
                if (serialized == null) throw new IllegalArgumentException("Missing serialized item");
                ItemStack item = ItemStack.deserialize(serialized);
                State state = State.valueOf(section.getString("state", "PENDING"));
                refunds.put(id, new PendingRefund(new RefundEntry(id, owner, source, item), state));
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Invalid pending refund " + rawId, exception);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PendingRefund refund : refunds.values()) {
            String path = "refunds." + refund.entry().id();
            yaml.set(path + ".owner", refund.entry().owner().toString());
            yaml.set(path + ".source", refund.entry().source().toString());
            yaml.set(path + ".state", refund.state().name());
            yaml.set(path + ".item", refund.entry().item().serialize());
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist pending refunds", exception);
        }
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    public record PendingRefund(RefundEntry entry, State state) { }
}
