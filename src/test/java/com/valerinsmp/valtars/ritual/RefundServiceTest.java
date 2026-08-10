package com.valerinsmp.valtars.ritual;

import com.valerinsmp.valtars.BukkitTest;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefundServiceTest extends BukkitTest {
    @Test
    void inventoryLeftoversDefineExactlyWhatWasAccepted() {
        assertEquals(0, RefundService.acceptedAmount(5, Map.of(0, new ItemStack(Material.STONE, 5))));
        assertEquals(2, RefundService.acceptedAmount(5, Map.of(0, new ItemStack(Material.STONE, 3))));
        assertEquals(5, RefundService.acceptedAmount(5, Map.of()));
    }

    @Test
    void durableTagNeverTouchesTheSourceAndConfirmedItemStacksNormallyAgain() {
        NamespacedKey key = new NamespacedKey("valtars", "refund_id");
        UUID id = UUID.randomUUID();
        ItemStack common = new ItemStack(Material.STONE, 1);

        ItemStack tagged = RefundService.tagged(common, id, key);

        assertNull(RefundService.refundId(common, key));
        assertEquals(id, RefundService.refundId(tagged, key));
        assertFalse(common.isSimilar(tagged));

        RefundService.clearRefundId(tagged, key);
        assertNull(RefundService.refundId(tagged, key));
        assertTrue(common.isSimilar(tagged));
    }
}
