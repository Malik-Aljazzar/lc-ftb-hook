package dev.malik.lcftbhook.port.claim;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Backend-agnostic team/claim queries and actions: which team a player is
 * on, their rank, how many chunks a team has claimed, which team (if any)
 * owns a given chunk, claiming/unclaiming/force-loading, and the online
 * members a team-wide notification should reach. This is the Claim port in
 * the Clean Architecture sense - see {@code ARCHITECTURE.md} at the repo
 * root.
 * <p>
 * Adapters: {@code adapter.ftb.FtbClaimPort} (FTB Teams/FTB Chunks) and
 * {@code adapter.opac.OpacClaimPort} (Open Parties and Claims).
 */
public interface ClaimPort {
    /** The protection settings this claim mod enforces - regions offer exactly these. */
    dev.malik.lcftbhook.port.protection.ProtectionFieldSet protectionFields();

    /** The team's claim color on maps, 0xRRGGBB. */
    int teamColor(MinecraftServer server, UUID teamId);

    @Nullable
    UUID teamIdForPlayer(MinecraftServer server, UUID playerId);

    boolean teamExists(MinecraftServer server, UUID teamId);

    String teamDisplayName(MinecraftServer server, UUID teamId);

    ClaimRank rankOf(MinecraftServer server, UUID teamId, UUID playerId);

    /** Whether this team is an actual multi-player group (party/FTB party team) as opposed to a solo player's own implicit team. */
    boolean isPartyTeam(MinecraftServer server, UUID teamId);

    /**
     * An admin-run team without players, e.g. for the spawn (FTB Teams' server
     * teams). Its claims, unclaims and force-loads cost nothing and pay no
     * refunds, it has no upkeep or wars, and its protection comes from the
     * claim mod's own team settings instead of regions.
     */
    default boolean isServerTeam(MinecraftServer server, UUID teamId) {
        return false;
    }

    /**
     * Whether this team currently participates in upkeep/wars/billing: a party
     * that at least one member still actively belongs to, or a solo team its
     * player still uses (not one left behind after joining a party).
     */
    boolean isActiveTeam(MinecraftServer server, UUID teamId);

    /** The team's owner (for a solo team: the player itself), or null if the team doesn't exist. */
    @Nullable
    UUID ownerOf(MinecraftServer server, UUID teamId);

    /** Every member (online or not), owner included. */
    List<UUID> members(MinecraftServer server, UUID teamId);

    /** Every team this backend currently knows about (solo teams included where the backend has them). */
    List<UUID> allTeams(MinecraftServer server);

    int claimedChunkCount(MinecraftServer server, UUID teamId);

    List<ChunkKey> claimedChunks(MinecraftServer server, UUID teamId);

    @Nullable
    UUID ownerTeamOf(MinecraftServer server, ResourceKey<Level> dimension, int chunkX, int chunkZ);

    /** Online members of this team, for broadcasting a team-wide notification - see {@code notifyTeamManagers}-style call sites. */
    List<ServerPlayer> onlineMembers(MinecraftServer server, UUID teamId);

    /** Chunks of this team that are force-loaded right now (live, not pending). */
    List<ChunkKey> forceLoadedChunks(MinecraftServer server, UUID teamId);

    /**
     * Unclaims one chunk the way its owning team would - so the backend's
     * normal unclaim hooks (refund, region cleanup) run exactly as for a
     * player-initiated unclaim. Returns whether it was actually unclaimed.
     */
    boolean unclaim(MinecraftServer server, ChunkKey chunk);

    boolean isForceLoaded(MinecraftServer server, ChunkKey chunk);

    /** Directly sets a chunk's force-load flag, bypassing any player-facing permission/cost check (upkeep settlement's own dismantle/restore loops already did that) - mirrors {@code ClaimedChunk.forceLoad}/{@code unForceLoad}. */
    void setForceLoaded(MinecraftServer server, ChunkKey chunk, boolean forceLoaded);

    /** A stable key for one chunk under this port - dimension + position, backend-agnostic (not FTB's own {@code ChunkDimPos} or OPAC's raw dimension+x+z triple). */
    record ChunkKey(ResourceKey<Level> dimension, ChunkPos pos) {
    }
}
