package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

/** Conversions between FTB Library's {@link ChunkDimPos} and this mod's backend-neutral chunk keys. */
public final class FtbChunkKeys {
    private FtbChunkKeys() {
    }

    public static String encode(ChunkDimPos pos) {
        return ChunkPosKey.encode(pos.dimension().location(), pos.x(), pos.z());
    }

    public static ChunkDimPos toChunkDimPos(String key) {
        ClaimPort.ChunkKey chunk = ChunkPosKey.toChunkKey(key);
        return new ChunkDimPos(chunk.dimension(), chunk.pos().x, chunk.pos().z);
    }

    public static ChunkDimPos toChunkDimPos(ClaimPort.ChunkKey chunk) {
        return new ChunkDimPos(chunk.dimension(), chunk.pos().x, chunk.pos().z);
    }

    public static ClaimPort.ChunkKey toChunkKey(ChunkDimPos pos) {
        return new ClaimPort.ChunkKey(pos.dimension(), new ChunkPos(pos.x(), pos.z()));
    }

    /** A team's claimed chunks read straight from its own object - works even once FTB Teams has already unregistered it. */
    public static List<ClaimPort.ChunkKey> claimedChunksOf(Team team) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return List.of();
        }
        return FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().stream()
                .map(chunk -> toChunkKey(chunk.getPos()))
                .toList();
    }
}
