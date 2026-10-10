package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientPendingState;
import dev.malik.lcftbhook.client.ClientUiRefresh;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record SyncPendingStatePayload(
        Map<String, String> pendingProperties,
        Set<String> pendingForceLoads,
        Set<String> pendingForceUnloads,
        Map<String, UUID> pendingRegionAssignments
) implements CustomPacketPayload {
    public static final Type<SyncPendingStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_pending_state"));
    public static final SyncPendingStatePayload EMPTY = new SyncPendingStatePayload(Map.of(), Set.of(), Set.of(), Map.of());
    public static final StreamCodec<FriendlyByteBuf, SyncPendingStatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.pendingProperties.size());
                for (var entry : payload.pendingProperties.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    buffer.writeUtf(entry.getValue());
                }
                buffer.writeCollection(payload.pendingForceLoads, FriendlyByteBuf::writeUtf);
                buffer.writeCollection(payload.pendingForceUnloads, FriendlyByteBuf::writeUtf);
                buffer.writeVarInt(payload.pendingRegionAssignments.size());
                for (var entry : payload.pendingRegionAssignments.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    buffer.writeUUID(entry.getValue());
                }
            },
            buffer -> {
                int propertyCount = buffer.readVarInt();
                Map<String, String> properties = new HashMap<>(propertyCount);
                for (int i = 0; i < propertyCount; i++) {
                    properties.put(buffer.readUtf(), buffer.readUtf());
                }
                Set<String> forceLoads = buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf);
                Set<String> forceUnloads = buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf);
                int assignmentCount = buffer.readVarInt();
                Map<String, UUID> assignments = new HashMap<>(assignmentCount);
                for (int i = 0; i < assignmentCount; i++) {
                    assignments.put(buffer.readUtf(), buffer.readUUID());
                }
                return new SyncPendingStatePayload(properties, forceLoads, forceUnloads, assignments);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncPendingStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPendingState.update(
                    payload.pendingProperties,
                    payload.pendingForceLoads,
                    payload.pendingForceUnloads,
                    payload.pendingRegionAssignments
            );
            ClientUiRefresh.refreshOpenScreens();
        });
    }
}
