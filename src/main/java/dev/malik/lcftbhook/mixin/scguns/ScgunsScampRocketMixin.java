package dev.malik.lcftbhook.mixin.scguns;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import dev.malik.lcftbhook.service.BlockChangeGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scamp rockets (fired by Scamp mobs) break "tank breakable" blocks around
 * their impact: per block, first a chance of dropping its loot, then the
 * block is destroyed. Both calls are guarded with the same check, so a
 * protected block neither breaks nor drops a duplicate of itself. The rocket
 * is a vanilla projectile - the guard judges it by the mob that fired it.
 */
@Mixin(targets = "top.ribs.scguns.entity.monster.ScampRocketEntity", remap = false)
public abstract class ScgunsScampRocketMixin {
    @WrapOperation(
            method = "destroyBreakableBlocks()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"),
            remap = false
    )
    private void lcFtbHook$guardDrops(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity breaker, ItemStack tool,
                                      Operation<Void> original) {
        if (lcFtbHook$allowed(level, pos)) {
            original.call(state, level, pos, blockEntity, breaker, tool);
        }
    }

    @WrapOperation(
            method = "destroyBreakableBlocks()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
            remap = false
    )
    private boolean lcFtbHook$guardBreak(Level level, BlockPos pos, boolean drop, Operation<Boolean> original) {
        return lcFtbHook$allowed(level, pos) && original.call(level, pos, drop);
    }

    private boolean lcFtbHook$allowed(Level level, BlockPos pos) {
        return BlockChangeGuard.allows(level, pos, (Entity) (Object) this, BlockChangeKind.BREAK);
    }
}
