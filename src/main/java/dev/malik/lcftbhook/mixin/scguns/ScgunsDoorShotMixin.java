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
 * Bullets that shoot doors open (the only {@code setBlock} in each of these
 * {@code onHitBlock} methods): judged as using the door, like a player
 * opening it by hand - a protected door stays shut.
 */
@Mixin(targets = {
        "top.ribs.scguns.entity.projectile.ProjectileEntity",
        "top.ribs.scguns.entity.projectile.ShatterRoundProjectileEntity",
        "top.ribs.scguns.entity.projectile.BearPackShellProjectileEntity"
}, remap = false)
public abstract class ScgunsDoorShotMixin {
    @WrapOperation(
            method = "onHitBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;DDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            remap = false
    )
    private boolean lcFtbHook$guardDoorShot(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        return ScgunsBlockChanges.byProjectile(this, level, pos, BlockChangeKind.INTERACT) && original.call(level, pos, state, flags);
    }
}
