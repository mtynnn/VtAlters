/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.ritual;

public record RitualSettings(
        boolean broadcastSummon,
        double pedestalHeight,
        double readyParticleHeight,
        double ritualRingOffset,
        String readyParticle,
        String ringParticle,
        String pedestalParticle,
        String trailParticle,
        String secondaryTrailParticle,
        String burstParticle,
        String startSound,
        String ambientSound,
        String itemsFlySound,
        String convergeSound,
        String spawnSound
) { }
