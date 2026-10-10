package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;

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
}
