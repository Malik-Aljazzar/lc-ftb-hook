package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.ClaimedChunkInfo;
import dev.malik.lcftbhook.data.TeamDisplayInfo;
import dev.malik.lcftbhook.data.ViewerRank;
import dev.malik.lcftbhook.network.SyncClaimMapPayload;
import dev.malik.lcftbhook.network.SyncViewerRanksPayload;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps every client's claim map (who owns which chunk, force-loads, team
 * names/colors) and each player's own team standing up to date, through the
 * claim port - so client features work with any claim mod. Compares a fresh
 * snapshot every two seconds and only sends what changed.
 */
public final class ClaimMapSync {
    private static final int INTERVAL_TICKS = 40;

    private static int ticks;
    private static SyncClaimMapPayload lastMap;
    private static final Map<UUID, SyncViewerRanksPayload> lastViewer = new HashMap<>();

    private ClaimMapSync() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (++ticks < INTERVAL_TICKS || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        ticks = 0;
        try {
            SyncClaimMapPayload map = snapshot(server);
            if (!map.equals(lastMap)) {
                lastMap = map;
                PacketDistributor.sendToAllPlayers(map);
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncViewer(server, player, map, false);
            }
        } catch (RuntimeException e) {
            LCFtbHook.LOGGER.error("Claim map sync failed", e);
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        SyncClaimMapPayload map = lastMap != null ? lastMap : snapshot(player.server);
        lastMap = map;
        PacketDistributor.sendToPlayer(player, map);
        syncViewer(player.server, player, map, true);
    }

    public static void reset() {
        ticks = 0;
        lastMap = null;
        lastViewer.clear();
    }

    private static SyncClaimMapPayload snapshot(MinecraftServer server) {
        ClaimPort claims = Backends.claim();
        Map<String, ClaimedChunkInfo> chunks = new HashMap<>();
        Map<UUID, TeamDisplayInfo> teams = new HashMap<>();
        for (UUID teamId : claims.allTeams(server)) {
            Set<ClaimPort.ChunkKey> forceLoaded = new HashSet<>(claims.forceLoadedChunks(server, teamId));
            boolean hasClaims = false;
            for (ClaimPort.ChunkKey chunk : claims.claimedChunks(server, teamId)) {
                chunks.put(ChunkPosKey.encode(chunk), new ClaimedChunkInfo(teamId, forceLoaded.contains(chunk)));
                hasClaims = true;
            }
            if (hasClaims) {
                teams.put(teamId, new TeamDisplayInfo(claims.teamDisplayName(server, teamId), claims.teamColor(server, teamId)));
            }
        }
        return new SyncClaimMapPayload(chunks, teams);
    }

    private static void syncViewer(MinecraftServer server, ServerPlayer player, SyncClaimMapPayload map, boolean force) {
        ClaimPort claims = Backends.claim();
        UUID playerId = player.getUUID();
        UUID ownTeam = claims.teamIdForPlayer(server, playerId);
        Set<UUID> teams = new HashSet<>(map.teams().keySet());
        if (ownTeam != null) {
            teams.add(ownTeam);
        }
        Map<UUID, ViewerRank> ranks = new HashMap<>();
        for (UUID teamId : teams) {
            ViewerRank rank = ViewerRank.of(claims.rankOf(server, teamId, playerId));
            if (rank != ViewerRank.NONE) {
                ranks.put(teamId, rank);
            }
        }
        SyncViewerRanksPayload payload = new SyncViewerRanksPayload(ownTeam, ranks);
        if (force || !payload.equals(lastViewer.get(playerId))) {
            lastViewer.put(playerId, payload);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
