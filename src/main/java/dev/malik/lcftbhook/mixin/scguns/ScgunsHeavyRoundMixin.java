package dev.malik.lcftbhook.mixin.scguns;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.malik.lcftbhook.adapter.scguns.ScgunsBlockChanges;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Heavy rounds (Krahg, Osborne slug) destroy any block up to hardness 4
 * without a block entity - stone, wood, dirt. Both decide that in
 * {@code canBreakBlock} (used for punching through blocks in {@code tick} and
 * for the break itself in {@code onHitBlock}); a protected block now counts
 * as unbreakable, so the round stops at it and nothing is destroyed.
 */
@Mixin(targets = {
        "top.ribs.scguns.entity.projectile.KrahgRoundProjectileEntity",
        "top.ribs.scguns.entity.projectile.OsborneSlugProjectileEntity"
}, remap = false)
public abstract class ScgunsHeavyRoundMixin {
    @ModifyReturnValue(
            method = "canBreakBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private boolean lcFtbHook$guardHeavyRound(boolean breakable, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return breakable && ScgunsBlockChanges.byProjectile(this, level, pos, BlockChangeKind.BREAK);
    }
}
