package dev.malik.lcftbhook.mixin.ftb;

import dev.malik.lcftbhook.adapter.ftb.FtbForceLoadTakeover;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * The single place FTB Chunks adds or removes a force-load ticket. Adding is
 * skipped while this mod loads force-loaded chunks itself (see
 * {@link FtbForceLoadTakeover}); removing always goes through, so no FTB
 * ticket can get stuck. {@code require = 0}: if a future FTB Chunks renames
 * this, only the takeover stops - FTB then loads by its own rules again.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.neoforge.FTBChunksExpectedImpl", remap = false)
public abstract class FTBChunksForceLoadTicketMixin {
    @Inject(method = "addChunkToForceLoaded", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void lcFtbHook$skipNativeTicket(ServerLevel level, String modId, UUID owner, int chunkX, int chunkZ,
                                                   boolean add, CallbackInfo ci) {
        if (add && FtbForceLoadTakeover.isTakenOver()) {
            ci.cancel();
        }
    }
}
