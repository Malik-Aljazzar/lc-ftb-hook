package dev.malik.lcftbhook.adapter.ftb;

import dev.ftb.mods.ftbchunks.api.ClaimResult;
import net.minecraft.network.chat.MutableComponent;

/** An FTB Chunks claim result carrying one of this mod's own (already formatted) denial messages. */
public record FtbMessageClaimResult(String resultId, MutableComponent message) implements ClaimResult {
    @Override
    public String getResultId() {
        return resultId;
    }

    @Override
    public MutableComponent getMessage() {
        return message;
    }
}
