package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.network.ListForSalePayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Price entry for listing one or more chunks for sale ("Sell" for a private
 * resale, "Sell as Country" for state-owned chunks). A price must be above 0.
 */
public class ChunkSalePriceScreen extends PriceEntryScreen {
    public ChunkSalePriceScreen(@Nullable Runnable onBack, List<String> chunkKeys, boolean asCountry) {
        super(
                Component.translatable("gui.lc_ftb_hook.marketplace.price_title", chunkKeys.size()),
                Component.translatable("gui.lc_ftb_hook.marketplace.sell_confirm"),
                true,
                onBack,
                price -> PacketDistributor.sendToServer(new ListForSalePayload(chunkKeys, price, asCountry))
        );
    }
}
