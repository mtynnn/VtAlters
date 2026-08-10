/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.config;

import com.valerinsmp.valtars.domain.AltarDefinition;
import com.valerinsmp.valtars.domain.BlockKey;
import com.valerinsmp.valtars.domain.ItemRequirement;
import com.valerinsmp.valtars.domain.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AltarRepository {
    private final Path file;

    public AltarRepository(Path file) {
        this.file = file;
    }

    public Map<String, AltarDefinition> load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("altars");
        Map<String, AltarDefinition> definitions = new LinkedHashMap<>();
        if (root == null) return definitions;

        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) continue;
            try {
                AltarDefinition altar = read(name, section);
                definitions.put(key(name), altar);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Invalid altar '" + name + "': " + exception.getMessage(), exception);
            }
        }
        validateUniqueBlocks(definitions.values());
        return definitions;
    }

    public void save(AltarDefinition altar) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        write(yaml, "altars." + altar.name(), altar);
        saveAtomically(yaml);
    }

    public void delete(String altarName) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        yaml.set("altars." + altarName, null);
        saveAtomically(yaml);
    }

    public void saveAll(Collection<AltarDefinition> altars) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (AltarDefinition altar : altars) write(yaml, "altars." + altar.name(), altar);
        saveAtomically(yaml);
    }

    private AltarDefinition read(String name, ConfigurationSection section) {
        BlockKey center = BlockKey.parse(section.getString("center"));
        ItemSpec activation = readItem(section, "central-item", "central-item-nexo-id");
        List<ItemRequirement> requirements = new ArrayList<>();

        for (Map<?, ?> entry : section.getMapList("required-items")) {
            Object rawItem = entry.get("item");
            Object rawNexoId = entry.get("nexo-id");
            Object rawAmount = entry.get("amount");
            if (!(rawAmount instanceof Number amount)) {
                throw new IllegalArgumentException("Malformed required-items entry");
            }
            if (rawNexoId instanceof String id && !id.isBlank()) {
                requirements.add(new ItemRequirement(ItemSpec.nexo(id), amount.intValue()));
            } else if (rawItem instanceof Map<?, ?> itemMap) {
                requirements.add(new ItemRequirement(ItemSpec.vanilla(ItemStack.deserialize(stringMap(itemMap))), amount.intValue()));
            } else {
                throw new IllegalArgumentException("Malformed required-items entry");
            }
        }
        ConfigurationSection nexo = section.getConfigurationSection("required-items-nexo");
        if (nexo != null) {
            for (String id : nexo.getKeys(false)) {
                requirements.add(new ItemRequirement(ItemSpec.nexo(id), nexo.getInt(id)));
            }
        }

        List<BlockKey> pedestals = section.getStringList("pedestal-locations").stream().map(BlockKey::parse).toList();
        AltarDefinition result = new AltarDefinition(name, section.getString("boss-name", "DefaultBoss"),
                center, activation, section.getInt("central-item-amount", 1), requirements, pedestals);
        if (result.requiredPedestalCount() > result.pedestals().size()) {
            throw new IllegalArgumentException("Required item slots exceed pedestal count");
        }
        return result;
    }

    private ItemSpec readItem(ConfigurationSection section, String vanillaPath, String nexoPath) {
        String nexoId = section.getString(nexoPath);
        if (nexoId != null && !nexoId.isBlank()) return ItemSpec.nexo(nexoId);
        ConfigurationSection vanilla = section.getConfigurationSection(vanillaPath);
        return vanilla == null ? null : ItemSpec.vanilla(ItemStack.deserialize(vanilla.getValues(true)));
    }

    private void write(YamlConfiguration yaml, String path, AltarDefinition altar) {
        yaml.set(path, null);
        yaml.set(path + ".boss-name", altar.bossName());
        yaml.set(path + ".center", altar.center() == null ? "not_set" : altar.center().toString());
        if (altar.activationItem() == null) {
            yaml.set(path + ".central-item", null);
            yaml.set(path + ".central-item-nexo-id", null);
            yaml.set(path + ".central-item-amount", null);
        } else if (altar.activationItem().isNexo()) {
            yaml.set(path + ".central-item", null);
            yaml.set(path + ".central-item-nexo-id", altar.activationItem().nexoId());
            yaml.set(path + ".central-item-amount", altar.activationAmount());
        } else {
            yaml.set(path + ".central-item", altar.activationItem().vanilla().serialize());
            yaml.set(path + ".central-item-nexo-id", null);
            yaml.set(path + ".central-item-amount", altar.activationAmount());
        }

        List<Map<String, Object>> requirements = new ArrayList<>();
        for (ItemRequirement requirement : altar.requirements()) {
            Map<String, Object> item = new LinkedHashMap<>();
            if (requirement.item().isNexo()) {
                item.put("nexo-id", requirement.item().nexoId());
            } else {
                item.put("item", requirement.item().vanilla().serialize());
            }
            item.put("amount", requirement.amount());
            requirements.add(item);
        }
        yaml.set(path + ".required-items", requirements);
        yaml.set(path + ".required-items-nexo", null);
        yaml.set(path + ".pedestal-locations", altar.pedestals().stream().map(BlockKey::toString).toList());
    }

    private void saveAtomically(YamlConfiguration yaml) {
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
            throw new IllegalStateException("Could not save " + file, exception);
        }
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> result = new HashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static void validateUniqueBlocks(Collection<AltarDefinition> altars) {
        Map<BlockKey, String> owners = new HashMap<>();
        for (AltarDefinition altar : altars) {
            if (altar.center() != null) claim(owners, altar.center(), altar.name());
            for (BlockKey pedestal : altar.pedestals()) claim(owners, pedestal, altar.name());
        }
    }

    private static void claim(Map<BlockKey, String> owners, BlockKey block, String altar) {
        String previous = owners.putIfAbsent(block, altar);
        if (previous != null) throw new IllegalArgumentException("Block " + block + " belongs to both '" + previous + "' and '" + altar + "'");
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
