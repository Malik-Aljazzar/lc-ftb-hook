package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientClaims;
import dev.malik.lcftbhook.data.ClaimedChunkInfo;
import dev.malik.lcftbhook.data.TeamDisplayInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every claimed chunk (owning team, force-loaded) plus each owning team's
 * name and color, broadcast to all players - the client's claim data source,
 * independent of which claim mod the server runs.
 */
public record SyncClaimMapPayload(Map<String, ClaimedChunkInfo> chunks, Map<UUID, TeamDisplayInfo> teams)
        implements CustomPacketPayload {
    public static final Type<SyncClaimMapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_claim_map"));
    public static final StreamCodec<FriendlyByteBuf, SyncClaimMapPayload> STREAM_CODEC = StreamCodec.of(
            SyncClaimMapPayload::write, SyncClaimMapPayload::read);

    private static void write(FriendlyByteBuf buffer, SyncClaimMapPayload payload) {
        buffer.writeVarInt(payload.chunks.size());
        payload.chunks.forEach((key, info) -> {
            buffer.writeUtf(key);
            buffer.writeUUID(info.teamId());
            buffer.writeBoolean(info.forceLoaded());
        });
        buffer.writeVarInt(payload.teams.size());
        payload.teams.forEach((teamId, info) -> {
            buffer.writeUUID(teamId);
            buffer.writeUtf(info.name());
            buffer.writeInt(info.color());
        });
    }

    private static SyncClaimMapPayload read(FriendlyByteBuf buffer) {
        int chunkCount = buffer.readVarInt();
        Map<String, ClaimedChunkInfo> chunks = new HashMap<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            chunks.put(buffer.readUtf(), new ClaimedChunkInfo(buffer.readUUID(), buffer.readBoolean()));
        }
        int teamCount = buffer.readVarInt();
        Map<UUID, TeamDisplayInfo> teams = new HashMap<>(teamCount);
        for (int i = 0; i < teamCount; i++) {
            teams.put(buffer.readUUID(), new TeamDisplayInfo(buffer.readUtf(), buffer.readInt()));
        }
        return new SyncClaimMapPayload(chunks, teams);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncClaimMapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientClaims.updateMap(payload.chunks, payload.teams));
    }
}
