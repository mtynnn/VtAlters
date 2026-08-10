/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.domain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;
import java.util.Objects;

public record BlockKey(String world, int x, int y, int z) {
    public BlockKey {
        Objects.requireNonNull(world, "world");
        if (world.isBlank()) throw new IllegalArgumentException("World name cannot be blank");
    }

    public static BlockKey from(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new BlockKey(world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockKey parse(String serialized) {
        if (serialized == null || serialized.isBlank() || serialized.equalsIgnoreCase("not_set")) return null;
        String[] parts = serialized.split(",", -1);
        if (parts.length != 4 || parts[0].isBlank()) {
            throw new IllegalArgumentException("Expected world,x,y,z but got: " + serialized);
        }
        try {
            return new BlockKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid block coordinates: " + serialized, exception);
        }
    }

    public Location location() {
        World loadedWorld = Bukkit.getWorld(world);
        return loadedWorld == null ? null : new Location(loadedWorld, x, y, z);
    }

    public double distanceSquared(BlockKey other) {
        if (other == null || !world.equalsIgnoreCase(other.world)) return Double.POSITIVE_INFINITY;
        long dx = (long) x - other.x;
        long dy = (long) y - other.y;
        long dz = (long) z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public String normalizedWorld() {
        return world.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return world + "," + x + "," + y + "," + z;
    }
}
