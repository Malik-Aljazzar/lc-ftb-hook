package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.RegionPropertyKey;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClientPendingState {
    private static Map<String, String> pendingProperties = Map.of();
    private static Set<String> pendingForceLoads = Set.of();
    private static Set<String> pendingForceUnloads = Set.of();
    private static Map<String, UUID> pendingRegionAssignments = Map.of();

    private ClientPendingState() {
    }

    public static void update(
            Map<String, String> properties,
            Set<String> forceLoads,
            Set<String> forceUnloads,
            Map<String, UUID> regionAssignments
    ) {
        // Chunks whose pending force-load changed need their map highlight (hatching) redrawn.
        Set<String> changed = new java.util.HashSet<>();
        symmetricDifference(pendingForceLoads, forceLoads, changed);
        symmetricDifference(pendingForceUnloads, forceUnloads, changed);
        pendingProperties = Map.copyOf(properties);
        pendingForceLoads = Set.copyOf(forceLoads);
        pendingForceUnloads = Set.copyOf(forceUnloads);
        pendingRegionAssignments = Map.copyOf(regionAssignments);
        ClientUiRefresh.redrawMap();
        dev.malik.lcftbhook.client.xaero.XaeroHighlightRefresh.refresh(changed);
    }

    private static void symmetricDifference(Set<String> before, Set<String> after, Set<String> into) {
        before.stream().filter(key -> !after.contains(key)).forEach(into::add);
        after.stream().filter(key -> !before.contains(key)).forEach(into::add);
    }

    /** How many force-loaded chunks a region of the own team will have once its queued force-load changes apply. */
    public static int forceLoadCountAfterPending(UUID regionId) {
        dev.malik.lcftbhook.data.Region defaultRegion = ClientRegions.getDefault();
        UUID defaultId = defaultRegion != null ? defaultRegion.id() : null;
        int count = ClientRegions.forceLoadCount(regionId);
        for (String chunkKey : pendingForceLoads) {
            if (regionId.equals(ClientRegionMembership.regionOf(chunkKey, defaultId))) {
                count++;
            }
        }
        for (String chunkKey : pendingForceUnloads) {
            if (regionId.equals(ClientRegionMembership.regionOf(chunkKey, defaultId))) {
                count--;
            }
        }
        return Math.max(0, count);
    }

    public static boolean isPendingForceLoad(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return pendingForceLoads.contains(ChunkPosKeyClient.encode(dimension.location(), x, z));
    }

    public static boolean isPendingForceUnload(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return pendingForceUnloads.contains(ChunkPosKeyClient.encode(dimension.location(), x, z));
    }

    public static boolean isPendingRegionAssignment(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return pendingRegionAssignments.containsKey(ChunkPosKeyClient.encode(dimension.location(), x, z));
    }

    @Nullable
    public static UUID pendingRegionAssignmentTarget(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return pendingRegionAssignments.get(ChunkPosKeyClient.encode(dimension.location(), x, z));
    }

    @Nullable
    public static String getPendingRegionProperty(UUID regionId, ProtectionProperty property) {
        return pendingProperties.get(RegionPropertyKey.encode(regionId, property.id()));
    }

    public static boolean hasPendingRegionProperty(UUID regionId, ProtectionProperty property) {
        return pendingProperties.containsKey(RegionPropertyKey.encode(regionId, property.id()));
    }

    private static final class ChunkPosKeyClient {
        private static String encode(net.minecraft.resources.ResourceLocation dimension, int x, int z) {
            return dimension + "#" + x + "#" + z;
        }
    }
}
