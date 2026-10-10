package dev.malik.lcftbhook.adapter.ftb;

import dev.architectury.event.CompoundEventResult;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.service.ClaimDecision;
import dev.malik.lcftbhook.util.Guard;
import dev.malik.lcftbhook.service.ClaimLifecycleService;
import dev.malik.lcftbhook.service.ForceLoadRequestService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Translates FTB Chunks' claim/unclaim/force-load events into this mod's
 * backend-neutral hooks ({@link ClaimLifecycleService},
 * {@link ForceLoadRequestService}) and their {@link ClaimDecision}s back into
 * FTB's {@code ClaimResult}s.
 * <p>
 * FTB fires BEFORE_* strictly before its claim registry changes and only
 * clears {@code ChunkTeamData}'s cached chunk list afterwards, so a count
 * read in AFTER_* can be stale - the accurate pre-change count is captured in
 * BEFORE_* and handed to the AFTER_* hook.
 */
public final class FtbClaimEvents {
    private int pendingClaimCount = -1;
    private int pendingUnclaimCount = -1;

    public FtbClaimEvents() {
        // Guarded: a failing check refuses the action (nothing changes unchecked/unpaid),
        // a failing follow-up is only logged - our exceptions never reach FTB Chunks.
        ClaimedChunkEvent.BEFORE_CLAIM.register((source, chunk) -> Guard.call("FTB before-claim",
                () -> beforeClaim(source, chunk), toResult(ClaimDecision.failedCheck())));
        ClaimedChunkEvent.AFTER_CLAIM.register((source, chunk) -> Guard.run("FTB after-claim", () -> afterClaim(source, chunk)));
        ClaimedChunkEvent.BEFORE_UNCLAIM.register((source, chunk) -> Guard.call("FTB before-unclaim",
                () -> beforeUnclaim(source, chunk), toResult(ClaimDecision.failedCheck())));
        ClaimedChunkEvent.AFTER_UNCLAIM.register((source, chunk) -> Guard.run("FTB after-unclaim", () -> afterUnclaim(source, chunk)));
        ClaimedChunkEvent.BEFORE_LOAD.register((source, chunk) -> Guard.call("FTB force-load request",
                () -> forceLoadRequest(source, chunk, true), toResult(ClaimDecision.failedCheck())));
        ClaimedChunkEvent.BEFORE_UNLOAD.register((source, chunk) -> Guard.call("FTB force-unload request",
                () -> forceLoadRequest(source, chunk, false), toResult(ClaimDecision.failedCheck())));
    }

    private CompoundEventResult<ClaimResult> beforeClaim(CommandSourceStack source, ClaimedChunk chunk) {
        if (!FtbIntegration.isActive()) {
            return CompoundEventResult.pass();
        }
        pendingClaimCount = chunk.getTeamData().getClaimedChunks().size();
        return toResult(ClaimLifecycleService.beforeClaim(source.getServer(), source.getPlayer(), teamOf(chunk), pendingClaimCount));
    }

    private void afterClaim(CommandSourceStack source, ClaimedChunk chunk) {
        if (!FtbIntegration.isActive()) {
            return;
        }
        int countBefore = pendingClaimCount >= 0 ? pendingClaimCount : chunk.getTeamData().getClaimedChunks().size() - 1;
        pendingClaimCount = -1;
        UUID teamId = teamOf(chunk);
        if (teamId != null) {
            ClaimLifecycleService.afterClaim(source.getServer(), source.getPlayer(), teamId, FtbChunkKeys.encode(chunk.getPos()), countBefore);
        }
    }

    private CompoundEventResult<ClaimResult> beforeUnclaim(CommandSourceStack source, ClaimedChunk chunk) {
        if (!FtbIntegration.isActive()) {
            return CompoundEventResult.pass();
        }
        pendingUnclaimCount = chunk.getTeamData().getClaimedChunks().size();
        return toResult(ClaimLifecycleService.beforeUnclaim(source.getServer(), source.getPlayer(), teamOf(chunk)));
    }

    private void afterUnclaim(CommandSourceStack source, ClaimedChunk chunk) {
        if (!FtbIntegration.isActive()) {
            return;
        }
        int countBefore = pendingUnclaimCount >= 0 ? pendingUnclaimCount : chunk.getTeamData().getClaimedChunks().size() + 1;
        pendingUnclaimCount = -1;
        MinecraftServer server = source.getServer();
        if (server != null) {
            ClaimLifecycleService.afterUnclaim(server, source.getPlayer(), teamOf(chunk), FtbChunkKeys.encode(chunk.getPos()), countBefore);
        }
    }

    /** Only player requests are intercepted - this mod's own settlement toggles force-loading through the server source. */
    private CompoundEventResult<ClaimResult> forceLoadRequest(CommandSourceStack source, ClaimedChunk chunk, boolean load) {
        ServerPlayer player = source.getPlayer();
        UUID teamId = teamOf(chunk);
        if (player == null || teamId == null || !FtbIntegration.isActive()) {
            return CompoundEventResult.pass();
        }
        return toResult(ForceLoadRequestService.onRequest(player.server, player, teamId,
                FtbChunkKeys.encode(chunk.getPos()), chunk.isForceLoaded(), load));
    }

    @Nullable
    private static UUID teamOf(ClaimedChunk chunk) {
        Team team = chunk.getTeamData().getTeam();
        return team == null ? null : team.getTeamId();
    }

    static CompoundEventResult<ClaimResult> toResult(ClaimDecision decision) {
        return switch (decision.kind()) {
            case ALLOW -> CompoundEventResult.pass();
            case HANDLED -> CompoundEventResult.interruptFalse(ClaimResult.success());
            case DENY -> CompoundEventResult.interruptFalse(
                    new FtbMessageClaimResult(decision.resultId(), decision.message().copy()));
        };
    }
}
