package com.valerinsmp.valtars.command;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CommandContractTest {
    @Test
    void canonicalAndLegacyPermissionsAreAccepted() {
        Set<String> legacyOnly = Set.of("vtalters.command.edit");
        assertTrue(PermissionPolicy.has(legacyOnly::contains, "valtars.command.edit"));
        assertTrue(PermissionPolicy.has(Set.of("valtars.command.edit")::contains, "valtars.command.edit"));
        assertFalse(PermissionPolicy.has(Set.<String>of()::contains, "valtars.command.edit"));
    }

    @Test
    void helpPaginationIsStableAtBounds() {
        HelpPaginator.Page<Integer> second = HelpPaginator.page(List.of(1, 2, 3, 4, 5), 2, 3);
        assertTrue(second.valid());
        assertEquals(List.of(4, 5), second.entries());
        assertEquals(2, second.totalPages());
        assertFalse(HelpPaginator.page(List.of(1), 2, 8).valid());
    }

    @Test
    void pluginMetadataExposesPublicAndAdminCommandContracts() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(stream);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals("vAltars", yaml.getString("name"));
            assertEquals("com.valerinsmp.valtars.VAltarsPlugin", yaml.getString("main"));
            assertEquals("https://github.com/ValerinSMP/vAltars", yaml.getString("website"));
            assertNotNull(yaml.getConfigurationSection("commands.valtars"));
            assertEquals(List.of("altar", "vta", "vtalters"), yaml.getStringList("commands.valtarsadmin.aliases"));
            assertTrue(yaml.isSet("permissions.valtars.admin"));
            assertTrue(yaml.isSet("permissions.valtars.command.teleport"));
            assertTrue(yaml.isSet("permissions.vtalters.admin"));
            assertTrue(yaml.getString("commands.valtarsadmin.usage").contains("gui"));
        }
    }

    @Test
    void everyBundledLanguageExposesTheAltarBrowserContract() throws Exception {
        for (String language : List.of("es", "en", "vi")) {
            try (var stream = getClass().getClassLoader().getResourceAsStream(
                    "language/messages_" + language + ".yml")) {
                assertNotNull(stream);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                assertTrue(yaml.isList("gui.altar-lore"));
                assertFalse(yaml.getStringList("gui.altar-lore").isEmpty());
                assertNotNull(yaml.getString("gui.teleported"));
                assertNotNull(yaml.getString("gui.teleport-failed"));
                assertNotNull(yaml.getString("help.descriptions.gui"));
                assertEquals("https://github.com/ValerinSMP/vAltars",
                        yaml.getString("about.repository-url"));
            }
        }
    }
}
