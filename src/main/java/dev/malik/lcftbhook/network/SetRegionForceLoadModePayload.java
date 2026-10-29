package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.ForceLoadMode;
import dev.malik.lcftbhook.service.ForceLoadModeChange;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** A team manager picking the force-load mode of one of their regions (see {@link ForceLoadModeChange#setMode}). */
public record SetRegionForceLoadModePayload(UUID regionId, String modeId) implements CustomPacketPayload {
    public static final Type<SetRegionForceLoadModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "set_region_force_load_mode"));
    public static final StreamCodec<FriendlyByteBuf, SetRegionForceLoadModePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.regionId);
                buffer.writeUtf(payload.modeId);
            },
            buffer -> new SetRegionForceLoadModePayload(buffer.readUUID(), buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SetRegionForceLoadModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            UUID teamId = dev.malik.lcftbhook.service.TeamCatalog.managedTeamOf(player.server, player.getUUID());
            ForceLoadMode mode = ForceLoadMode.byId(payload.modeId, null);
            if (teamId == null || mode == null) {
                return;
            }
            ForceLoadModeChange.setMode(player.server, teamId, payload.regionId, mode);
        });
    }
}
