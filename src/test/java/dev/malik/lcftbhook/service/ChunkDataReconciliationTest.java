package dev.malik.lcftbhook.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChunkDataReconciliationTest {
    private static final UUID TEAM = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static Set<String> chunks(int from, int to) {
        Set<String> keys = new HashSet<>();
        for (int i = from; i < to; i++) {
            keys.add("minecraft:overworld:" + i + ":0");
        }
        return keys;
    }

    @Test
    void ownedChunks_areKept() {
        var plan = ChunkDataReconciliation.plan(Map.of(TEAM, chunks(0, 10)), Map.of(TEAM, chunks(0, 10)), 100);
        assertTrue(plan.nothingToDo());
        assertFalse(plan.refused());
    }

    @Test
    void chunkThatExpiredInTheClaimMod_isDropped() {
        // OPAC expiry / admin clear removed one claim without telling this mod.
        var plan = ChunkDataReconciliation.plan(Map.of(TEAM, chunks(0, 10)), Map.of(TEAM, chunks(1, 10)), 100);
        assertEquals(Map.of(TEAM, Set.of("minecraft:overworld:0:0")), plan.stale());
    }

    @Test
    void chunkTransferredToAnotherTeam_isDroppedFromTheOldOne() {
        Map<UUID, Set<String>> tracked = Map.of(TEAM, chunks(0, 10), OTHER, chunks(20, 30));
        Map<UUID, Set<String>> owned = new HashMap<>();
        owned.put(TEAM, chunks(1, 10));
        Set<String> otherOwned = chunks(20, 30);
        otherOwned.add("minecraft:overworld:0:0");
        owned.put(OTHER, otherOwned);
        var plan = ChunkDataReconciliation.plan(tracked, owned, 100);
        assertEquals(Set.of(TEAM), plan.stale().keySet());
    }

    @Test
    void teamUnknownToTheClaimMod_isSkipped() {
        var plan = ChunkDataReconciliation.plan(Map.of(TEAM, chunks(0, 10)), Map.of(), 100);
        assertTrue(plan.nothingToDo());
        assertEquals(0, plan.trackedCount());
    }

    @Test
    void claimModReportingNoClaimsAtAll_isRefused() {
        // Every team suddenly without claims: the claim mod most likely didn't load - never wipe on that.
        var plan = ChunkDataReconciliation.plan(Map.of(TEAM, chunks(0, 50), OTHER, chunks(50, 100)),
                Map.of(TEAM, Set.of(), OTHER, Set.of()), 0);
        assertEquals(Set.of(TEAM, OTHER), plan.refusedTeams());
        assertTrue(plan.nothingToDo());
        assertEquals(100, plan.staleCount());
    }

    @Test
    void onlyTrackedTeamLostAllItsClaims_whileOthersHaveClaims_isCleaned() {
        // migration fixture broken_4.0.0: the only per-chunk data in the world belongs to a party whose
        // 9 chunks nobody owns any more, while the claim mod knows 586 claims of other teams.
        var plan = ChunkDataReconciliation.plan(Map.of(TEAM, chunks(0, 9)), Map.of(TEAM, Set.of()), 586);
        assertFalse(plan.refused());
        assertEquals(9, plan.stale().get(TEAM).size());
    }

    @Test
    void singleTeamLosingAllItsClaims_isNormal() {
        // One inactive player's claims expired in OPAC.
        Map<UUID, Set<String>> tracked = Map.of(TEAM, chunks(0, 2), OTHER, chunks(10, 60));
        var plan = ChunkDataReconciliation.plan(tracked, Map.of(TEAM, Set.of(), OTHER, chunks(10, 60)), 50);
        assertFalse(plan.refused());
        assertEquals(Set.of(TEAM), plan.stale().keySet());
    }
}
