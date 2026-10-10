package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientWarState;
import dev.malik.lcftbhook.client.ClientUiRefresh;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record SyncWarStatePayload(
        long baseUpkeepCopper,
        long incomingWarCopper,
        long outgoingWarCopper,
        double warCostMultiplier,
        List<WarTeamEntry> incoming,
        List<WarTeamEntry> outgoing,
        List<WarTeamEntry> availableTargets,
        boolean canManageWar
) implements CustomPacketPayload {
    public static final Type<SyncWarStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_war_state"));
    public static final StreamCodec<FriendlyByteBuf, SyncWarStatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.baseUpkeepCopper);
                buffer.writeVarLong(payload.incomingWarCopper);
                buffer.writeVarLong(payload.outgoingWarCopper);
                buffer.writeDouble(payload.warCostMultiplier);
                buffer.writeVarInt(payload.incoming.size());
                for (WarTeamEntry entry : payload.incoming) {
                    WarTeamEntry.STREAM_CODEC.encode(buffer, entry);
                }
                buffer.writeVarInt(payload.outgoing.size());
                for (WarTeamEntry entry : payload.outgoing) {
                    WarTeamEntry.STREAM_CODEC.encode(buffer, entry);
                }
                buffer.writeVarInt(payload.availableTargets.size());
                for (WarTeamEntry entry : payload.availableTargets) {
                    WarTeamEntry.STREAM_CODEC.encode(buffer, entry);
                }
                buffer.writeBoolean(payload.canManageWar);
            },
            buffer -> {
                long base = buffer.readVarLong();
                long incoming = buffer.readVarLong();
                long outgoing = buffer.readVarLong();
                double multiplier = buffer.readDouble();
                int incomingCount = buffer.readVarInt();
                List<WarTeamEntry> incomingEntries = new java.util.ArrayList<>(incomingCount);
                for (int i = 0; i < incomingCount; i++) {
                    incomingEntries.add(WarTeamEntry.STREAM_CODEC.decode(buffer));
                }
                int outgoingCount = buffer.readVarInt();
                List<WarTeamEntry> outgoingEntries = new java.util.ArrayList<>(outgoingCount);
                for (int i = 0; i < outgoingCount; i++) {
                    outgoingEntries.add(WarTeamEntry.STREAM_CODEC.decode(buffer));
                }
                int targetCount = buffer.readVarInt();
                List<WarTeamEntry> targets = new java.util.ArrayList<>(targetCount);
                for (int i = 0; i < targetCount; i++) {
                    targets.add(WarTeamEntry.STREAM_CODEC.decode(buffer));
                }
                return new SyncWarStatePayload(
                        base,
                        incoming,
                        outgoing,
                        multiplier,
                        incomingEntries,
                        outgoingEntries,
                        targets,
                        buffer.readBoolean()
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncWarStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientWarState.update(payload);
            ClientUiRefresh.refreshWarState();
        });
    }
}
