package dev.malik.lcftbhook.adapter.scguns;

import dev.malik.lcftbhook.mixin.scguns.ScgunsProjectileAccessor;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import dev.malik.lcftbhook.service.BlockChangeGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Shared bits of the ScorchedGuns hooks ({@code mixin/scguns}): who is
 * behind a ScorchedGuns bullet, and which blocks a bullet can prime.
 * ScorchedGuns' own bullets are plain entities, not vanilla projectiles, so
 * their shooter is read through {@link ScgunsProjectileAccessor}; everything
 * else (ownership chain, claim rules) is {@link BlockChangeGuard}'s job.
 */
public final class ScgunsBlockChanges {
    private static final Set<ResourceLocation> PRIMABLE_KEGS = Set.of(
            ResourceLocation.fromNamespaceAndPath("scguns", "powder_keg"),
            ResourceLocation.fromNamespaceAndPath("scguns", "nitro_keg")
    );

    private ScgunsBlockChanges() {
    }

    /**
     * Whether the bullet {@code projectile} may change the block at
     * {@code pos} - judged by its shooter (player or mob) or, without one,
     * as a change nobody is responsible for.
     */
    public static boolean byProjectile(Object projectile, Level level, BlockPos pos, BlockChangeKind kind) {
        Entity bullet = (Entity) projectile;
        LivingEntity shooter = bullet instanceof ScgunsProjectileAccessor accessor ? accessor.lcFtbHook$getShooter() : null;
        return BlockChangeGuard.allows(level, pos, shooter != null ? shooter : bullet, kind);
    }

    /** TNT and ScorchedGuns' powder/nitro kegs - the blocks a bullet removes and lights. */
    public static boolean isPrimable(BlockState state) {
        return state.is(Blocks.TNT) || PRIMABLE_KEGS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }
}
