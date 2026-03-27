/*
 * VtAlters - Plugin for summoning bosses via ritual altars.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */

package com.vtalters;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model for a ritual altar.
 *
 * An altar consists of:
 *  - A center block where the player activates the summoning ritual.
 *  - Pedestal blocks where required items must be placed.
 *  - A central (activation) item that must be held to trigger the ritual.
 *  - A set of required items that must be placed on pedestals beforehand.
 *  - The name of the MythicMobs boss to summon.
 *
 * Nexo support:
 *  - centralItemNexoId   : Nexo item ID for the activation item (null = plain Bukkit item).
 *  - requiredItemNexoIds : Map of Nexo item ID → required quantity for pedestal items.
 *  When a Nexo field is set it takes priority over the equivalent ItemStack.
 */
public class Altar {

    private final String name;
    private String bossName;
    private ItemStack centralItem;
    private String centralItemNexoId;
    private Map<ItemStack, Integer> requiredItems;
    private Map<String, Integer> requiredItemNexoIds;
    private Location centerLocation;
    private List<Location> pedestalLocations;

    public Altar(String name) {
        this.name = name;
        this.requiredItems      = new HashMap<>();
        this.requiredItemNexoIds = new HashMap<>();
        this.pedestalLocations  = new ArrayList<>();
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String getName()                              { return name; }
    public String getBossName()                          { return bossName; }
    public ItemStack getCentralItem()                    { return centralItem; }
    public String getCentralItemNexoId()                 { return centralItemNexoId; }
    public Map<ItemStack, Integer> getRequiredItems()    { return requiredItems; }
    public Map<String, Integer> getRequiredItemNexoIds() { return requiredItemNexoIds; }
    public Location getCenterLocation()                  { return centerLocation; }
    public List<Location> getPedestalLocations()         { return pedestalLocations; }

    // ── Setters ───────────────────────────────────────────────────────────

    public void setBossName(String bossName)                              { this.bossName = bossName; }
    public void setCentralItem(ItemStack centralItem)                     { this.centralItem = centralItem; }
    public void setCentralItemNexoId(String id)                           { this.centralItemNexoId = id; }
    public void setRequiredItems(Map<ItemStack, Integer> requiredItems)   { this.requiredItems = requiredItems; }
    public void setRequiredItemNexoIds(Map<String, Integer> ids)          { this.requiredItemNexoIds = ids; }
    public void setCenterLocation(Location centerLocation)                { this.centerLocation = centerLocation; }
    public void setPedestalLocations(List<Location> pedestalLocations)    { this.pedestalLocations = pedestalLocations; }

    // ── Location serialization ─────────────────────────────────────────────

    /** Serializes a Location to "world,x,y,z" for YAML storage. */
    public static String locationToString(Location loc) {
        if (loc == null) return null;
        return String.format("%s,%d,%d,%d",
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Deserializes a Location from "world,x,y,z".
     * Returns null if the string is invalid or the world does not exist.
     */
    public static Location locationFromString(String locString) {
        if (locString == null || locString.isEmpty() || locString.equalsIgnoreCase("not_set")) return null;
        String[] parts = locString.split(",");
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
