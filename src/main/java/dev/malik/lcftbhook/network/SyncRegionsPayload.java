package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.data.Region;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The viewer's own team's regions (order + definitions incl. protection
 * values). Team-scoped (sent only to that team's online members), unlike
 * {@link SyncRegionMembershipPayload}, since region names/prices are private.
 */
public record SyncRegionsPayload(
        List<UUID> regionOrder,
        Map<UUID, Region> regions,
        Map<UUID, Integer> chunkCounts,
        Map<UUID, Integer> billableChunkCounts,
        Map<UUID, Long> currentUpkeepCopper,
        Map<UUID, Long> pendingUpkeepCopper,
        Map<UUID, Integer> forceLoadCounts,
        Map<UUID, Long> forceLoadCurrentUpkeepCopper,
        Map<UUID, Long> forceLoadPendingUpkeepCopper
) implements CustomPacketPayload {
    public static final Type<SyncRegionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_regions"));
    public static final StreamCodec<FriendlyByteBuf, SyncRegionsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.regionOrder.size());
                for (UUID id : payload.regionOrder) {
                    buffer.writeUUID(id);
                }
                buffer.writeVarInt(payload.regions.size());
                for (Region region : payload.regions.values()) {
                    buffer.writeUUID(region.id());
                    buffer.writeUtf(region.name());
                    buffer.writeBoolean(region.isDefault());
                    buffer.writeBoolean(region.allowPrivateSelling());
                    buffer.writeUtf(region.buyerAccess());
                    buffer.writeVarInt(region.properties().size());
                    for (var entry : region.properties().entrySet()) {
                        buffer.writeUtf(entry.getKey());
                        buffer.writeUtf(entry.getValue());
                    }
                    buffer.writeVarInt(payload.chunkCounts.getOrDefault(region.id(), 0));
                    buffer.writeVarInt(payload.billableChunkCounts.getOrDefault(region.id(), 0));
                    buffer.writeVarLong(payload.currentUpkeepCopper.getOrDefault(region.id(), 0L));
                    buffer.writeVarLong(payload.pendingUpkeepCopper.getOrDefault(region.id(), 0L));
                    buffer.writeVarInt(payload.forceLoadCounts.getOrDefault(region.id(), 0));
                    buffer.writeVarLong(payload.forceLoadCurrentUpkeepCopper.getOrDefault(region.id(), 0L));
                    buffer.writeVarLong(payload.forceLoadPendingUpkeepCopper.getOrDefault(region.id(), 0L));
                }
            },
            buffer -> {
                int orderSize = buffer.readVarInt();
                List<UUID> order = new ArrayList<>(orderSize);
                for (int i = 0; i < orderSize; i++) {
                    order.add(buffer.readUUID());
                }
                int regionCount = buffer.readVarInt();
                Map<UUID, Region> regions = new HashMap<>(regionCount);
                Map<UUID, Integer> chunkCounts = new HashMap<>(regionCount);
                Map<UUID, Integer> billableChunkCounts = new HashMap<>(regionCount);
                Map<UUID, Long> currentUpkeep = new HashMap<>(regionCount);
                Map<UUID, Long> pendingUpkeep = new HashMap<>(regionCount);
                Map<UUID, Integer> forceLoadCounts = new HashMap<>(regionCount);
                Map<UUID, Long> forceLoadCurrentUpkeep = new HashMap<>(regionCount);
                Map<UUID, Long> forceLoadPendingUpkeep = new HashMap<>(regionCount);
                for (int i = 0; i < regionCount; i++) {
                    UUID id = buffer.readUUID();
                    String name = buffer.readUtf();
                    boolean isDefault = buffer.readBoolean();
                    boolean allowPrivateSelling = buffer.readBoolean();
                    String buyerAccess = buffer.readUtf();
                    int propCount = buffer.readVarInt();
                    Map<String, String> properties = new HashMap<>(propCount);
                    for (int j = 0; j < propCount; j++) {
                        properties.put(buffer.readUtf(), buffer.readUtf());
                    }
                    regions.put(id, new Region(id, name, isDefault, properties, allowPrivateSelling, buyerAccess));
                    chunkCounts.put(id, buffer.readVarInt());
                    billableChunkCounts.put(id, buffer.readVarInt());
                    currentUpkeep.put(id, buffer.readVarLong());
                    pendingUpkeep.put(id, buffer.readVarLong());
                    forceLoadCounts.put(id, buffer.readVarInt());
                    forceLoadCurrentUpkeep.put(id, buffer.readVarLong());
                    forceLoadPendingUpkeep.put(id, buffer.readVarLong());
                }
                return new SyncRegionsPayload(
                        order, regions, chunkCounts, billableChunkCounts, currentUpkeep, pendingUpkeep,
                        forceLoadCounts, forceLoadCurrentUpkeep, forceLoadPendingUpkeep
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncRegionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRegions.update(
                    payload.regionOrder, payload.regions, payload.chunkCounts, payload.billableChunkCounts,
                    payload.currentUpkeepCopper, payload.pendingUpkeepCopper,
                    payload.forceLoadCounts, payload.forceLoadCurrentUpkeepCopper, payload.forceLoadPendingUpkeepCopper
            );
            dev.malik.lcftbhook.client.ClientUiRefresh.refreshRegionList();
        });
    }
}
