package com.valerinsmp.valtars.service;

import com.valerinsmp.valtars.domain.AltarDefinition;
import com.valerinsmp.valtars.domain.BlockKey;
import com.valerinsmp.valtars.domain.ItemRequirement;
import com.valerinsmp.valtars.domain.ItemSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AltarManagerExpiryTest {
    @Test
    void placementExpiresAtIdleDeadlineOrAsSoonAsEveryContributorLeaves() {
        long now = 1_000L;
        long deadline = AltarManager.deadlineAfter(now, 45);

        assertEquals(now + TimeUnit.SECONDS.toNanos(45), deadline);
        assertFalse(AltarManager.shouldExpire(deadline - 1, deadline, true));
        assertTrue(AltarManager.shouldExpire(deadline, deadline, true));
        assertTrue(AltarManager.shouldExpire(now, deadline, false));
    }

    @Test
    void onePedestalAcceptsOnlyTheMissingAmountWithoutExceedingItsStackLimit() {
        assertEquals(16, AltarManager.amountToPlace(64, 16, 0, 64));
        assertEquals(8, AltarManager.amountToPlace(8, 16, 0, 64));
        assertEquals(8, AltarManager.amountToPlace(16, 8, 8, 64));
        assertEquals(4, AltarManager.amountToPlace(16, 16, 60, 64));
        assertEquals(0, AltarManager.amountToPlace(16, 16, 64, 64));
    }

    @Test
    void repeatedItemsRemainAssignedToTheirIndividualPedestals() {
        BlockKey first = new BlockKey("world", 1, 64, 0);
        BlockKey second = new BlockKey("world", 2, 64, 0);
        AltarDefinition altar = new AltarDefinition("basalt", "Boss",
                new BlockKey("world", 0, 64, 0), ItemSpec.nexo("molten_heart"),
                List.of(
                        new ItemRequirement(ItemSpec.nexo("crimson_fragment"), 16),
                        new ItemRequirement(ItemSpec.nexo("crimson_fragment"), 16)),
                List.of(first, second));

        assertEquals(16, AltarManager.requirementAt(altar, first).amount());
        assertEquals(16, AltarManager.requirementAt(altar, second).amount());
        assertNull(AltarManager.requirementAt(altar, new BlockKey("world", 3, 64, 0)));
        assertEquals(0, AltarManager.firstIncompleteIndex(altar, index -> 0));
        assertEquals(1, AltarManager.firstIncompleteIndex(altar, index -> index == 0 ? 16 : 0));
        assertEquals(1, AltarManager.firstIncompleteIndex(altar, index -> index == 0 ? 16 : 8));
        assertEquals(-1, AltarManager.firstIncompleteIndex(altar, index -> 16));
    }
}
