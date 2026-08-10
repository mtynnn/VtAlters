/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.integration;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.api.exceptions.InvalidMobTypeException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;

public final class MythicMobsIntegration {
    public LivingEntity spawn(String mobName, Location location) throws InvalidMobTypeException {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("MythicMobs access must run on the primary thread");
        LivingEntity living;
        if (mobName.equalsIgnoreCase("DefaultBoss")) {
            living = location.getWorld().spawn(location, Zombie.class);
        } else {
            Entity entity = MythicBukkit.inst().getAPIHelper().spawnMythicMob(mobName, location, 1);
            if (!(entity instanceof LivingEntity spawned)) {
                throw new IllegalStateException("MythicMobs did not return a LivingEntity for " + mobName);
            }
            living = spawned;
        }
        if (!living.isValid() || living.isDead()) {
            throw new IllegalStateException("Spawned boss is not a valid living entity: " + mobName);
        }
        return living;
    }
}
