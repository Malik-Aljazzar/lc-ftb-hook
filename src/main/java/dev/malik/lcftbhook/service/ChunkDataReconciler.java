package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.network.PendingStateSync;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enforces the per-chunk data invariant of {@link ChunkDataReconciliation}
 * against the claim mod: at server start and before every upkeep period, data
 * of chunks a team no longer owns is dropped. Covers every way a claim can go
 * away or change hands, including ones that bypass the claim listener.
 */
public final class ChunkDataReconciler {
    private static boolean refusalReported;

    private ChunkDataReconciler() {
    }

    public static void reconcile(MinecraftServer server) {
        if (!FtbHookSavedData.isAvailable(server)) {
            return;
        }
        ClaimPort claims = Backends.claim();
        List<UUID> teams = claims.allTeams(server);
        if (teams.isEmpty()) {
            return; // the claim mod hasn't loaded its teams - nothing can be judged
        }
        FtbHookSavedData data = FtbHookSavedData.get(server);
        Map<UUID, Set<String>> tracked = new HashMap<>();
        Map<UUID, Set<String>> owned = new HashMap<>();
        int claimsInTotal = 0;
        for (UUID teamId : teams) {
            claimsInTotal += claims.claimedChunkCount(server, teamId);
            Set<String> keys = data.trackedChunkKeys(teamId);
            if (keys.isEmpty()) {
                continue;
            }
            tracked.put(teamId, keys);
            owned.put(teamId, claims.claimedChunks(server, teamId).stream().map(ChunkPosKey::encode).collect(Collectors.toSet()));
        }

        ChunkDataReconciliation.Plan plan = ChunkDataReconciliation.plan(tracked, owned, claimsInTotal);
        if (plan.refused()) {
            if (!refusalReported) {
                refusalReported = true;
                LCFtbHook.LOGGER.error("Chunk data check: the claim mod reports no claims at all, for any team - it most likely"
                        + " didn't load its data. This mod's data of {} chunk(s) of {} team(s) is left untouched: {}",
                        plan.staleCount(), plan.refusedTeams().size(), plan.refusedTeams());
            }
        } else {
            refusalReported = false;
        }
        if (plan.nothingToDo()) {
            return;
        }

        plan.stale().forEach((teamId, chunkKeys) -> {
            reportLostPurchases(server, data, claims, teamId, chunkKeys);
            data.dropChunkData(teamId, chunkKeys);
            LCFtbHook.LOGGER.warn("Removed this mod's data (region, marketplace, queued changes) of {} chunk(s) team {} no longer owns -"
                    + " unclaimed or transferred outside of this mod (e.g. claim expiry or an admin command): {}",
                    chunkKeys.size(), claims.teamDisplayName(server, teamId), chunkKeys.size() <= 10 ? chunkKeys : "(list omitted)");
            PendingStateSync.syncTeam(server, teamId);
        });
        RegionService.broadcastRegionMembership(server);
        MarketplaceService.broadcastChunkOwnership(server);
        MarketplaceService.broadcastUnsettledChunks(server);
    }

    /**
     * A privately owned chunk was bought with real money - if it disappears outside of this mod, there's
     * no refund path, so every such loss is logged with player, chunk and price for an admin to settle.
     */
    private static void reportLostPurchases(MinecraftServer server, FtbHookSavedData data, ClaimPort claims, UUID teamId, Set<String> chunkKeys) {
        for (String chunkKey : chunkKeys) {
            ChunkOwnership ownership = data.getChunkOwnership(teamId, chunkKey);
            if (ownership.privateOwner() == null && ownership.listingPricePerChunk() == null) {
                continue;
            }
            String owner = ownership.privateOwner() == null ? "-" : server.getProfileCache() == null ? ownership.privateOwner().toString()
                    : server.getProfileCache().get(ownership.privateOwner()).map(profile -> profile.getName()).orElse(ownership.privateOwner().toString());
            LCFtbHook.LOGGER.warn("Chunk {} of team {} left this mod's knowledge (unclaimed or transferred outside of it) with"
                    + " marketplace data: private owner {}, listed for {} - no refund was made, settle it manually if needed",
                    chunkKey, claims.teamDisplayName(server, teamId), owner,
                    ownership.listingPricePerChunk() == null ? "-" : ownership.listingPricePerChunk());
        }
    }
}
