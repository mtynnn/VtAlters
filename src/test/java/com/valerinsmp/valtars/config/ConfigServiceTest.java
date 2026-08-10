package com.valerinsmp.valtars.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceTest {
    @TempDir Path directory;

    @Test
    void invalidReloadLeavesPreviousSnapshotActiveAndValidReloadRequiresApply() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "language: es\naltar:\n  max-pedestal-radius: 10\n");
        ConfigService service = new ConfigService(file);
        assertEquals(45, service.snapshot().placementIdleSeconds());
        assertEquals(16.0, service.snapshot().placementMaxPlayerDistance());

        Files.writeString(file, "language: es\naltar:\n  max-pedestal-radius: 10\n"
                + "  placement-expiry:\n    idle-seconds: 0\n    max-player-distance: 16\n");
        assertThrows(IllegalArgumentException.class, service::validateReload);
        assertEquals(10.0, service.snapshot().maxPedestalRadius());

        Files.writeString(file, "language: en\naltar:\n  max-pedestal-radius: 6\n"
                + "  placement-expiry:\n    idle-seconds: 60\n    max-player-distance: 24\n");
        ConfigService.Snapshot candidate = service.validateReload();
        assertEquals("es", service.snapshot().language());
        service.apply(candidate);
        assertEquals("en", service.snapshot().language());
        assertEquals(6.0, service.snapshot().maxPedestalRadius());
        assertEquals(60, service.snapshot().placementIdleSeconds());
        assertEquals(24.0, service.snapshot().placementMaxPlayerDistance());
    }
}
