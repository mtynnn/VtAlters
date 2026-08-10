package com.valerinsmp.valtars.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDataMigratorTest {
    @TempDir Path directory;

    @Test
    void migrationIsIdempotentBackedUpAndNeverOverwritesModernData() throws IOException {
        Path legacy = directory.resolve("VtAlters");
        Path modern = directory.resolve("vAltars");
        Files.createDirectories(legacy.resolve("language"));
        Files.createDirectories(modern);
        Files.writeString(legacy.resolve("altars.yml"), "legacy-altars");
        Files.writeString(legacy.resolve("language/messages_es.yml"), "legacy-language");
        Files.writeString(modern.resolve("altars.yml"), "modern-altars");

        LegacyDataMigrator migrator = new LegacyDataMigrator(legacy, modern);
        LegacyDataMigrator.Result first = migrator.migrate();
        LegacyDataMigrator.Result second = migrator.migrate();

        assertEquals(1, first.copiedFiles());
        assertEquals(1, first.preservedModernFiles());
        assertEquals(0, second.copiedFiles());
        assertEquals(2, second.preservedModernFiles());
        assertEquals("modern-altars", Files.readString(modern.resolve("altars.yml")));
        assertEquals("legacy-language", Files.readString(modern.resolve("language/messages_es.yml")));
        assertEquals("legacy-altars", Files.readString(modern.resolve("backups/legacy-v1/altars.yml")));
        assertEquals("legacy-language", Files.readString(modern.resolve("backups/legacy-v1/language/messages_es.yml")));
    }
}
