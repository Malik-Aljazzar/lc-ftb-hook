package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbchunks.FTBChunksExpected;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.malik.lcftbhook.util.Guard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * While this mod runs upkeep on FTB Chunks, it loads force-loaded chunks
 * itself, per region force-load mode ({@code service.ForceLoadActivation}).
 * FTB Chunks' own loading - its force_load_mode and offline force-loading
 * rules - is switched off: {@code mixin.ftb.FTBChunksForceLoadTicketMixin}
 * stops it from adding tickets, and {@link #releaseNativeTickets} drops the
 * ones FTB restored from the save at world load. FTB still owns the
 * force-loaded FLAG of a chunk (claim screen, limits, expiry) - only the
 * actual loading moves here. If the mixin ever fails to apply, FTB keeps
 * loading by its own rules on top of ours: chunks may then stay loaded longer
 * than their mode says, never shorter.
 */
public final class FtbForceLoadTakeover {
    private static final String FTB_TICKET_MOD_ID = "ftbchunks";

    private FtbForceLoadTakeover() {
    }

    /** Called by the mixin for every ticket FTB Chunks wants to add. Never throws - on doubt FTB's own behavior stands. */
    public static boolean isTakenOver() {
        return Guard.call("FTB force-load takeover check", FtbIntegration::isActive, false);
    }

    /** Drops every ticket FTB Chunks holds for a force-loaded chunk; ours are added by the next reconcile pass. */
    static void releaseNativeTickets(MinecraftServer server) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return;
        }
        for (ClaimedChunk chunk : FTBChunksAPI.api().getManager().getAllClaimedChunks()) {
            if (!chunk.isForceLoaded()) {
                continue;
            }
            ServerLevel level = server.getLevel(chunk.getPos().dimension());
            if (level != null) {
                FTBChunksExpected.addChunkToForceLoaded(level, FTB_TICKET_MOD_ID, chunk.getTeamData().getTeam().getId(),
                        chunk.getPos().x(), chunk.getPos().z(), false);
            }
        }
    }
}
