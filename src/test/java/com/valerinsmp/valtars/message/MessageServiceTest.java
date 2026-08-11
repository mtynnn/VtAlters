package com.valerinsmp.valtars.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageServiceTest {
    @Test
    void migratesOnlyTheBundledLegacyPrefix() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("prefix", "<dark_gray>[</dark_gray><primary>vAltars</primary><dark_gray>]</dark_gray> <reset>");

        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("prefix", "<primary><bold>vAltars</bold></primary> <dark_gray>»</dark_gray> ");
        assertTrue(MessageService.migrateLegacyPrefix(legacy, defaults));
        assertEquals(defaults.getString("prefix"), legacy.getString("prefix"));

        YamlConfiguration custom = new YamlConfiguration();
        custom.set("prefix", "<aqua>Mi altar</aqua> ");
        assertFalse(MessageService.migrateLegacyPrefix(custom, defaults));
        assertEquals("<aqua>Mi altar</aqua> ", custom.getString("prefix"));
    }
}
