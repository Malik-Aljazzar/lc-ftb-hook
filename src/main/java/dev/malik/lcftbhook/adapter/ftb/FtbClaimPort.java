package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.port.claim.ClaimRank;
import dev.malik.lcftbhook.adapter.ftb.FtbTeamCatalog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link ClaimPort} backed by FTB Teams + FTB Chunks.
 */
public final class FtbClaimPort implements ClaimPort {
    @Override
    public dev.malik.lcftbhook.port.protection.ProtectionFieldSet protectionFields() {
        return FtbProtectionFields.SET;
    }

    @Override
    public int teamColor(MinecraftServer server, UUID teamId) {
        return team(teamId).map(t -> t.getProperty(dev.ftb.mods.ftbteams.api.property.TeamProperties.COLOR).rgb()).orElse(0xFFFFFF);
    }

    @Override
    @Nullable
    public UUID teamIdForPlayer(MinecraftServer server, UUID playerId) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerId).map(Team::getTeamId).orElse(null);
    }

    @Override
    public boolean teamExists(MinecraftServer server, UUID teamId) {
        return FTBTeamsAPI.api().isManagerLoaded()
                && FTBTeamsAPI.api().getManager().getTeamByID(teamId).isPresent();
    }

    @Override
    public String teamDisplayName(MinecraftServer server, UUID teamId) {
        return team(teamId).map(t -> t.getName().getString()).orElse("");
    }

    @Override
    public ClaimRank rankOf(MinecraftServer server, UUID teamId, UUID playerId) {
        TeamRank rank = team(teamId).map(t -> t.getRankForPlayer(playerId)).orElse(TeamRank.NONE);
        return new FtbClaimRank(rank);
    }

    @Override
    public boolean isPartyTeam(MinecraftServer server, UUID teamId) {
        return team(teamId).map(Team::isPartyTeam).orElse(false);
    }

    @Override
    public boolean isServerTeam(MinecraftServer server, UUID teamId) {
        return team(teamId).map(Team::isServerTeam).orElse(false);
    }

    @Override
    public boolean isActiveTeam(MinecraftServer server, UUID teamId) {
        return team(teamId).map(t -> FtbTeamCatalog.isTracked(server, t)).orElse(false);
    }

    @Override
    @Nullable
    public UUID ownerOf(MinecraftServer server, UUID teamId) {
        return team(teamId).map(FtbPartyOwners::effectiveOwner).orElse(null);
    }

    @Override
    public List<UUID> members(MinecraftServer server, UUID teamId) {
        return team(teamId).map(t -> List.copyOf(t.getMembers())).orElseGet(List::of);
    }

    @Override
    public List<UUID> allTeams(MinecraftServer server) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return List.of();
        }
        return FTBTeamsAPI.api().getManager().getTeams().stream()
                .filter(Team::isValid)
                .map(Team::getTeamId)
                .toList();
    }

    @Override
    public int claimedChunkCount(MinecraftServer server, UUID teamId) {
        return team(teamId).map(t -> FTBChunksAPI.api().getManager().getOrCreateData(t).getClaimedChunks().size()).orElse(0);
    }

    @Override
    public List<ChunkKey> claimedChunks(MinecraftServer server, UUID teamId) {
        return team(teamId).map(t -> {
            List<ChunkKey> keys = new ArrayList<>();
            for (ClaimedChunk chunk : FTBChunksAPI.api().getManager().getOrCreateData(t).getClaimedChunks()) {
                ChunkDimPos pos = chunk.getPos();
                keys.add(new ChunkKey(pos.dimension(), new ChunkPos(pos.x(), pos.z())));
            }
            return keys;
        }).orElseGet(List::of);
    }

    @Override
    @Nullable
    public UUID ownerTeamOf(MinecraftServer server, ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return null;
        }
        ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(dimension, chunkX, chunkZ));
        return chunk == null ? null : chunk.getTeamData().getTeam().getTeamId();
    }

    @Override
    public List<ServerPlayer> onlineMembers(MinecraftServer server, UUID teamId) {
        return team(teamId).map(t -> List.<ServerPlayer>copyOf(t.getOnlineMembers())).orElseGet(List::of);
    }

    @Override
    public List<ChunkKey> forceLoadedChunks(MinecraftServer server, UUID teamId) {
        return team(teamId).map(t -> FTBChunksAPI.api().getManager().getOrCreateData(t).getForceLoadedChunks().stream()
                .map(chunk -> FtbChunkKeys.toChunkKey(chunk.getPos()))
                .toList()).orElseGet(List::of);
    }

    @Override
    public boolean unclaim(MinecraftServer server, ChunkKey chunk) {
        ClaimedChunk claimed = findChunk(chunk);
        if (claimed == null) {
            return false;
        }
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        return claimed.getTeamData().unclaim(source, FtbChunkKeys.toChunkDimPos(chunk), false).isSuccess();
    }

    @Override
    public boolean isForceLoaded(MinecraftServer server, ChunkKey chunk) {
        ClaimedChunk claimed = findChunk(chunk);
        return claimed != null && claimed.isForceLoaded();
    }

    @Override
    public void setForceLoaded(MinecraftServer server, ChunkKey chunk, boolean forceLoaded) {
        ClaimedChunk claimed = findChunk(chunk);
        if (claimed == null) {
            return;
        }
        ChunkTeamData chunkData = claimed.getTeamData();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        ChunkDimPos pos = new ChunkDimPos(chunk.dimension(), chunk.pos().x, chunk.pos().z);
        if (forceLoaded) {
            chunkData.forceLoad(source, pos, false);
        } else {
            chunkData.unForceLoad(source, pos, false);
        }
    }

    @Nullable
    private ClaimedChunk findChunk(ChunkKey chunk) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return null;
        }
        return FTBChunksAPI.api().getManager()
                .getChunk(new ChunkDimPos(chunk.dimension(), chunk.pos().x, chunk.pos().z));
    }

    private java.util.Optional<Team> team(UUID teamId) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return java.util.Optional.empty();
        }
        return FTBTeamsAPI.api().getManager().getTeamByID(teamId);
    }

    private record FtbClaimRank(TeamRank rank) implements ClaimRank {
        @Override
        public boolean isMemberOrBetter() {
            return rank.isMemberOrBetter();
        }

        @Override
        public boolean isAllyOrBetter() {
            return rank.isAllyOrBetter();
        }

        @Override
        public boolean isOfficerOrBetter() {
            return rank.isOfficerOrBetter();
        }
    }
}
