package dev.malik.lcftbhook.mixin.sable;

import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.service.ProtectionResolution;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sable Collision Damage (a Create: Aeronautics addon - a crashing
 * airship/vehicle destroys terrain blocks it collides with) breaks blocks by
 * calling {@code Level.destroyBlock(BlockPos, boolean)} directly from its own
 * physics-tick logic, completely bypassing the normal block-break/explosion
 * events FTB Chunks protection actually listens to - a claimed, explosion-
 * protected region's terrain was just as destructible by a crash as
 * unclaimed wilderness. Redirects that one call inside {@code
 * SablePreSolverDamage.destroyBlockFragileLike} and, if the target position
 * is in a claimed chunk whose Region currently has explosion protection
 * active, silently fails the destruction instead - the exact same "couldn't
 * destroy this block" outcome the method already produces for an
 * unbreakable block (e.g. bedrock), which correctly skips the impact
 * particles/velocity-slowdown effects that would otherwise still fire for a
 * destruction that never actually happened.
 * <p>
 * Only covers the real-world destroy path (the OTHER call in the same method,
 * against a Sable "sub-level"/ship's own embedded blocks, is left alone - a
 * ship's own hull isn't a claimed chunk, and protecting those is a separate,
 * not-yet-implemented feature this mod already has a placeholder for
 * elsewhere ("Sable Sublevels - Coming soon" in the Region Settings screen).
 * <p>
 * Lives in its own optional, soft-fail mixin config ({@code
 * lc_ftb_hook_sable.mixins.json}, required=false) - same reasoning as the
 * Xaero integration mixins: Sable Collision Damage is a rare third-party
 * addon most players won't have installed, so this must never crash mod
 * loading if the target class doesn't exist. remap = false because the
 * target class itself belongs to that third-party mod - the {@code
 * ServerLevel.destroyBlock} INVOKE target inside {@code @At} still resolves
 * normally against Mojang mappings, same as every other third-party-
 * targeting mixin in this project that redirects a vanilla call (e.g.
 * {@code ChunkScreenPanelMixin}'s {@code Set.isEmpty()} redirect).
 * <p>
 * {@code destroyBlock(BlockPos, boolean)} is actually a DEFAULT method
 * declared on the {@code LevelWriter} interface, not {@code Level}/{@code
 * ServerLevel} directly - but {@code javap}-disassembling the real shipped
 * jar (not just reading its GitHub source) showed their compiler emitted
 * {@code invokevirtual ServerLevel.destroyBlock(...)} for this call (the
 * concrete declared-variable type, not the declaring interface) - an
 * {@code @At} target of {@code LevelWriter;destroyBlock(...)} silently
 * never matched anything (this mixin's {@code defaultRequire=0} masks a
 * missed target instead of erroring), so the redirect was a complete no-op
 * and every collision kept destroying terrain exactly as before. Always
 * verify the ACTUAL bytecode of a third-party target, not just its source -
 * default-interface-method call sites are compiled inconsistently across
 * javac versions/call-site shapes.
 */
@Mixin(targets = "com.sable.collision_damage.SablePreSolverDamage", remap = false)
public abstract class SableCollisionExplosionProtectionMixin {
    @Redirect(
            method = "destroyBlockFragileLike",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"
            ),
            remap = false
    )
    private static boolean lcFtbHook$checkExplosionProtection(ServerLevel level, BlockPos pos, boolean dropBlock) {
        // Guarded: if the check fails, the block is treated as protected - nothing gets destroyed.
        if (dev.malik.lcftbhook.util.Guard.call("Sable explosion protection check", () -> lcFtbHook$isExplosionProtected(level, pos), true)) {
            return false;
        }
        return level.destroyBlock(pos, dropBlock);
    }

    private static boolean lcFtbHook$isExplosionProtected(ServerLevel level, BlockPos pos) {
        // Unclaimed land resolves to the Default region at minimum = unprotected.
        ClaimPort.ChunkKey chunk = new ClaimPort.ChunkKey(level.dimension(), new ChunkPos(pos));
        Region region = RegionService.resolveRegion(level.getServer(), chunk);
        ChunkOwnership ownership = RegionService.resolveOwnership(level.getServer(), chunk);
        // Protected if any explosion protection of the active claim mod is on.
        for (ProtectionProperty property : ProtectionProperty.withRole(ProtectionFieldSpec.Role.EXPLOSIONS)) {
            if (!ProtectionResolution.isAtMinimum(region, ownership, property)) {
                return true;
            }
        }
        return false;
    }
}
