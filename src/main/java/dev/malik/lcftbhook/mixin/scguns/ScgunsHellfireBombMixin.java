package dev.malik.lcftbhook.mixin.scguns;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import dev.malik.lcftbhook.service.BlockChangeGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hellfire bombs place soul fire around their (already protected) explosion
 * block by block. The bomb is a vanilla thrown projectile, so the guard finds
 * whoever threw it as its owner.
 */
@Mixin(targets = "top.ribs.scguns.entity.throwable.ThrowableHellfireBombEntity", remap = false)
public abstract class ScgunsHellfireBombMixin {
    @WrapOperation(
            method = "createSoulFireExplosion(Lnet/minecraft/world/entity/Entity;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            remap = false
    )
    private static boolean lcFtbHook$guardSoulFire(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original,
                                                   @Local(argsOnly = true) Entity bomb) {
        return BlockChangeGuard.allows(level, pos, bomb, BlockChangeKind.BREAK) && original.call(level, pos, state, flags);
    }
}
