package dev.malik.lcftbhook.mixin.ftb;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.malik.lcftbhook.adapter.ftb.FtbRegionPrivacy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FTB Chunks' piston protection (world config {@code piston_protection}, on
 * by default) stops pistons from pushing or pulling blocks across into
 * another team's claim unless that claim's block edit is Public - read from
 * the team property, never from the Region. Answered by the Region (and
 * private owner override) of the affected chunk instead: FTB's
 * {@code prevent(editProp, srcClaim, dstClaim)} is asked once per affected
 * chunk, and {@code dstClaim} is that chunk.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.util.PistonHelper", remap = false)
public abstract class PistonRegionPrivacyMixin {
    @WrapOperation(
            method = "prevent",
            at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbteams/api/Team;getProperty(Ldev/ftb/mods/ftbteams/api/property/TeamProperty;)Ljava/lang/Object;"),
            remap = false
    )
    private static Object lcFtbHook$affectedRegionEditMode(Team team, TeamProperty<?> property, Operation<Object> original,
                                                          @Local(argsOnly = true, ordinal = 1) ClaimedChunk dstClaim) {
        return FtbRegionPrivacy.propertyFor(dstClaim, property, () -> original.call(team, property));
    }
}
