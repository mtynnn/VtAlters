/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

public final class LegacyDataMigrator {
    private final Path legacyFolder;
    private final Path modernFolder;

    public LegacyDataMigrator(Path legacyFolder, Path modernFolder) {
        this.legacyFolder = legacyFolder.toAbsolutePath().normalize();
        this.modernFolder = modernFolder.toAbsolutePath().normalize();
    }

    public Result migrate() {
        if (legacyFolder.equals(modernFolder) || !Files.isDirectory(legacyFolder)) return new Result(0, 0);
        Path backup = modernFolder.resolve("backups/legacy-v1");
        int copied = 0;
        int preserved = 0;
        try {
            Files.createDirectories(modernFolder);
            List<Path> legacyFiles;
            try (Stream<Path> paths = Files.walk(legacyFolder)) {
                legacyFiles = paths.filter(Files::isRegularFile).toList();
            }
            for (Path source : legacyFiles) {
                Path relative = legacyFolder.relativize(source);
                Path backupTarget = backup.resolve(relative);
                if (!Files.exists(backupTarget)) {
                    Files.createDirectories(backupTarget.getParent());
                    Files.copy(source, backupTarget, StandardCopyOption.COPY_ATTRIBUTES);
                }

                Path target = modernFolder.resolve(relative);
                if (Files.exists(target)) {
                    preserved++;
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                copied++;
            }
            return new Result(copied, preserved);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not migrate legacy VtAlters data", exception);
        }
    }

    public record Result(int copiedFiles, int preservedModernFiles) { }
}
