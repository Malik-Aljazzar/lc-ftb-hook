package dev.malik.lcftbhook.mixin.ftb;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.RegionEnforcementContext;
import dev.malik.lcftbhook.service.RegionService;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publishes the resolved Region of the chunk being checked so that
 * {@code canPlayerUse} (which only receives the privacy property, not the
 * chunk) can read that Region's own stored value instead of an FTB team
 * property.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl", remap = false)
public abstract class ClaimedChunkManagerProtectionMixin {
    @Inject(method = "shouldPreventInteraction", at = @At("HEAD"), remap = false)
    private void lcFtbHook$markRegionContext(
            Entity actor,
            InteractionHand hand,
            BlockPos pos,
            Protection protection,
            Entity targetEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        RegionEnforcementContext.clear();
        // Guarded: without a context, the privacy checks fall back to the Default region / FTB.
        dev.malik.lcftbhook.util.Guard.run("FTB region context", () -> lcFtbHook$setContext(actor, pos));
    }

    private void lcFtbHook$setContext(Entity actor, BlockPos pos) {
        if (actor instanceof ServerPlayer player && player.level() != null) {
            ClaimedChunk chunk = ((dev.ftb.mods.ftbchunks.api.ClaimedChunkManager) this)
                    .getChunk(new ChunkDimPos(player.level(), pos));
            RegionEnforcementContext.set(
                    chunk != null ? dev.malik.lcftbhook.adapter.ftb.FtbRegionLookup.resolveRegion(chunk) : Region.createDefault(),
                    chunk != null ? resolveOwnership(chunk) : ChunkOwnership.EMPTY
            );
        } else {
            RegionEnforcementContext.set(Region.createDefault(), ChunkOwnership.EMPTY);
        }
    }

    @Inject(method = "shouldPreventInteraction", at = @At("RETURN"), remap = false)
    private void lcFtbHook$clearRegionContext(
            Entity actor,
            InteractionHand hand,
            BlockPos pos,
            Protection protection,
            Entity targetEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        RegionEnforcementContext.clear();
    }

    private static ChunkOwnership resolveOwnership(ClaimedChunk chunk) {
        Team team = chunk.getTeamData().getTeam();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (team == null || server == null) {
            return ChunkOwnership.EMPTY;
        }
        return FtbHookSavedData.get(server).getChunkOwnership(team.getTeamId(), dev.malik.lcftbhook.adapter.ftb.FtbChunkKeys.encode(chunk.getPos()));
    }
}
