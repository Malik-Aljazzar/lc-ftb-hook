package dev.malik.lcftbhook.mixin.scguns;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.malik.lcftbhook.adapter.scguns.ScgunsBlockChanges;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Incendiary rounds: they set fire on the faces of the block they hit and
 * burn away sculk blocks around the impact - both directly per block, so each
 * fire/sculk position is checked on its own.
 */
@Mixin(targets = "top.ribs.scguns.entity.projectile.FireRoundEntity", remap = false)
public abstract class ScgunsFireRoundMixin {
    @WrapOperation(
            method = "tryPlaceWallFire(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            remap = false
    )
    private boolean lcFtbHook$guardFire(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        return ScgunsBlockChanges.byProjectile(this, level, pos, BlockChangeKind.BREAK) && original.call(level, pos, state, flags);
    }

    @WrapOperation(
            method = "clearSculkInArea(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
            remap = false
    )
    private boolean lcFtbHook$guardSculkBurn(Level level, BlockPos pos, boolean drop, Operation<Boolean> original) {
        return ScgunsBlockChanges.byProjectile(this, level, pos, BlockChangeKind.BREAK) && original.call(level, pos, drop);
    }
}
