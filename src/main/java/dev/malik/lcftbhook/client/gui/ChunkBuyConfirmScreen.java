package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.network.BuyChunksPayload;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/** Price/cancel/confirm dialog for buying one or more listed chunks ("Buy" or "Buy as Country"). */
public class ChunkBuyConfirmScreen extends LcScreen {
    private static final int COLOR_WARNING = 0xEBCB8B;

    private final List<String> chunkKeys;
    private final boolean asCountry;

    public ChunkBuyConfirmScreen(@Nullable Screen parent, List<String> chunkKeys, boolean asCountry) {
        super(Component.translatable("gui.lc_ftb_hook.marketplace.buy_title", chunkKeys.size()), parent);
        this.chunkKeys = chunkKeys;
        this.asCountry = asCountry;
    }

    @Override
    protected int preferredWidth(int screenWidth) {
        return 220;
    }

    @Override
    protected int preferredHeight(int screenHeight) {
        return 84;
    }

    @Override
    protected void initContent() {
        button(Component.translatable("gui.lc_ftb_hook.marketplace.buy"), left + boxWidth - 100, top + boxHeight - 26, 92, b -> {
            PacketDistributor.sendToServer(new BuyChunksPayload(chunkKeys, asCountry));
            onClose();
        });
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        Component total = Component.translatable("gui.lc_ftb_hook.marketplace.buy_total", Money.format(totalCopper()));
        graphics.drawString(font, total, left + PAD, top + HEADER_HEIGHT + 10, COLOR_TEXT_DIM);
        if (asCountry) {
            graphics.drawString(font, Component.translatable("gui.lc_ftb_hook.marketplace.buying_as_country"),
                    left + PAD, top + HEADER_HEIGHT + 22, COLOR_WARNING);
        }
    }

    private long totalCopper() {
        long total = 0L;
        for (String key : chunkKeys) {
            Long price = ClientChunkOwnership.get(key).listingPricePerChunk();
            if (price != null) {
                total += price;
            }
        }
        return total;
    }
}
