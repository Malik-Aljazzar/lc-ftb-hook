package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.network.ExpropriateChunksPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Compensation entry for "Expropriate...". Unlike a sale price, a
 * compensation of 0 is a valid (if unfriendly) choice, so no minimum applies.
 */
public class ExpropriatePriceScreen extends PriceEntryScreen {
    public ExpropriatePriceScreen(@Nullable Runnable onBack, List<String> chunkKeys) {
        super(
                Component.translatable("gui.lc_ftb_hook.marketplace.expropriate_title", chunkKeys.size()),
                Component.translatable("gui.lc_ftb_hook.marketplace.expropriate_confirm"),
                false,
                onBack,
                compensation -> PacketDistributor.sendToServer(new ExpropriateChunksPayload(chunkKeys, compensation))
        );
    }
}
