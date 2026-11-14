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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Claim protection for what every ScorchedGuns bullet does to a block it
 * hits: shooting out fragile blocks (glass, panes, ice, lanterns, ...) and
 * priming TNT / powder kegs / nitro kegs. ScorchedGuns does both with direct
 * {@code destroyBlock}/{@code removeBlock} calls that no claim mod sees.
 * A protected block simply survives the hit - the bullet still stops there.
 * <p>
 * Optional config {@code lc_ftb_hook_scguns.mixins.json}; all ScorchedGuns
 * call sites verified with {@code javap} against ScorchedGuns 1.5 (they
 * compile to {@code invokevirtual Level.destroyBlock/setBlock}).
 */
@Mixin(targets = "top.ribs.scguns.entity.projectile.ProjectileEntity", remap = false)
public abstract class ScgunsProjectileMixin {
    @WrapOperation(
            method = "onHit(Lnet/minecraft/world/phys/HitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
            remap = false
    )
    private boolean lcFtbHook$guardFragileBlock(Level level, BlockPos pos, boolean drop, Operation<Boolean> original) {
        return ScgunsBlockChanges.byProjectile(this, level, pos, BlockChangeKind.BREAK) && original.call(level, pos, drop);
    }

    /** Returning true = "handled", exactly what primeTNT returns for any other block - nothing is removed or lit. */
    @Inject(
            method = "primeTNT(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void lcFtbHook$guardPriming(Level level, BlockState state, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ScgunsBlockChanges.isPrimable(state) && !ScgunsBlockChanges.byProjectile(this, level, pos, BlockChangeKind.BREAK)) {
            cir.setReturnValue(true);
        }
    }
}
