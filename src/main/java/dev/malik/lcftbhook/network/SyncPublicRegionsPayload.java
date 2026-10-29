package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientPublicRegions;
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
 * Every team's regions - name, protection values, and which one is that
 * team's Default - broadcast globally (unlike {@link SyncRegionsPayload},
 * which stays team-private because it also carries chunk counts and copper
 * prices). This carries neither: a chunk's protection status is visible
 * gameplay information, not private team data, so hovering another team's
 * chunk (see {@code ProtectionInfoLines}) should show the exact same
 * protection info that team itself sees, even for a non-Default region.
 */
public record SyncPublicRegionsPayload(
        Map<UUID, Region> regionsById,
        Map<UUID, UUID> defaultRegionByTeam
) implements CustomPacketPayload {
    public static final Type<SyncPublicRegionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_public_regions"));
    public static final StreamCodec<FriendlyByteBuf, SyncPublicRegionsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.regionsById.size());
                for (Region region : payload.regionsById.values()) {
                    buffer.writeUUID(region.id());
                    buffer.writeUtf(region.name());
                    buffer.writeUtf(region.buyerAccess());
                    buffer.writeVarInt(region.properties().size());
                    for (var entry : region.properties().entrySet()) {
                        buffer.writeUtf(entry.getKey());
                        buffer.writeUtf(entry.getValue());
                    }
                }
                buffer.writeVarInt(payload.defaultRegionByTeam.size());
                for (var entry : payload.defaultRegionByTeam.entrySet()) {
                    buffer.writeUUID(entry.getKey());
                    buffer.writeUUID(entry.getValue());
                }
            },
            buffer -> {
                int regionCount = buffer.readVarInt();
                Map<UUID, Region> regionsById = new HashMap<>(regionCount);
                for (int i = 0; i < regionCount; i++) {
                    UUID id = buffer.readUUID();
                    String name = buffer.readUtf();
                    String buyerAccess = buffer.readUtf();
                    int propCount = buffer.readVarInt();
                    Map<String, String> properties = new HashMap<>(propCount);
                    for (int j = 0; j < propCount; j++) {
                        properties.put(buffer.readUtf(), buffer.readUtf());
                    }
                    // isDefault/allowPrivateSelling aren't needed client-side here
                    // (defaultRegionByTeam covers the "is this the default"
                    // question, and selling permission isn't shown in the hover
                    // panel) - false is a safe placeholder for both. Same for
                    // the force-load mode, which is team-private.
                    regionsById.put(id, new Region(id, name, false, properties, false, buyerAccess,
                            dev.malik.lcftbhook.data.ForceLoadMode.DEFAULT));
                }
                int teamCount = buffer.readVarInt();
                Map<UUID, UUID> defaultRegionByTeam = new HashMap<>(teamCount);
                for (int i = 0; i < teamCount; i++) {
                    UUID teamId = buffer.readUUID();
                    defaultRegionByTeam.put(teamId, buffer.readUUID());
                }
                return new SyncPublicRegionsPayload(regionsById, defaultRegionByTeam);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncPublicRegionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPublicRegions.update(payload.regionsById, payload.defaultRegionByTeam));
    }
}
