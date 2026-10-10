package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.TeamPendingState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PendingChangeService {
    private PendingChangeService() {
    }

    /** One region's worth of force-loaded (or pending-to-load) chunks, treated as a single all-or-nothing unit - see {@link #forceLoadDismantleOrder}. */
    public record RegionForceLoadGroup(UUID regionId, List<String> chunkKeys) {
    }

    public static void removeAllForceLoads(MinecraftServer server, UUID teamId) {
        for (ClaimPort.ChunkKey chunk : Backends.claim().forceLoadedChunks(server, teamId)) {
            Backends.claim().setForceLoaded(server, chunk, false);
        }
    }

    /**
     * Currently force-loaded chunks, grouped by region in the team's own
     * region dismantle-priority order (index 0 dismantled first - same list
     * {@link ProtectionDismantleOrder} uses for protections), only regions
     * that actually have 1+ force-loaded chunks. A region's chunks are
     * dismantled all together as one unit, not one at a time - a team either
     * can or can't sustain everything it's force-loading in a given region,
     * same all-or-nothing granularity as one war being kept or dropped (see
     * {@code UpkeepSettlementService.dismantleOutgoingWarsUntilAffordable}),
     * rather than the finer per-property granularity protections use.
     */
    public static List<RegionForceLoadGroup> forceLoadDismantleOrder(MinecraftServer server, UUID teamId) {
        return groupByRegion(server, teamId, Backends.claim().forceLoadedChunks(server, teamId));
    }

    /**
     * Currently pending (queued, not yet live) force-loads, grouped the same
     * way as {@link #forceLoadDismantleOrder} but in RESTORE priority order
     * (reverse of dismantle order - the region kept longest is restored
     * first, mirroring {@link ProtectionDismantleOrder#restoreOrder}).
     */
    public static List<RegionForceLoadGroup> pendingForceLoadRestoreOrder(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Map<UUID, List<String>> byRegion = new LinkedHashMap<>();
        for (String chunkKey : pendingState.pendingForceLoads()) {
            UUID regionId = ProtectionPricing.effectiveRegion(savedData, teamId, chunkKey, pendingState);
            byRegion.computeIfAbsent(regionId, r -> new ArrayList<>()).add(chunkKey);
        }
        List<UUID> restoreOrder = new ArrayList<>(savedData.getRegionOrder(teamId));
        Collections.reverse(restoreOrder);
        return orderedGroups(byRegion, restoreOrder);
    }

    private static List<RegionForceLoadGroup> groupByRegion(MinecraftServer server, UUID teamId, List<ClaimPort.ChunkKey> chunks) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(teamId);
        Map<UUID, List<String>> byRegion = new LinkedHashMap<>();
        for (ClaimPort.ChunkKey chunk : chunks) {
            String key = ChunkPosKey.encode(chunk);
            UUID regionId = ProtectionPricing.effectiveRegion(savedData, teamId, key, pendingState);
            byRegion.computeIfAbsent(regionId, r -> new ArrayList<>()).add(key);
        }
        return orderedGroups(byRegion, savedData.getRegionOrder(teamId));
    }

    private static List<RegionForceLoadGroup> orderedGroups(Map<UUID, List<String>> byRegion, List<UUID> regionOrder) {
        List<RegionForceLoadGroup> groups = new ArrayList<>();
        for (UUID regionId : regionOrder) {
            List<String> keys = byRegion.get(regionId);
            if (keys == null || keys.isEmpty()) {
                continue;
            }
            Collections.sort(keys);
            groups.add(new RegionForceLoadGroup(regionId, List.copyOf(keys)));
        }
        return groups;
    }

    /**
     * Same real-world effect as un-force-loading a chunk directly (FTB
     * Chunks actually stops force-loading it - upkeep can't afford it right
     * now), but keeps the player's INTENT: it's queued in
     * {@code pendingForceLoads} exactly like a brand-new request that
     * couldn't apply immediately would be, so it silently resumes the
     * moment upkeep can afford it again (see {@code UpkeepSettlementService
     * .restorePendingProtections} -> {@link #applyAffordablePendingForceLoads}),
     * instead of being lost outright and requiring the player to notice and
     * re-request it. A chunk the player had ALREADY separately queued to
     * unload is left alone - they don't want it back, so there's nothing to
     * preserve intent for.
     */
    public static TeamPendingState convertOneForceLoadToPending(MinecraftServer server, UUID teamId, String chunkKey, TeamPendingState pendingState) {
        Backends.claim().setForceLoaded(server, ChunkPosKey.toChunkKey(chunkKey), false);
        return pendingState.isPendingForceUnload(chunkKey) ? pendingState : pendingState.withPendingForceLoad(chunkKey);
    }

    /**
     * Applies every pending force-unload unconditionally (dropping a chunk
     * never costs anything), then activates pending force-loads region by
     * region, in {@link #pendingForceLoadRestoreOrder} - all of a region's
     * pending chunks are activated together, or none of them, and restoration
     * stops entirely at the first region that doesn't fully fit (same
     * all-or-nothing, stop-at-first-failure granularity as
     * {@code UpkeepSettlementService.restorePendingWars}, not the finer
     * per-property granularity protections use). A region that doesn't fit
     * stays queued and is retried again next period - lower-priority regions
     * queued behind it are left untouched even if they'd individually fit,
     * matching how a lower-priority war behind an unaffordable one is never
     * separately considered either.
     */
    public static TeamPendingState applyAffordablePendingForceLoads(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            dev.malik.lcftbhook.port.currency.CurrencyAccount account
    ) {
        TeamPendingState updated = pendingState;
        for (String chunkKey : pendingState.pendingForceUnloads()) {
            Backends.claim().setForceLoaded(server, ChunkPosKey.toChunkKey(chunkKey), false);
            updated = updated.withoutPendingForceUnload(chunkKey);
        }
        for (RegionForceLoadGroup group : pendingForceLoadRestoreOrder(server, teamId, updated)) {
            if (!WarCostService.canAffordUpkeepWithMoreForceLoads(server, teamId, updated, account, group.chunkKeys().size())) {
                break;
            }
            for (String chunkKey : group.chunkKeys()) {
                Backends.claim().setForceLoaded(server, ChunkPosKey.toChunkKey(chunkKey), true);
                updated = updated.withoutPendingForceLoad(chunkKey);
            }
        }
        return updated;
    }
}
