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
 * Incendiary grenade rounds: their explosion itself is a normal (already
 * protected) explosion, but the fire they spread around it is placed block by
 * block with {@code setBlock} - each fire position is checked on its own.
 */
@Mixin(targets = "top.ribs.scguns.entity.projectile.FireGrenadeRoundEntity", remap = false)
public abstract class ScgunsFireGrenadeMixin {
    @WrapOperation(
            method = "explode()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            remap = false
    )
    private boolean lcFtbHook$guardFire(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        return ScgunsBlockChanges.byProjectile(this, level, pos, BlockChangeKind.BREAK) && original.call(level, pos, state, flags);
    }
}
