package dev.malik.lcftbhook.mixin.ftb.client;

import dev.ftb.mods.ftbchunks.net.ChunkChangeResponsePacket;
import dev.malik.lcftbhook.client.ClientClaimPrices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = ChunkChangeResponsePacket.class, remap = false)
public class ChunkChangeResponsePacketClientMixin {
    @Inject(
            method = "handle(Ldev/ftb/mods/ftbchunks/net/ChunkChangeResponsePacket;Ldev/architectury/networking/NetworkManager$PacketContext;)V",
            at = @At("HEAD"),
            remap = false
    )
    private static void lcFtbHook$trackChunkUpdate(
            ChunkChangeResponsePacket packet,
            dev.architectury.networking.NetworkManager.PacketContext context,
            CallbackInfo ci
    ) {
        ClientClaimPrices.noteChunkUpdate(
                packet.totalChunks(),
                packet.changedChunks(),
                packet.problems()
        );
    }
}
