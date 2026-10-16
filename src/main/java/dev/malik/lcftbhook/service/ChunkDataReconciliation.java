package dev.malik.lcftbhook.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Invariant: this mod keeps per-chunk data for a team (region assignment,
 * marketplace ownership and listing, settlement markers, queued changes) only
 * for chunks that team actually owns according to the claim mod.
 * <p>
 * Normally unclaiming clears that data, but claims can also disappear or change
 * hands without this mod being told - e.g. Open Parties and Claims' expiry of
 * inactive players' claims and its {@code admin clear}/{@code admin transfer}
 * commands bypass the claim listener. Left behind, such data showed chunks as
 * for sale or force-loaded that nobody owns, and queued force-loads were
 * retried forever. This decides what to drop. If the claim mod reports no claims
 * at all for any team, it most likely hasn't loaded its data - then teams without
 * claims are left untouched (teams the claim mod doesn't know are always skipped).
 */
public final class ChunkDataReconciliation {
    /**
     * @param stale        per team, the chunks whose data is to be dropped
     * @param staleCount   chunks found stale in total (including refused ones)
     * @param trackedCount chunks with data in total
     * @param refusedTeams teams left untouched because the claim mod reported no claims at all - for them
     *                     and for every other team - so it probably hasn't loaded its data
     */
    public record Plan(Map<UUID, Set<String>> stale, int staleCount, int trackedCount, Set<UUID> refusedTeams) {
        public boolean nothingToDo() {
            return stale.isEmpty();
        }

        public boolean refused() {
            return !refusedTeams.isEmpty();
        }
    }

    private ChunkDataReconciliation() {
    }

    /**
     * @param trackedByTeam chunks each team has data for
     * @param ownedByTeam   chunks each team owns according to the claim mod - only for teams the claim mod
     *                      knows; a team missing here is skipped (deleted teams are purged elsewhere)
     * @param claimsInTotal how many claims the claim mod reports across ALL teams it knows (not only these)
     */
    public static Plan plan(Map<UUID, Set<String>> trackedByTeam, Map<UUID, Set<String>> ownedByTeam, int claimsInTotal) {
        Map<UUID, Set<String>> stale = new HashMap<>();
        Map<UUID, Set<String>> staleOfTeamsWithoutClaims = new HashMap<>();
        int staleCount = 0;
        int trackedCount = 0;
        for (Map.Entry<UUID, Set<String>> entry : trackedByTeam.entrySet()) {
            Set<String> owned = ownedByTeam.get(entry.getKey());
            if (owned == null) {
                continue;
            }
            trackedCount += entry.getValue().size();
            Set<String> teamStale = new HashSet<>(entry.getValue());
            teamStale.removeAll(owned);
            if (teamStale.isEmpty()) {
                continue;
            }
            staleCount += teamStale.size();
            if (owned.isEmpty()) {
                // "No claims at all" can also mean "not loaded" - decided below, all such teams together.
                staleOfTeamsWithoutClaims.put(entry.getKey(), Set.copyOf(teamStale));
            } else {
                // The claim mod demonstrably knows this team's claims - what it doesn't list is gone.
                stale.put(entry.getKey(), Set.copyOf(teamStale));
            }
        }
        Set<UUID> refused = Set.of();
        if (claimsInTotal <= 0) {
            refused = Set.copyOf(staleOfTeamsWithoutClaims.keySet());
        } else {
            stale.putAll(staleOfTeamsWithoutClaims);
        }
        return new Plan(Map.copyOf(stale), staleCount, trackedCount, refused);
    }
}
