package dev.malik.lcftbhook.mixin.scguns;

import com.llamalad7.mixinextras.sugar.Local;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import dev.malik.lcftbhook.service.BlockChangeGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Beam weapons (mining lasers etc.) mine the block they point at and shatter
 * fragile blocks, removing them with {@code removeBlock}/{@code destroyBlock}
 * - no block-break event, so a beam could mine straight into a claim.
 * {@code updateBlockMining} is the one place every beam tick on a block goes
 * through (fragile check, then mining progress); a block the player may not
 * break is skipped entirely, so no progress builds up on it either.
 * <p>
 * The target's last parameter is ScorchedGuns' own {@code Gun} type, which
 * this mod doesn't compile against - the handler takes only the vanilla
 * arguments it needs via {@code @Local(argsOnly = true)}.
 */
@Mixin(targets = "top.ribs.scguns.common.BeamHandlerCommon$BeamMiningManager", remap = false)
public abstract class ScgunsBeamMiningMixin {
    @Inject(
            method = "updateBlockMining(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerPlayer;Ltop/ribs/scguns/common/Gun;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void lcFtbHook$guardBeam(CallbackInfo ci, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos,
                                            @Local(argsOnly = true) ServerPlayer player) {
        if (!BlockChangeGuard.allows(level, pos, player, BlockChangeKind.BREAK)) {
            ci.cancel();
        }
    }
}
