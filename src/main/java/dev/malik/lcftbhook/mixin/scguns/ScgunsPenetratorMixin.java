package dev.malik.lcftbhook.mixin.scguns;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import dev.malik.lcftbhook.service.BlockChangeGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * The Penetrator block drills a 3x3, 10 blocks long tunnel when powered,
 * collecting each block's drops and then setting it to air - everything but
 * bedrock, even when placed outside a claim and aimed into it. Nobody is
 * responsible for a powered block, so each tunnel block follows the claim's
 * explosion rules (like TNT). Drops and removal are guarded with the same
 * check: a protected block is neither removed nor duplicated into the drops.
 */
@Mixin(targets = "top.ribs.scguns.blockentity.PenetratorBlockEntity", remap = false)
public abstract class ScgunsPenetratorMixin {
    @WrapOperation(
            method = "explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;"),
            remap = false
    )
    private List<ItemStack> lcFtbHook$guardDrops(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity,
                                                 Operation<List<ItemStack>> original) {
        return BlockChangeGuard.allows(level, pos, null, BlockChangeKind.BREAK) ? original.call(state, level, pos, blockEntity) : List.of();
    }

    @WrapOperation(
            method = "explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            remap = false
    )
    private boolean lcFtbHook$guardRemoval(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        return BlockChangeGuard.allows(level, pos, null, BlockChangeKind.BREAK) && original.call(level, pos, state, flags);
    }
}
