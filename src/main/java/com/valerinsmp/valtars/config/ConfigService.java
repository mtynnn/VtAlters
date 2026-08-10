/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.config;

import com.valerinsmp.valtars.ritual.RitualSettings;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ConfigService {
    private static final Pattern LANGUAGE = Pattern.compile("[a-zA-Z0-9_-]{2,16}");
    private final Path file;
    private Snapshot snapshot;

    public ConfigService(Path file) {
        this.file = file;
        this.snapshot = load(file);
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public Snapshot validateReload() {
        return load(file);
    }

    public void apply(Snapshot candidate) {
        snapshot = candidate;
    }

    public static Snapshot load(Path file) {
        File source = file.toFile();
        if (!source.isFile()) throw new IllegalArgumentException("Missing config.yml: " + file);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);

        String language = yaml.getString("language", "es").trim();
        if (!LANGUAGE.matcher(language).matches()) {
            throw new IllegalArgumentException("Invalid language id: " + language);
        }
        double radius = yaml.getDouble("altar.max-pedestal-radius", 10.0);
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("altar.max-pedestal-radius must be positive");
        }
        int placementIdleSeconds = yaml.getInt("altar.placement-expiry.idle-seconds", 45);
        if (placementIdleSeconds <= 0) {
            throw new IllegalArgumentException("altar.placement-expiry.idle-seconds must be positive");
        }
        double placementMaxPlayerDistance = yaml.getDouble("altar.placement-expiry.max-player-distance", 16.0);
        if (!Double.isFinite(placementMaxPlayerDistance) || placementMaxPlayerDistance <= 0.0) {
            throw new IllegalArgumentException("altar.placement-expiry.max-player-distance must be positive");
        }

        RitualSettings ritual = new RitualSettings(
                yaml.getBoolean("altar.broadcast-summon.enabled", true),
                finite(yaml, "effects.heights.pedestal", 1.2),
                finite(yaml, "effects.heights.ready-particle", 1.2),
                finite(yaml, "effects.heights.ritual-ring-offset", 0.0),
                token(yaml, "effects.particles.altar-ready", "SOUL_FIRE_FLAME"),
                token(yaml, "effects.particles.ritual-ring", "SOUL_FIRE_FLAME"),
                token(yaml, "effects.particles.pedestal-ready", "END_ROD"),
                token(yaml, "effects.particles.animation-trail", "ENCHANT"),
                token(yaml, "effects.particles.animation-trail-secondary", "END_ROD"),
                token(yaml, "effects.particles.convergence-burst", "END_ROD"),
                sound(yaml, "effects.sounds.ritual-start", "BLOCK_BEACON_ACTIVATE,1.5,0.8"),
                sound(yaml, "effects.sounds.ritual-ambient-loop", "BLOCK_CONDUIT_AMBIENT_SHORT,1.0,1.2"),
                sound(yaml, "effects.sounds.ritual-items-fly", "ENTITY_PHANTOM_SWOOP,0.7,1.5"),
                sound(yaml, "effects.sounds.ritual-converge", "ENTITY_GENERIC_EXPLODE,2.0,1.2"),
                sound(yaml, "effects.sounds.summon-spawn", "ENTITY_WITHER_SPAWN,2.0,1.0")
        );
        return new Snapshot(language, yaml.getBoolean("altar.prevent-item-theft", true), radius,
                placementIdleSeconds, placementMaxPlayerDistance, ritual);
    }

    private static double finite(YamlConfiguration yaml, String path, double fallback) {
        double value = yaml.getDouble(path, fallback);
        if (!Double.isFinite(value)) throw new IllegalArgumentException(path + " must be finite");
        return value;
    }

    private static String token(YamlConfiguration yaml, String path, String fallback) {
        String value = yaml.getString(path, fallback);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + " cannot be blank");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String sound(YamlConfiguration yaml, String path, String fallback) {
        String value = token(yaml, path, fallback);
        String[] parts = value.split(",", -1);
        if (parts.length > 3 || parts[0].isBlank()) throw new IllegalArgumentException("Invalid sound at " + path);
        try {
            if (parts.length > 1 && Float.parseFloat(parts[1]) < 0) throw new NumberFormatException();
            if (parts.length > 2 && Float.parseFloat(parts[2]) < 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid sound volume/pitch at " + path, exception);
        }
        return value;
    }

    public record Snapshot(String language, boolean preventItemTheft, double maxPedestalRadius,
                           int placementIdleSeconds, double placementMaxPlayerDistance,
                           RitualSettings ritual) { }
}
