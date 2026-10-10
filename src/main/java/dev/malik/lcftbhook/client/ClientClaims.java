package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.ClaimedChunkInfo;
import dev.malik.lcftbhook.data.TeamDisplayInfo;
import dev.malik.lcftbhook.data.ViewerRank;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * The client's view of all claims and of the local player's own team
 * standing, synced by the server's {@code ClaimMapSync} - works the same
 * whatever claim mod the server uses (no FTB/OPAC client API involved).
 */
public final class ClientClaims {
    private static Map<String, ClaimedChunkInfo> chunks = Map.of();
    private static Map<UUID, TeamDisplayInfo> teams = Map.of();
    @Nullable
    private static UUID ownTeam;
    private static Map<UUID, ViewerRank> ranks = Map.of();

    private ClientClaims() {
    }

    public static void updateMap(Map<String, ClaimedChunkInfo> newChunks, Map<UUID, TeamDisplayInfo> newTeams) {
        chunks = Map.copyOf(newChunks);
        teams = Map.copyOf(newTeams);
        ClientUiRefresh.redrawMap();
        dev.malik.lcftbhook.client.xaero.XaeroHighlightRefresh.refreshAll();
    }

    public static void updateViewer(@Nullable UUID newOwnTeam, Map<UUID, ViewerRank> newRanks) {
        ownTeam = newOwnTeam;
        ranks = Map.copyOf(newRanks);
        ClientUiRefresh.refreshOpenScreens();
    }

    public static void reset() {
        chunks = Map.of();
        teams = Map.of();
        ownTeam = null;
        ranks = Map.of();
    }

    @Nullable
    public static ClaimedChunkInfo chunk(String chunkKey) {
        return chunks.get(chunkKey);
    }

    @Nullable
    public static ClaimedChunkInfo chunk(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return chunks.get(ChunkPosKey.encode(dimension.location(), chunkX, chunkZ));
    }

    @Nullable
    public static UUID ownerTeam(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ClaimedChunkInfo info = chunk(dimension, chunkX, chunkZ);
        return info != null ? info.teamId() : null;
    }

    @Nullable
    public static TeamDisplayInfo team(UUID teamId) {
        return teams.get(teamId);
    }

    /** Team name in the team's own map color. */
    public static Component coloredName(UUID teamId) {
        TeamDisplayInfo info = teams.get(teamId);
        return info == null
                ? Component.literal("?")
                : Component.literal(info.name()).withStyle(style -> style.withColor(TextColor.fromRgb(info.color())));
    }

    /** The local player's standing in {@code teamId}; NONE for unknown/null teams. */
    public static ViewerRank rankIn(@Nullable UUID teamId) {
        return teamId == null ? ViewerRank.NONE : ranks.getOrDefault(teamId, ViewerRank.NONE);
    }

    @Nullable
    public static UUID ownTeam() {
        return ownTeam;
    }

    public static boolean isOwnOfficer() {
        return rankIn(ownTeam).isOfficerOrBetter();
    }
}
