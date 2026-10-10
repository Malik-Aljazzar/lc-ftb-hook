package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CreateRegionPayload(String name) implements CustomPacketPayload {
    public static final Type<CreateRegionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "create_region"));
    public static final StreamCodec<FriendlyByteBuf, CreateRegionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.name),
            buffer -> new CreateRegionPayload(buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(CreateRegionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || payload.name.isBlank()) {
                return;
            }
            java.util.UUID teamId = dev.malik.lcftbhook.service.TeamCatalog.managedTeamOf(player.server, player.getUUID());
            if (teamId == null) {
                return;
            }
            RegionService.createRegion(player.server, teamId, payload.name.trim());
        });
    }
}
