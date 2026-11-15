package dev.malik.lcftbhook.mixin.ftb;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup;
import dev.malik.lcftbhook.adapter.ftb.FtbRegionPrivacy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FTB Chunks' fluid flow protection (world config
 * {@code flowing_fluid_protection}) stops fluids from flowing into another
 * team's claim unless that claim's block edit is Public - read from the team
 * property, never from the Region. Answered by the Region (and private owner
 * override) of the chunk the fluid would flow into instead.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.util.FlowingFluidHelper", remap = false)
public abstract class FlowingFluidRegionPrivacyMixin {
    @WrapOperation(
            method = "checkDir",
            at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbteams/api/Team;getProperty(Ldev/ftb/mods/ftbteams/api/property/TeamProperty;)Ljava/lang/Object;"),
            remap = false
    )
    private static Object lcFtbHook$destinationRegionEditMode(Team team, TeamProperty<?> property, Operation<Object> original,
                                                             @Local(argsOnly = true) Level level,
                                                             @Local(argsOnly = true) BlockPos pos,
                                                             @Local(argsOnly = true) Direction dir) {
        return FtbRegionPrivacy.propertyFor(FtbRegionLookup.chunkAt(level, pos.relative(dir)), property, () -> original.call(team, property));
    }
}
