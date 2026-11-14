package dev.malik.lcftbhook.mixin.scguns;

import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import dev.malik.lcftbhook.service.BlockChangeGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Scamp Tank mob crushes "tank breakable" blocks (granite, lanterns,
 * barrels, sandbags, supply crates) while driving and charging, ignoring the
 * mobGriefing rule. Every crushed block (drops included) goes through this one
 * method; a block protected against mob griefing is left alone.
 */
@Mixin(targets = "top.ribs.scguns.entity.monster.ScampTankEntity", remap = false)
public abstract class ScgunsScampTankMixin {
    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void lcFtbHook$guardCrush(BlockPos pos, BlockState state, boolean charging, CallbackInfo ci) {
        Entity tank = (Entity) (Object) this;
        if (!BlockChangeGuard.allows(tank.level(), pos, tank, BlockChangeKind.BREAK)) {
            ci.cancel();
        }
    }
}
