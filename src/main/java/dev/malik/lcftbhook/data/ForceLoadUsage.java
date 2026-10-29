package dev.malik.lcftbhook.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Per team: the regions whose force-loads were loaded at some point since the
 * team's last paid upkeep settlement. Force-load upkeep bills a region for a
 * period if it was loaded at ANY moment of it, not only at the instant the
 * settlement runs - otherwise switching mode or logging out right before each
 * settlement would make loaded chunks free. Saved with the world (by
 * {@link FtbHookSavedData}), so a restart doesn't forget usage either.
 */
public final class ForceLoadUsage {
    private final Map<UUID, Set<UUID>> usedRegionsByTeam = new HashMap<>();

    /** True if this changed anything (so the caller knows to mark the data dirty). */
    public boolean markUsed(UUID teamId, UUID regionId) {
        return usedRegionsByTeam.computeIfAbsent(teamId, id -> new HashSet<>()).add(regionId);
    }

    public boolean wasUsed(UUID teamId, UUID regionId) {
        Set<UUID> used = usedRegionsByTeam.get(teamId);
        return used != null && used.contains(regionId);
    }

    /** True if this changed anything. */
    public boolean clear(UUID teamId) {
        Set<UUID> removed = usedRegionsByTeam.remove(teamId);
        return removed != null && !removed.isEmpty();
    }

    /** What to save. @param teamKnown teams it returns false for (deleted since) are left out, so their entries don't pile up. */
    public Map<UUID, Set<UUID>> snapshot(Predicate<UUID> teamKnown) {
        Map<UUID, Set<UUID>> copy = new HashMap<>();
        usedRegionsByTeam.forEach((teamId, regions) -> {
            if (!regions.isEmpty() && teamKnown.test(teamId)) {
                copy.put(teamId, Set.copyOf(regions));
            }
        });
        return copy;
    }

    public static ForceLoadUsage of(Map<UUID, Set<UUID>> saved) {
        ForceLoadUsage usage = new ForceLoadUsage();
        saved.forEach((teamId, regions) -> usage.usedRegionsByTeam.put(teamId, new HashSet<>(regions)));
        return usage;
    }
}
