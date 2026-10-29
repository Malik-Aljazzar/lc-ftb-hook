package dev.malik.lcftbhook.mixin.opac;

import dev.malik.lcftbhook.adapter.opac.OpacIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.pac.common.server.player.config.IPlayerConfig;

/**
 * The single place OPAC decides whether a player's force-load tickets are
 * active (owner online, offline force-loading allowed, ...). While this mod
 * runs upkeep on OPAC it loads force-loaded chunks itself, per region
 * force-load mode ({@code service.ForceLoadActivation}), so OPAC's own
 * tickets stay off - OPAC still owns which claims are marked force-loaded.
 * OPAC's tickets aren't saved with the world, so nothing to clean up on start.
 * {@code require = 0}: if a future OPAC renames this, only the takeover
 * stops - OPAC then loads by its own rules on top of ours (longer, never
 * shorter than the region's mode says).
 */
@Mixin(targets = "xaero.pac.common.server.claims.forceload.ForceLoadTicketManager", remap = false)
public abstract class ForceLoadTicketManagerMixin {
    @Inject(
            method = "ticketsShouldBeEnabled(Lxaero/pac/common/server/player/config/IPlayerConfig;Z)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void lcFtbHook$takeOverForceLoading(IPlayerConfig config, boolean owner, CallbackInfoReturnable<Boolean> cir) {
        if (dev.malik.lcftbhook.util.Guard.call("OPAC force-load takeover check", OpacIntegration::isActive, false)) {
            cir.setReturnValue(false);
        }
    }
}
