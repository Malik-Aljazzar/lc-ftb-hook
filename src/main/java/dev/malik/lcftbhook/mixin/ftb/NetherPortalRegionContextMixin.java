package dev.malik.lcftbhook.mixin.ftb;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbteams.api.property.PrivacyProperty;
import dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FTB Chunks 2101.1.24 keeps vanilla from building a nether portal for a
 * player in another team's claim (world config {@code nether_portal_creation})
 * unless the player may edit blocks there - asked with {@code canPlayerUse}
 * directly, so it runs with the Region context of the portal's destination
 * chunk (in the destination dimension, not the player's current one).
 * {@code @Pseudo}: the class doesn't exist in older FTB Chunks (2101.1.22),
 * where this simply doesn't apply.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbchunks.util.ClaimRespectingNetherPortals", remap = false)
public abstract class NetherPortalRegionContextMixin {
    @WrapOperation(
            method = "tryCreatePortal",
            at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbchunks/api/ChunkTeamData;canPlayerUse(Lnet/minecraft/server/level/ServerPlayer;Ldev/ftb/mods/ftbteams/api/property/PrivacyProperty;)Z"),
            remap = false
    )
    private static boolean lcFtbHook$portalInRegionContext(ChunkTeamData teamData, ServerPlayer player, PrivacyProperty property,
                                                           Operation<Boolean> original,
                                                           @Local(argsOnly = true) ServerLevel destLevel,
                                                           @Local(argsOnly = true) BlockPos exitPos) {
        return FtbRegionLookup.callInContextAt(destLevel, exitPos, () -> original.call(teamData, player, property));
    }
}
