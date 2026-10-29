package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.data.Region;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-side cache of the local player's own team's regions, synced from the server. */
public final class ClientRegions {
    private static List<UUID> regionOrder = List.of();
    private static Map<UUID, Region> regions = Map.of();
    private static Map<UUID, Integer> chunkCounts = Map.of();
    private static Map<UUID, Integer> billableChunkCounts = Map.of();
    private static Map<UUID, Long> currentUpkeepCopper = Map.of();
    private static Map<UUID, Long> pendingUpkeepCopper = Map.of();
    private static Map<UUID, Integer> forceLoadCounts = Map.of();
    private static Map<UUID, Long> forceLoadCurrentUpkeepCopper = Map.of();
    private static Map<UUID, Long> forceLoadPendingUpkeepCopper = Map.of();
    private static List<dev.malik.lcftbhook.data.ForceLoadMode> enabledForceLoadModes = List.of();

    private ClientRegions() {
    }

    public static void update(
            List<UUID> order,
            Map<UUID, Region> updated,
            Map<UUID, Integer> counts,
            Map<UUID, Integer> billableCounts,
            Map<UUID, Long> currentUpkeep,
            Map<UUID, Long> pendingUpkeep,
            Map<UUID, Integer> forceLoadCountsIn,
            Map<UUID, Long> forceLoadCurrentUpkeepIn,
            Map<UUID, Long> forceLoadPendingUpkeepIn,
            List<dev.malik.lcftbhook.data.ForceLoadMode> enabledForceLoadModesIn
    ) {
        regionOrder = List.copyOf(order);
        regions = Map.copyOf(updated);
        chunkCounts = Map.copyOf(counts);
        billableChunkCounts = Map.copyOf(billableCounts);
        currentUpkeepCopper = Map.copyOf(currentUpkeep);
        pendingUpkeepCopper = Map.copyOf(pendingUpkeep);
        forceLoadCounts = Map.copyOf(forceLoadCountsIn);
        forceLoadCurrentUpkeepCopper = Map.copyOf(forceLoadCurrentUpkeepIn);
        forceLoadPendingUpkeepCopper = Map.copyOf(forceLoadPendingUpkeepIn);
        enabledForceLoadModes = List.copyOf(enabledForceLoadModesIn);
    }

    /** The force-load modes the server lets regions pick, cheapest first. */
    public static List<dev.malik.lcftbhook.data.ForceLoadMode> enabledForceLoadModes() {
        return enabledForceLoadModes;
    }

    public static List<UUID> regionOrder() {
        return regionOrder;
    }

    public static Map<UUID, Region> regions() {
        return regions;
    }

    public static int chunkCount(UUID regionId) {
        return chunkCounts.getOrDefault(regionId, 0);
    }

    /** How many of this region's chunks are actually billed, after the team's free-chunk allowance is consumed. */
    public static int billableChunkCount(UUID regionId) {
        return billableChunkCounts.getOrDefault(regionId, 0);
    }

    /** This region's own current per-period upkeep, already accounting for the team's free-chunk allowance. */
    public static long currentUpkeepCopper(UUID regionId) {
        return currentUpkeepCopper.getOrDefault(regionId, 0L);
    }

    /** What this region's upkeep would be once every currently-queued pending property change for it settles. */
    public static long pendingUpkeepCopper(UUID regionId) {
        return pendingUpkeepCopper.getOrDefault(regionId, 0L);
    }

    /** How many of this region's chunks are currently force-loaded. */
    public static int forceLoadCount(UUID regionId) {
        return forceLoadCounts.getOrDefault(regionId, 0);
    }

    /** This region's own current force-load upkeep (already accounting for forceLoadUpkeepMode gating). */
    public static long forceLoadCurrentUpkeepCopper(UUID regionId) {
        return forceLoadCurrentUpkeepCopper.getOrDefault(regionId, 0L);
    }

    /** What this region's force-load upkeep would be once every currently-queued pending force-load/unload settles. */
    public static long forceLoadPendingUpkeepCopper(UUID regionId) {
        return forceLoadPendingUpkeepCopper.getOrDefault(regionId, 0L);
    }

    @Nullable
    public static Region get(UUID regionId) {
        return regions.get(regionId);
    }

    @Nullable
    public static Region getDefault() {
        for (Region region : regions.values()) {
            if (region.isDefault()) {
                return region;
            }
        }
        return null;
    }
}
