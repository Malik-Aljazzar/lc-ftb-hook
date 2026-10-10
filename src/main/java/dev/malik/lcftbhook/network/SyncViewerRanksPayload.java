package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientClaims;
import dev.malik.lcftbhook.data.ViewerRank;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The receiving player's own team and their standing (ally/member/officer)
 * in every team that matters to them - sent per player.
 */
public record SyncViewerRanksPayload(@Nullable UUID ownTeam, Map<UUID, ViewerRank> ranks) implements CustomPacketPayload {
    public static final Type<SyncViewerRanksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_viewer_ranks"));
    public static final StreamCodec<FriendlyByteBuf, SyncViewerRanksPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.ownTeam != null);
                if (payload.ownTeam != null) {
                    buffer.writeUUID(payload.ownTeam);
                }
                buffer.writeVarInt(payload.ranks.size());
                payload.ranks.forEach((teamId, rank) -> {
                    buffer.writeUUID(teamId);
                    buffer.writeEnum(rank);
                });
            },
            buffer -> {
                UUID ownTeam = buffer.readBoolean() ? buffer.readUUID() : null;
                int count = buffer.readVarInt();
                Map<UUID, ViewerRank> ranks = new HashMap<>(count);
                for (int i = 0; i < count; i++) {
                    ranks.put(buffer.readUUID(), buffer.readEnum(ViewerRank.class));
                }
                return new SyncViewerRanksPayload(ownTeam, ranks);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncViewerRanksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientClaims.updateViewer(payload.ownTeam, payload.ranks));
    }
}
