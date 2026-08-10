package com.valerinsmp.valtars.ritual;

import com.valerinsmp.valtars.BukkitTest;
import com.valerinsmp.valtars.domain.BlockKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RitualSessionTest extends BukkitTest {
    @Test
    void terminalTransitionsAreIdempotentAndExclusive() {
        RitualSession committed = session("sun", settings("FLAME"));
        assertTrue(committed.commit());
        assertFalse(committed.commit());
        assertTrue(committed.rollback().isEmpty());
        assertEquals(RitualSession.State.COMMITTED, committed.state());

        RitualSession rolledBack = session("moon", settings("SOUL"));
        assertEquals(1, rolledBack.rollback().size());
        assertTrue(rolledBack.rollback().isEmpty());
        assertFalse(rolledBack.commit());
        assertEquals(RitualSession.State.ROLLED_BACK, rolledBack.state());
    }

    @Test
    void oneSessionPerAltarDoesNotCoupleDifferentAltars() {
        RitualSessions sessions = new RitualSessions();
        RitualSession first = session("Sun", settings("FLAME"));
        RitualSession duplicate = session("sun", settings("FLAME"));
        RitualSession other = session("Moon", settings("SOUL"));

        assertTrue(sessions.begin(first));
        assertFalse(sessions.begin(duplicate));
        assertTrue(sessions.begin(other));

        first.rollback();
        sessions.remove("SUN");
        assertFalse(sessions.contains("sun"));
        assertSame(other, sessions.get("moon"));
        assertEquals(RitualSession.State.ACTIVE, other.state());
    }

    @Test
    void failedSpawnAndDisableReturnEachSnapshotOnlyOnce() {
        RitualSessions sessions = new RitualSessions();
        RitualSession failedSpawn = session("failed", settings("FLAME"));
        RitualSession disabled = session("disabled", settings("SOUL"));
        sessions.begin(failedSpawn);
        sessions.begin(disabled);

        assertEquals(1, failedSpawn.rollback().size(), "failed spawn refunds its captured item");
        assertTrue(failedSpawn.rollback().isEmpty(), "failure callback cannot refund twice");
        sessions.remove(failedSpawn.altarName());

        List<RefundEntry> shutdownRefunds = new ArrayList<>();
        for (RitualSession active : sessions.snapshot()) {
            shutdownRefunds.addAll(active.rollback());
            sessions.remove(active.altarName());
        }
        assertEquals(1, shutdownRefunds.size());
        assertTrue(sessions.snapshot().isEmpty());
    }

    @Test
    void sessionKeepsImmutableItemsAndConfigurationSnapshotAcrossReload() {
        ItemStack source = new ItemStack(Material.DIAMOND, 4);
        UUID owner = UUID.randomUUID();
        RitualSettings original = settings("FLAME");
        RitualSession session = new RitualSession("sun", owner, "Martin", "Boss", original,
                List.of(RefundEntry.create(owner, new BlockKey("world", 0, 64, 0), source)),
                List.of(source), Map.of(owner, 2));

        source.setAmount(32);
        ItemStack exposed = session.visualItems().getFirst();
        exposed.setAmount(12);
        RitualSettings reloaded = settings("SOUL");

        assertEquals(4, session.refunds().getFirst().item().getAmount());
        assertEquals(4, session.visualItems().getFirst().getAmount());
        assertSame(original, session.settings());
        assertNotEquals(reloaded.readyParticle(), session.settings().readyParticle());
    }

    private static RitualSession session(String altar, RitualSettings settings) {
        UUID owner = UUID.randomUUID();
        ItemStack item = new ItemStack(Material.STONE, 1);
        return new RitualSession(altar, owner, "Player", "Boss", settings,
                List.of(RefundEntry.create(owner, new BlockKey("world", 0, 64, 0), item)),
                List.of(item), Map.of(owner, 1));
    }

    private static RitualSettings settings(String readyParticle) {
        return new RitualSettings(true, 1.2, 1.2, 0.0,
                readyParticle, "RING", "PEDESTAL", "TRAIL", "TRAIL_2", "BURST",
                "START,1,1", "AMBIENT,1,1", "FLY,1,1", "CONVERGE,1,1", "SPAWN,1,1");
    }
}
