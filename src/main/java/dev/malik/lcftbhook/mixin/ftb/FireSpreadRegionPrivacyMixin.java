package dev.malik.lcftbhook.mixin.ftb;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup;
import dev.malik.lcftbhook.adapter.ftb.FtbRegionPrivacy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FTB Chunks' fire spread protection (world config
 * {@code fire_spread_protection}) stops fire from spreading into another
 * team's claim unless that claim's block edit is Public - read from the team
 * property, never from the Region. Answered by the destination chunk's Region
 * (and its private owner's override) instead.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.util.FireSpreadHelper", remap = false)
public abstract class FireSpreadRegionPrivacyMixin {
    @WrapOperation(
            method = "shouldPreventFireSpread",
            at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbteams/api/Team;getProperty(Ldev/ftb/mods/ftbteams/api/property/TeamProperty;)Ljava/lang/Object;"),
            remap = false
    )
    private static Object lcFtbHook$destinationRegionEditMode(Team team, TeamProperty<?> property, Operation<Object> original,
                                                             @Local(argsOnly = true) LevelReader levelReader,
                                                             @Local(argsOnly = true, ordinal = 1) BlockPos toPos) {
        // FTB only gets here for a Level (it checks instanceof right before).
        return levelReader instanceof Level level
                ? FtbRegionPrivacy.propertyFor(FtbRegionLookup.chunkAt(level, toPos), property, () -> original.call(team, property))
                : original.call(team, property);
    }
}
