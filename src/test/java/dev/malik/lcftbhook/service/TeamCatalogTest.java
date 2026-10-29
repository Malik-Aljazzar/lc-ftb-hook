package dev.malik.lcftbhook.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamCatalogTest {
    private static final UUID PARTY = UUID.randomUUID();
    private static final UUID SOLO = UUID.randomUUID();
    private static final UUID INACTIVE = UUID.randomUUID();

    @Test
    void incident_4_4_1_partyListedPerMember_isSettledOnce() {
        // Production 2026-10-28: FTB listed the party "Schweden" once for itself and once for its owner's
        // personal team (getTeamId() = the party) - upkeep billed it twice every 15 minutes.
        List<UUID> settled = TeamCatalog.distinctActive(List.of(PARTY, SOLO, PARTY, PARTY), id -> true);
        assertEquals(List.of(PARTY, SOLO), settled);
    }

    @Test
    void inactiveAndNull_areDropped_orderKept() {
        List<UUID> teams = new ArrayList<>(Arrays.asList(SOLO, null, INACTIVE, PARTY));
        List<UUID> settled = TeamCatalog.distinctActive(teams, id -> !id.equals(INACTIVE));
        assertEquals(List.of(SOLO, PARTY), settled);
    }

    @Test
    void everyTeamAtMostOnce_whateverTheBackendReturns() {
        List<UUID> teams = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            teams.add(i % 3 == 0 ? PARTY : i % 3 == 1 ? SOLO : INACTIVE);
        }
        List<UUID> settled = TeamCatalog.distinctActive(teams, id -> true);
        assertEquals(Set.copyOf(settled).size(), settled.size());
    }
}
