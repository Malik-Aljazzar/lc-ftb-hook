package dev.malik.lcftbhook.network;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.client.ClientUpkeepSummary;
import dev.malik.lcftbhook.service.UpkeepSummaryService.UpkeepSummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Team-wide upkeep totals (protection, force-load, war) for the Region list
 * screen's header - see {@link dev.malik.lcftbhook.service.UpkeepSummaryService}.
 */
public record SyncUpkeepSummaryPayload(UpkeepSummary summary) implements CustomPacketPayload {
    public static final Type<SyncUpkeepSummaryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCFtbHook.MOD_ID, "sync_upkeep_summary"));
    public static final StreamCodec<FriendlyByteBuf, SyncUpkeepSummaryPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                UpkeepSummary s = payload.summary;
                buffer.writeVarLong(s.protectionCurrentCopper());
                buffer.writeVarLong(s.protectionPendingCopper());
                buffer.writeVarLong(s.forceLoadCurrentCopper());
                buffer.writeVarLong(s.forceLoadPendingCopper());
                buffer.writeVarInt(s.forceLoadCurrentCount());
                buffer.writeVarInt(s.forceLoadPendingCount());
                buffer.writeVarLong(s.warIncomingCurrentCopper());
                buffer.writeVarLong(s.warIncomingPendingCopper());
                buffer.writeVarLong(s.warOutgoingCurrentCopper());
                buffer.writeVarLong(s.warOutgoingPendingCopper());
                buffer.writeVarInt(s.warIncomingCount());
                buffer.writeVarInt(s.warOutgoingCount());
                buffer.writeVarLong(s.protectionDiscountCurrentCopper());
                buffer.writeVarLong(s.protectionDiscountPendingCopper());
            },
            buffer -> new SyncUpkeepSummaryPayload(new UpkeepSummary(
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarLong(),
                    buffer.readVarLong()
            ))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncUpkeepSummaryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientUpkeepSummary.update(payload.summary));
    }
}
