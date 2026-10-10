package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ReorderRegionsPayload(List<UUID> newOrder) implements CustomPacketPayload {
    public static final Type<ReorderRegionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "reorder_regions"));
    public static final StreamCodec<FriendlyByteBuf, ReorderRegionsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.newOrder.size());
                for (UUID id : payload.newOrder) {
                    buffer.writeUUID(id);
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<UUID> order = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    order.add(buffer.readUUID());
                }
                return new ReorderRegionsPayload(order);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(ReorderRegionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            java.util.UUID teamId = dev.malik.lcftbhook.service.TeamCatalog.managedTeamOf(player.server, player.getUUID());
            if (teamId == null) {
                return;
            }
            RegionService.reorderRegions(player.server, teamId, payload.newOrder);
        });
    }
}
