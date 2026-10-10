package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.port.claim.ClaimRank;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ClaimPort} backed by OPAC (Open Parties and Claims). OPAC claims are
 * always owned by a player UUID, never by a party, so a "team" here is either
 * a real OPAC party (every member's claims folded together) or, for a player
 * in no party, that player's own UUID - FTB Teams' solo-team convention.
 */
public final class OpacClaimPort implements ClaimPort {
    @Override
    public dev.malik.lcftbhook.port.protection.ProtectionFieldSet protectionFields() {
        return OpacProtectionFields.SET;
    }

    /** The claims color the team's owner set in OPAC. */
    @Override
    public int teamColor(MinecraftServer server, UUID teamId) {
        UUID owner = ownerOf(server, teamId);
        var config = owner == null ? null : OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(owner);
        Integer color = config == null ? null
                : config.getEffective(xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions.CLAIMS_COLOR);
        return color != null ? color & 0xFFFFFF : 0xFFFFFF;
    }

    @Override
    @Nullable
    public UUID teamIdForPlayer(MinecraftServer server, UUID playerId) {
        IServerPartyAPI party = partyManager(server).getPartyByMember(playerId);
        return party != null ? party.getId() : playerId;
    }

    @Override
    public boolean teamExists(MinecraftServer server, UUID teamId) {
        if (partyManager(server).getPartyById(teamId) != null) {
            return true;
        }
        // Solo convention: teamId is a player UUID directly - "exists" if we
        // have ever seen this player (online now, or known to the profile
        // cache from a previous login).
        return server.getPlayerList().getPlayer(teamId) != null
                || server.getProfileCache() != null && server.getProfileCache().get(teamId).isPresent();
    }

    @Override
    public String teamDisplayName(MinecraftServer server, UUID teamId) {
        IServerPartyAPI party = partyManager(server).getPartyById(teamId);
        if (party != null) {
            return partyName(server, party);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(teamId);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(teamId).map(profile -> profile.getName()).orElse(teamId.toString());
        }
        return teamId.toString();
    }

    @Override
    public ClaimRank rankOf(MinecraftServer server, UUID teamId, UUID playerId) {
        IServerPartyAPI party = partyManager(server).getPartyById(teamId);
        if (party == null) {
            // Solo convention: full rights over one's own implicit team.
            return teamId.equals(playerId) ? OpacClaimRank.ADMIN_OR_OWNER : OpacClaimRank.NONE;
        }
        IPartyMemberAPI member = party.getMemberInfo(playerId);
        if (member == null) {
            // Not a member - but a member of an allied party counts as an ally
            // (OPAC's party alliances are its equivalent of FTB's ally rank).
            IServerPartyAPI playerParty = partyManager(server).getPartyByMember(playerId);
            return playerParty != null && party.isAlly(playerParty.getId()) ? OpacClaimRank.ALLY : OpacClaimRank.NONE;
        }
        return member.isOwner() ? OpacClaimRank.ADMIN_OR_OWNER : new OpacClaimRank(member.getRank(), false);
    }

    @Override
    public boolean isPartyTeam(MinecraftServer server, UUID teamId) {
        return partyManager(server).getPartyById(teamId) != null;
    }

    @Override
    public boolean isActiveTeam(MinecraftServer server, UUID teamId) {
        IServerPartyAPI party = partyManager(server).getPartyById(teamId);
        if (party != null) {
            return party.getMemberCount() > 0;
        }
        // Solo convention: only "in use" while that player isn't in a party -
        // mirrors FTB's rule that a solo team left behind after joining a
        // party no longer participates in upkeep/wars.
        return teamExists(server, teamId) && partyManager(server).getPartyByMember(teamId) == null;
    }

    @Override
    @Nullable
    public UUID ownerOf(MinecraftServer server, UUID teamId) {
        IServerPartyAPI party = partyManager(server).getPartyById(teamId);
        if (party != null) {
            return party.getOwner().getUUID();
        }
        return teamExists(server, teamId) ? teamId : null;
    }

    @Override
    public List<UUID> members(MinecraftServer server, UUID teamId) {
        return memberIdsOf(server, teamId);
    }

    @Override
    public List<UUID> allTeams(MinecraftServer server) {
        List<UUID> teams = new ArrayList<>(partyManager(server).getAllStream().map(IServerPartyAPI::getId).toList());
        claimsManager(server).getPlayerInfoStream()
                .map(IServerPlayerClaimInfoAPI::getPlayerId)
                .filter(playerId -> partyManager(server).getPartyByMember(playerId) == null)
                .forEach(teams::add);
        return teams;
    }

    @Override
    public int claimedChunkCount(MinecraftServer server, UUID teamId) {
        return memberIdsOf(server, teamId).stream()
                .mapToInt(playerId -> claimsManager(server).hasPlayerInfo(playerId)
                        ? claimsManager(server).getPlayerInfo(playerId).getClaimCount()
                        : 0)
                .sum();
    }

    @Override
    public List<ChunkKey> claimedChunks(MinecraftServer server, UUID teamId) {
        List<ChunkKey> keys = new ArrayList<>();
        IServerClaimsManagerAPI claims = claimsManager(server);
        for (UUID playerId : memberIdsOf(server, teamId)) {
            if (!claims.hasPlayerInfo(playerId)) {
                continue;
            }
            IServerPlayerClaimInfoAPI info = claims.getPlayerInfo(playerId);
            for (Map.Entry<ResourceLocation, IPlayerDimensionClaimsAPI> dimEntry : info.getStream().toList()) {
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimEntry.getKey());
                for (IPlayerClaimPosListAPI posList : dimEntry.getValue().getStream().toList()) {
                    posList.getStream().forEach(pos -> keys.add(new ChunkKey(dimension, pos)));
                }
            }
        }
        return keys;
    }

    @Override
    @Nullable
    public UUID ownerTeamOf(MinecraftServer server, ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        IPlayerChunkClaimAPI claim = claimsManager(server).get(dimension.location(), chunkX, chunkZ);
        if (claim == null) {
            return null;
        }
        UUID ownerId = claim.getPlayerId();
        IServerPartyAPI party = partyManager(server).getPartyByMember(ownerId);
        return party != null ? party.getId() : ownerId;
    }

    @Override
    public List<ServerPlayer> onlineMembers(MinecraftServer server, UUID teamId) {
        IServerPartyAPI party = partyManager(server).getPartyById(teamId);
        if (party != null) {
            return party.getOnlineMemberStream().toList();
        }
        ServerPlayer solo = server.getPlayerList().getPlayer(teamId);
        return solo != null ? List.of(solo) : List.of();
    }

    @Override
    public List<ChunkKey> forceLoadedChunks(MinecraftServer server, UUID teamId) {
        return claimedChunks(server, teamId).stream().filter(chunk -> isForceLoaded(server, chunk)).toList();
    }

    /**
     * Goes through OPAC's normal (listener-firing) unclaim path as the claim's
     * own owner, limits bypassed - so refunds and region cleanup run through
     * {@link OpacClaimListener} exactly as for a player unclaim.
     */
    @Override
    public boolean unclaim(MinecraftServer server, ChunkKey chunk) {
        ResourceLocation dimension = chunk.dimension().location();
        IPlayerChunkClaimAPI claim = claimsManager(server).get(dimension, chunk.pos().x, chunk.pos().z);
        if (claim == null) {
            return false;
        }
        return OpacInternalActions.run(() -> {
            claimsManager(server).tryToUnclaim(dimension, claim.getPlayerId(), dimension,
                    chunk.pos().x, chunk.pos().z, chunk.pos().x, chunk.pos().z, true);
            return claimsManager(server).get(dimension, chunk.pos().x, chunk.pos().z) == null;
        });
    }

    /** Removes a claim WITHOUT any listener firing - for undoing a claim this mod refused after the fact. */
    static void unclaimSilently(MinecraftServer server, ChunkKey chunk) {
        claimsManager(server).unclaim(chunk.dimension().location(), chunk.pos().x, chunk.pos().z);
    }

    @Override
    public boolean isForceLoaded(MinecraftServer server, ChunkKey chunk) {
        IPlayerChunkClaimAPI claim = claimsManager(server).get(chunk.dimension().location(), chunk.pos().x, chunk.pos().z);
        return claim != null && claim.isForceloadable();
    }

    @Override
    public void setForceLoaded(MinecraftServer server, ChunkKey chunk, boolean forceLoaded) {
        IServerClaimsManagerAPI claims = claimsManager(server);
        ResourceLocation dimension = chunk.dimension().location();
        IPlayerChunkClaimAPI claim = claims.get(dimension, chunk.pos().x, chunk.pos().z);
        if (claim == null) {
            return;
        }
        // force=true (last arg) bypasses distance/limit checks, so fromX/fromZ
        // (normally the acting player's own position) can safely be the
        // claimed chunk's own position - see IServerClaimsManagerAPI's javadoc
        // on tryToForceload's "force" parameter.
        OpacInternalActions.run(() -> {
            claims.tryToForceload(dimension, claim.getPlayerId(), dimension, chunk.pos().x, chunk.pos().z,
                    chunk.pos().x, chunk.pos().z, forceLoaded, true);
            return true;
        });
    }

    private static List<UUID> memberIdsOf(MinecraftServer server, UUID teamId) {
        IServerPartyAPI party = partyManager(server).getPartyById(teamId);
        if (party == null) {
            return List.of(teamId);
        }
        return party.getMemberInfoStream().map(IPartyMemberAPI::getUUID).toList();
    }

    /** The "Party Name" the owner set in OPAC's player config, else OPAC's default name. */
    static String partyName(MinecraftServer server, IServerPartyAPI party) {
        var ownerConfig = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(party.getOwner().getUUID());
        String name = ownerConfig == null ? null
                : ownerConfig.getEffective(xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions.PARTY_NAME);
        return name == null || name.isBlank() ? party.getDefaultName() : name;
    }

    private static IServerClaimsManagerAPI claimsManager(MinecraftServer server) {
        return OpenPACServerAPI.get(server).getServerClaimsManager();
    }

    private static IPartyManagerAPI partyManager(MinecraftServer server) {
        return OpenPACServerAPI.get(server).getPartyManager();
    }

    /**
     * Maps OPAC's {@link PartyMemberRank} (MEMBER &lt; CLAIMER &lt; MODERATOR
     * &lt; ADMIN, plus a separate {@code isOwner()} flag OPAC tracks outside
     * the rank enum entirely) onto this mod's 3-tier {@link ClaimRank}.
     * MODERATOR/ADMIN/the party owner are treated as officer-or-better (they
     * can manage money/regions/wars); every other recognized member (MEMBER,
     * CLAIMER) is member-or-better but not officer. A member of an allied
     * party is ally-or-better only.
     */
    private record OpacClaimRank(@Nullable PartyMemberRank rank, boolean ally) implements ClaimRank {
        private static final OpacClaimRank ADMIN_OR_OWNER = new OpacClaimRank(PartyMemberRank.ADMIN, false);
        private static final OpacClaimRank ALLY = new OpacClaimRank(null, true);
        private static final OpacClaimRank NONE = new OpacClaimRank(null, false);

        @Override
        public boolean isMemberOrBetter() {
            return rank != null;
        }

        @Override
        public boolean isAllyOrBetter() {
            return rank != null || ally;
        }

        @Override
        public boolean isOfficerOrBetter() {
            return rank == PartyMemberRank.ADMIN || rank == PartyMemberRank.MODERATOR;
        }
    }
}
