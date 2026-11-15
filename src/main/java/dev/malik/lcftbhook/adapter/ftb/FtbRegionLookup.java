package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.RegionEnforcementContext;
import dev.malik.lcftbhook.service.RegionService;
import dev.malik.lcftbhook.util.Guard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Region / private-ownership lookups for the FTB Chunks protection mixins,
 * which only have FTB's own {@link ClaimedChunk} at hand. Delegates to the
 * backend-neutral {@link RegionService}.
 */
public final class FtbRegionLookup {
    private FtbRegionLookup() {
    }

    public static Region resolveRegion(@Nullable ClaimedChunk chunk) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Team team = chunk == null ? null : chunk.getTeamData().getTeam();
        if (server == null || team == null) {
            return Region.createDefault();
        }
        return RegionService.resolveRegion(server, team.getTeamId(), FtbChunkKeys.encode(chunk.getPos()));
    }

    public static ChunkOwnership resolveOwnership(@Nullable ClaimedChunk chunk) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Team team = chunk == null ? null : chunk.getTeamData().getTeam();
        if (server == null || team == null) {
            return ChunkOwnership.EMPTY;
        }
        return FtbHookSavedData.get(server).getChunkOwnership(team.getTeamId(), FtbChunkKeys.encode(chunk.getPos()));
    }

    /** The chunk's Region and private ownership, as {@code canPlayerUse} reads them (the Default region for wilderness). */
    public static RegionEnforcementContext.Resolved contextFor(@Nullable ClaimedChunk chunk) {
        return new RegionEnforcementContext.Resolved(resolveRegion(chunk), resolveOwnership(chunk));
    }

    @Nullable
    public static ClaimedChunk chunkAt(Level level, BlockPos pos) {
        return FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos));
    }

    /**
     * Runs an FTB Chunks check that asks {@code canPlayerUse} directly
     * (instead of through {@code shouldPreventInteraction}) with the Region
     * context of the chunk at {@code pos} - without it, the region privacy
     * check can't know which chunk is meant and every region field reads as
     * its minimum (Public). Guarded: if the lookup fails, the check runs
     * without a context, as it did before.
     */
    public static <T> T callInContextAt(Level level, BlockPos pos, Supplier<T> check) {
        RegionEnforcementContext.Resolved context = Guard.call("FTB region context", () -> contextFor(chunkAt(level, pos)), null);
        return context == null ? check.get() : RegionEnforcementContext.callWith(context, check);
    }
}
