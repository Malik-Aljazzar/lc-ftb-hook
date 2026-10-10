package dev.malik.lcftbhook.mixin.opac;

import dev.malik.lcftbhook.adapter.opac.OpacAreaClaims;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.pac.common.claims.action.request.ClaimActionRequest;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.IServerData;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * OPAC runs an area claim/unclaim as a task spread over several ticks, with
 * no API hook around the whole action. This wraps it so the team's money is
 * checked for the whole area first and the chunks' payments/refunds are
 * reported as one summary, like FTB Chunks' multi-chunk actions (see
 * {@link OpacAreaClaims}). {@code require = 0}: if OPAC changes this task,
 * area claims simply fall back to per-chunk checks and messages.
 */
@Mixin(targets = "xaero.pac.common.server.claims.player.task.PlayerAreaClaimActionSpreadoutTask", remap = false)
public abstract class OpacAreaClaimTaskMixin {
    private static final String ON_TICK = "onTick(Lxaero/pac/common/server/IServerData;"
            + "Lxaero/pac/common/server/claims/player/task/PlayerAreaClaimActionSpreadoutTask;ILjava/util/List;)V";

    @Shadow @Final private ClaimActionRequest actionRequest;
    @Shadow @Final private boolean force;
    @Shadow @Final private UUID playerId;
    @Shadow @Final private int fromX;
    @Shadow @Final private int fromZ;
    @Shadow @Final private Set<Component> customReasons;
    @Shadow @Final private Set<ClaimResult.Type> resultTypes;
    @Shadow private boolean finished;

    @Unique private boolean lcFtbHook$started;
    @Unique private Object lcFtbHook$batch;
    @Unique private final OpacAreaClaims.Outcome lcFtbHook$outcome = new OpacAreaClaims.Outcome();

    @Shadow
    private void finish(IServerData<?, ?> serverData, List<?> tasksToAdd) {
    }

    /**
     * A force-load change this mod only queued is reported as accepted (green
     * check on Xaero's map) instead of an addon refusal (red X) - see
     * {@link OpacAreaClaims.Outcome}. The success text itself is reworded to
     * "queued" by the {@code opac_texts} resource pack.
     */
    @Inject(method = "finish", at = @At("HEAD"), remap = false, require = 0)
    private void lcFtbHook$reportQueuedAsAccepted(IServerData<?, ?> serverData, List<?> tasksToAdd, CallbackInfo ci) {
        if (lcFtbHook$outcome.onlyQueued()) {
            // Plain collection edits on OPAC's own result - nothing of ours that could throw.
            resultTypes.remove(ClaimResult.Type.ADDON_FORBIDS);
            resultTypes.add(actionRequest.getAction() == xaero.pac.common.claims.action.api.ClaimingAction.UNFORCELOAD
                    ? ClaimResult.Type.SUCCESSFUL_UNFORCELOAD : ClaimResult.Type.SUCCESSFUL_FORCELOAD);
        }
    }

    @Inject(method = ON_TICK, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lcFtbHook$beforeTick(IServerData<?, ?> serverData, @org.spongepowered.asm.mixin.injection.Coerce Object holder,
                                      int perTick, List<?> tasksToAdd, CallbackInfo ci) {
        if (!lcFtbHook$started) {
            lcFtbHook$started = true;
            OpacAreaClaims.Area area = new OpacAreaClaims.Area(actionRequest.getDimension(), actionRequest.getLeft(),
                    actionRequest.getTop(), actionRequest.getRight(), actionRequest.getBottom(), fromX, fromZ, force);
            // Guarded: if the up-front check fails, every chunk still goes through the (guarded) per-chunk check.
            Component refusal = dev.malik.lcftbhook.util.Guard.call("OPAC area claim check",
                    () -> OpacAreaClaims.refusal(serverData.getServer(), playerId, actionRequest.getAction(), area), null);
            if (refusal != null) {
                customReasons.add(refusal);
                finish(serverData, tasksToAdd);
                ci.cancel();
                return;
            }
            lcFtbHook$batch = dev.malik.lcftbhook.util.Guard.call("OPAC area claim batch start", () -> OpacAreaClaims.begin(playerId, actionRequest.getAction(), area), null);
        }
        OpacAreaClaims.resume(lcFtbHook$batch);
        OpacAreaClaims.track(lcFtbHook$outcome);
    }

    @Inject(method = ON_TICK, at = @At("RETURN"), remap = false, require = 0)
    private void lcFtbHook$afterTick(IServerData<?, ?> serverData, @org.spongepowered.asm.mixin.injection.Coerce Object holder,
                                     int perTick, List<?> tasksToAdd, CallbackInfo ci) {
        OpacAreaClaims.track(null);
        Object batch = OpacAreaClaims.pause();
        if (finished) {
            dev.malik.lcftbhook.util.Guard.run("OPAC area claim summary", () -> OpacAreaClaims.finish(serverData.getServer(), playerId, batch));
        } else {
            lcFtbHook$batch = batch;
        }
    }
}
