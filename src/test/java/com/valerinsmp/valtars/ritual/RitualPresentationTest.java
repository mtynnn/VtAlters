package com.valerinsmp.valtars.ritual;

import com.valerinsmp.valtars.BukkitTest;
import org.bukkit.Sound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RitualPresentationTest extends BukkitTest {
    @Test
    void resolvesBukkitSoundConstantsWithoutCorruptingCompoundKeys() {
        assertEquals(Sound.BLOCK_END_PORTAL_FRAME_FILL,
                RitualPresentation.resolveSound("BLOCK_END_PORTAL_FRAME_FILL"));
    }
}
