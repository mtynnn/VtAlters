package com.valerinsmp.valtars.ritual;

import com.valerinsmp.valtars.BukkitTest;
import com.valerinsmp.valtars.domain.BlockKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefundMailboxTest extends BukkitTest {
    @TempDir Path directory;

    @Test
    void offlineRefundSurvivesRestartWithoutBeingDeleted() {
        Path file = directory.resolve("pending-refunds.yml");
        UUID owner = UUID.randomUUID();
        RefundEntry entry = entry(owner, 4);

        RefundMailbox mailbox = new RefundMailbox(file);
        mailbox.enqueue(List.of(entry));

        RefundMailbox reloaded = new RefundMailbox(file);
        assertEquals(1, reloaded.size());
        assertEquals(4, reloaded.forOwner(owner).getFirst().entry().item().getAmount());
        assertEquals(RefundMailbox.State.PENDING, reloaded.forOwner(owner).getFirst().state());
    }

    @Test
    void fullInventoryLeavesTheCompleteRefundPending() {
        UUID owner = UUID.randomUUID();
        RefundEntry entry = entry(owner, 5);
        RefundMailbox mailbox = new RefundMailbox(directory.resolve("full.yml"));
        mailbox.enqueue(List.of(entry));
        mailbox.claim(entry.id());

        mailbox.acknowledge(entry.id(), 0);

        RefundMailbox.PendingRefund pending = mailbox.forOwner(owner).getFirst();
        assertEquals(entry.id(), pending.entry().id());
        assertEquals(5, pending.entry().item().getAmount());
        assertEquals(RefundMailbox.State.PENDING, pending.state());
    }

    @Test
    void partialDeliveryRotatesClaimIdAndPersistsOnlyExactLeftovers() {
        Path file = directory.resolve("partial.yml");
        UUID owner = UUID.randomUUID();
        RefundEntry original = entry(owner, 5);
        RefundMailbox mailbox = new RefundMailbox(file);
        mailbox.enqueue(List.of(original));
        mailbox.claim(original.id());

        mailbox.acknowledge(original.id(), 2);

        RefundMailbox.PendingRefund remainder = mailbox.forOwner(owner).getFirst();
        assertNotEquals(original.id(), remainder.entry().id());
        assertEquals(3, remainder.entry().item().getAmount());
        assertEquals(RefundMailbox.State.PENDING, remainder.state());

        RefundMailbox restarted = new RefundMailbox(file);
        assertEquals(remainder.entry().id(), restarted.forOwner(owner).getFirst().entry().id());
        assertEquals(3, restarted.forOwner(owner).getFirst().entry().item().getAmount());

        assertThrows(IllegalArgumentException.class, () -> restarted.acknowledge(original.id(), 2));
        restarted.acknowledge(remainder.entry().id(), 3);
        assertEquals(0, restarted.size(), "a fully acknowledged ID cannot be delivered again");
        assertEquals(0, new RefundMailbox(file).size());
    }

    @Test
    void claimedDeliveryCanBeRecoveredAfterProcessInterruption() {
        Path file = directory.resolve("claimed.yml");
        UUID owner = UUID.randomUUID();
        RefundEntry entry = entry(owner, 1);
        RefundMailbox mailbox = new RefundMailbox(file);
        mailbox.enqueue(List.of(entry));
        mailbox.claim(entry.id());

        RefundMailbox restarted = new RefundMailbox(file);
        assertEquals(RefundMailbox.State.CLAIMED, restarted.forOwner(owner).getFirst().state());
        restarted.pending(entry.id());
        assertEquals(RefundMailbox.State.PENDING, new RefundMailbox(file).forOwner(owner).getFirst().state());
    }

    @Test
    void failedPersistenceDoesNotLeaveAnUnpersistedEntryInMemory() throws Exception {
        Path blockingFile = directory.resolve("not-a-directory");
        java.nio.file.Files.writeString(blockingFile, "blocker");
        RefundMailbox mailbox = new RefundMailbox(blockingFile.resolve("refunds.yml"));

        assertThrows(IllegalStateException.class, () -> mailbox.enqueue(List.of(entry(UUID.randomUUID(), 1))));
        assertEquals(0, mailbox.size());
    }

    private static RefundEntry entry(UUID owner, int amount) {
        return RefundEntry.create(owner, new BlockKey("world", 1, 65, 1), new ItemStack(Material.STONE, amount));
    }
}
