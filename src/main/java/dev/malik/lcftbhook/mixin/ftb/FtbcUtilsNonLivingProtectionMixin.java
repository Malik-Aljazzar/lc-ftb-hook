package dev.malik.lcftbhook.mixin.ftb;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbteams.api.property.PrivacyProperty;
import dev.malik.lcftbhook.adapter.ftb.FtbChunkProtection;
import dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup;
import dev.malik.lcftbhook.util.Guard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FTB Chunks' damage check for item frames, armor stands, boats and
 * minecarts ({@code FTBCUtils.shouldProtectNonLivingEntity}, used for every
 * damage source, projectiles included) asks the claim's team directly instead
 * of going through {@code shouldPreventInteraction}:
 * <ul>
 *   <li>a player's attack: {@code canPlayerUse(NONLIVING_ENTITY_ATTACK_MODE)} -
 *   run with the Region context of the entity's chunk, otherwise the region
 *   privacy check can't know the chunk and reads the field as Public;</li>
 *   <li>an explosion: the team-wide {@code canExplosionsDamageTerrain()} -
 *   answered by the chunk's Region instead, like explosions on blocks.</li>
 * </ul>
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.FTBCUtils", remap = false)
public abstract class FtbcUtilsNonLivingProtectionMixin {
    @WrapOperation(
            method = "shouldProtectNonLivingEntity",
            at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbchunks/api/ChunkTeamData;canPlayerUse(Lnet/minecraft/server/level/ServerPlayer;Ldev/ftb/mods/ftbteams/api/property/PrivacyProperty;)Z"),
            remap = false
    )
    private static boolean lcFtbHook$attackInRegionContext(ChunkTeamData teamData, ServerPlayer player, PrivacyProperty property,
                                                           Operation<Boolean> original, @Local(argsOnly = true) Entity entity) {
        return FtbRegionLookup.callInContextAt(entity.level(), entity.blockPosition(), () -> original.call(teamData, player, property));
    }

    @WrapOperation(
            method = "shouldProtectNonLivingEntity",
            at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbchunks/api/ChunkTeamData;canExplosionsDamageTerrain()Z"),
            remap = false
    )
    private static boolean lcFtbHook$regionExplosions(ChunkTeamData teamData, Operation<Boolean> original,
                                                      @Local(argsOnly = true) Entity entity) {
        // Guarded: if the Region lookup fails, FTB's own team setting applies.
        Boolean region = Guard.call("FTB non-living entity explosion check", () -> {
            ClaimedChunk chunk = FtbRegionLookup.chunkAt(entity.level(), entity.blockPosition());
            return chunk != null ? FtbChunkProtection.regionAllowsExplosions(chunk) : null;
        }, null);
        return region != null ? region : original.call(teamData);
    }
}
