package dev.malik.lcftbhook.mixin.scguns;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The shooter of a ScorchedGuns bullet (player, mob, or null) - every ScorchedGuns bullet extends this class. */
@Mixin(targets = "top.ribs.scguns.entity.projectile.ProjectileEntity", remap = false)
public interface ScgunsProjectileAccessor {
    @Accessor(value = "shooter", remap = false)
    LivingEntity lcFtbHook$getShooter();
}
