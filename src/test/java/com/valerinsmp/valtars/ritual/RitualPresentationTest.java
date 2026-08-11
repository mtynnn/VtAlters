package com.valerinsmp.valtars.ritual;

import com.valerinsmp.valtars.BukkitTest;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RitualPresentationTest extends BukkitTest {
    @Test
    void resolvesBukkitSoundConstantsWithoutCorruptingCompoundKeys() {
        assertEquals(Sound.BLOCK_END_PORTAL_FRAME_FILL,
                RitualPresentation.resolveSound("BLOCK_END_PORTAL_FRAME_FILL"));
    }

    @Test
    void ritualConvergesAndSpawnsOnTopOfTheSelectedCenterBlock() {
        Location selectedBlock = new Location(null, 10, 64, -5);
        Location negativeBlock = new Location(null, -11, 20, -7);

        Location point = RitualPresentation.ritualPoint(selectedBlock);
        Location negativePoint = RitualPresentation.ritualPoint(negativeBlock);

        assertEquals(10.5, point.getX());
        assertEquals(65.0, point.getY());
        assertEquals(-4.5, point.getZ());
        assertEquals(-10.5, negativePoint.getX());
        assertEquals(21.0, negativePoint.getY());
        assertEquals(-6.5, negativePoint.getZ());
        assertEquals(64.0, selectedBlock.getY(), "the selected block location remains unchanged");
        assertEquals(20.0, negativeBlock.getY(), "negative coordinates remain unchanged too");
    }
}
