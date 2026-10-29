package dev.malik.lcftbhook.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Invariants of one team's saved data, checked (and repaired) whenever it is
 * loaded - so data written by an older version, or left inconsistent by a bug,
 * is fixed in a defined way instead of breaking things later:
 * <ul>
 *   <li>exactly one Default region,</li>
 *   <li>the region order lists every region exactly once,</li>
 *   <li>chunk assignments only point at existing, non-Default regions
 *       (Default is "no entry"),</li>
 *   <li>pending changes only reference existing regions.</li>
 * </ul>
 * Repairs never invent protection or ownership - they only drop or re-point
 * references that can't be valid.
 */
final class TeamLinkInvariants {
    private TeamLinkInvariants() {
    }

    /** The (possibly repaired) region data of one team, and what was repaired - empty if it was consistent. */
    record Checked(List<UUID> regionOrder, Map<UUID, Region> regions, Map<String, UUID> chunkRegions,
                   TeamPendingState pending, List<String> repairs) {
    }

    static Checked check(List<UUID> storedOrder, Map<UUID, Region> storedRegions,
                         Map<String, UUID> storedChunkRegions, TeamPendingState storedPending) {
        List<String> repairs = new ArrayList<>();
        Map<UUID, Region> regions = new LinkedHashMap<>(storedRegions);
        UUID defaultId = ensureSingleDefault(regions, repairs);
        List<UUID> order = repairedOrder(storedOrder, regions, repairs);
        Map<String, UUID> chunkRegions = repairedChunkRegions(storedChunkRegions, regions, defaultId, repairs);
        TeamPendingState pending = repairedPending(storedPending, regions, repairs);
        return new Checked(order, regions, chunkRegions, pending, repairs);
    }

    private static UUID ensureSingleDefault(Map<UUID, Region> regions, List<String> repairs) {
        List<Region> defaults = regions.values().stream().filter(Region::isDefault).toList();
        if (defaults.isEmpty()) {
            Region created = Region.createDefault();
            regions.put(created.id(), created);
            repairs.add("added the missing Default region");
            return created.id();
        }
        for (Region extra : defaults.subList(1, defaults.size())) {
            regions.put(extra.id(), new Region(extra.id(), extra.name(), false, extra.properties(),
                    extra.allowPrivateSelling(), extra.buyerAccess(), extra.forceLoadMode()));
            repairs.add("region " + extra.id() + " was a second Default region - kept as a normal region");
        }
        return defaults.get(0).id();
    }

    private static List<UUID> repairedOrder(List<UUID> stored, Map<UUID, Region> regions, List<String> repairs) {
        Set<UUID> order = new LinkedHashSet<>();
        stored.stream().filter(regions::containsKey).forEach(order::add);
        order.addAll(regions.keySet());
        List<UUID> result = new ArrayList<>(order);
        if (!result.equals(stored)) {
            repairs.add("region order didn't match the regions - fixed");
        }
        return result;
    }

    private static Map<String, UUID> repairedChunkRegions(Map<String, UUID> stored, Map<UUID, Region> regions,
                                                          UUID defaultId, List<String> repairs) {
        Map<String, UUID> result = new HashMap<>();
        int dropped = 0;
        for (Map.Entry<String, UUID> chunk : stored.entrySet()) {
            if (regions.containsKey(chunk.getValue()) && !chunk.getValue().equals(defaultId)) {
                result.put(chunk.getKey(), chunk.getValue());
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            repairs.add(dropped + " chunk(s) pointed at a missing or the Default region - now in Default");
        }
        return result;
    }

    private static TeamPendingState repairedPending(TeamPendingState pending, Map<UUID, Region> regions, List<String> repairs) {
        Set<UUID> referenced = new LinkedHashSet<>(pending.pendingRegionAssignments().values());
        for (String key : pending.pendingProperties().keySet()) {
            try {
                referenced.add(RegionPropertyKey.regionId(key));
            } catch (RuntimeException malformed) {
                repairs.add("ignored a malformed pending change key '" + key + "'");
            }
        }
        TeamPendingState result = pending;
        for (UUID regionId : referenced) {
            if (regionId != null && !regions.containsKey(regionId)) {
                result = result.withoutRegionReferences(regionId);
                repairs.add("dropped pending changes for missing region " + regionId);
            }
        }
        return result;
    }
}
