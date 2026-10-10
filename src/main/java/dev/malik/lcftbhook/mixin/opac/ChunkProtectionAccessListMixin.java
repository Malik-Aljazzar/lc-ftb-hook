package dev.malik.lcftbhook.mixin.opac;

import dev.malik.lcftbhook.adapter.opac.OpacAccessLists;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;

import java.util.UUID;

/**
 * The only hook into OPAC's protection internals: OPAC decides "is this accessor exempt
 * from this protection option" by exception group only; a private chunk
 * owner's per-player whitelist/blacklist is applied on top. Everything else
 * about protection goes through OPAC's public API. {@code require = 0}: if a
 * future OPAC renames this method, only the lists stop working.
 */
@Mixin(targets = "xaero.pac.common.server.claims.protection.ChunkProtection", remap = false)
public abstract class ChunkProtectionAccessListMixin {
    @Inject(
            method = "checkPlayerGroupExceptionOption(Ljava/lang/String;Lxaero/pac/common/server/player/config/api/v2/IPlayerConfigOptionSpecAPI;Lxaero/pac/common/server/player/config/IPlayerConfig;Lnet/minecraft/world/entity/Entity;Ljava/util/UUID;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void lcFtbHook$applyAccessList(String groupId, IPlayerConfigOptionSpecAPI<String> option, IPlayerConfig claimConfig,
                                           Entity accessor, UUID accessorId, CallbackInfoReturnable<Boolean> cir) {
        if (claimConfig == null || claimConfig.getPlayerId() == null) {
            return;
        }
        // Guarded: if the list check fails, OPAC's own decision stands.
        Boolean result = dev.malik.lcftbhook.util.Guard.call("OPAC access list check", () -> OpacAccessLists.apply(option.getId(), claimConfig.getPlayerId(),
                claimConfig.getSubId(), accessor, accessorId, cir.getReturnValueZ()), null);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
