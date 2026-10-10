package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.service.RegionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RenameRegionPayload(UUID regionId, String newName) implements CustomPacketPayload {
    public static final Type<RenameRegionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "rename_region"));
    public static final StreamCodec<FriendlyByteBuf, RenameRegionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.regionId);
                buffer.writeUtf(payload.newName);
            },
            buffer -> new RenameRegionPayload(buffer.readUUID(), buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RenameRegionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            java.util.UUID teamId = dev.malik.lcftbhook.service.TeamCatalog.managedTeamOf(player.server, player.getUUID());
            if (teamId == null) {
                return;
            }
            RegionService.renameRegion(player.server, teamId, payload.regionId, payload.newName);
        });
    }
}
