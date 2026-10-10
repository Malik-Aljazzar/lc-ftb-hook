package dev.malik.lcftbhook.mixin.ftb;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.malik.lcftbhook.bank.ClaimBatchContext;
import dev.malik.lcftbhook.adapter.ftb.FtbBulkClaimHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RequestChunkChangePacket.class, remap = false)
public class RequestChunkChangePacketMixin {
    @Inject(
            method = "handle(Ldev/ftb/mods/ftbchunks/net/RequestChunkChangePacket;Ldev/architectury/networking/NetworkManager$PacketContext;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Set;iterator()Ljava/util/Iterator;",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true,
            remap = false
    )
    private static void lcFtbHook$beginBulkOperation(
            RequestChunkChangePacket message,
            dev.architectury.networking.NetworkManager.PacketContext context,
            CallbackInfo ci
    ) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        CommandSourceStack source = player.createCommandSourceStack();
        ChunkTeamData chunkTeamData = dev.malik.lcftbhook.util.Guard.call("FTB bulk claim team lookup", () -> FtbBulkClaimHandler.resolveTeamData(message, player), null);
        if (chunkTeamData == null) {
            return;
        }

        // Guarded: if the up-front check fails, every chunk still goes through the (guarded) per-chunk check.
        if (dev.malik.lcftbhook.util.Guard.call("FTB bulk claim check", () -> FtbBulkClaimHandler.rejectIfInsufficientFunds(message, player, source, chunkTeamData), false)) {
            ci.cancel();
            return;
        }

        dev.malik.lcftbhook.util.Guard.run("FTB bulk claim batch start", () -> ClaimBatchContext.beginExecution(
                ClaimBatchContext.Operation.of(message.action().name()), message.chunks().size(), player.getUUID()));
    }

    @Inject(
            method = "handle(Ldev/ftb/mods/ftbchunks/net/RequestChunkChangePacket;Ldev/architectury/networking/NetworkManager$PacketContext;)V",
            at = @At("RETURN"),
            remap = false
    )
    private static void lcFtbHook$flushBulkMessages(
            RequestChunkChangePacket message,
            dev.architectury.networking.NetworkManager.PacketContext context,
            CallbackInfo ci
    ) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            dev.malik.lcftbhook.util.Guard.run("FTB bulk claim summary", () -> ClaimBatchContext.flush(player));
        }
    }
}
