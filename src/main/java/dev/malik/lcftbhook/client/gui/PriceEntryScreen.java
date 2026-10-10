package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyPort.Denomination;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Plain vanilla price entry: one row per coin of the active currency backend
 * (Lightman's Currency or Numismatics - see {@code CurrencyPort.denominations()})
 * with -/+ buttons (Shift = x10) that keep the amount in the fewest coins,
 * and a live total. Replaces the old screens
 * built on Lightman's Currency's own coin-input widget, which only existed
 * with LC installed.
 */
public class PriceEntryScreen extends Screen {
    private static final int WIDTH = 220;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 44;
    private static final int BORDER_COLOR = 0xFF4C566A;
    private static final int CONTENT_COLOR = 0xF02E3440;

    @Nullable
    private final Runnable onBack;
    private final Component confirmLabel;
    private final boolean requirePositive;
    private final LongConsumer onConfirm;
    private List<Denomination> denominations = List.of();
    private long[] counts = new long[0];
    private int left;
    private int top;

    public PriceEntryScreen(Component title, Component confirmLabel, boolean requirePositive,
                            @Nullable Runnable onBack, LongConsumer onConfirm) {
        super(title);
        this.confirmLabel = confirmLabel;
        this.requirePositive = requirePositive;
        this.onBack = onBack;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        if (denominations.isEmpty()) {
            denominations = Backends.currency().denominations();
            counts = new long[denominations.size()];
        }
        left = (this.width - boxWidth()) / 2;
        top = (this.height - boxHeight()) / 2;
        for (int i = 0; i < denominations.size(); i++) {
            addCoinButtons(i, top + HEADER_HEIGHT + 4 + i * ROW_HEIGHT);
        }
        int buttonY = top + boxHeight() - 22;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + 8, buttonY, 80, 16).build());
        addRenderableWidget(Button.builder(confirmLabel, b -> confirm())
                .bounds(left + boxWidth() - 88, buttonY, 80, 16).build());
    }

    private void addCoinButtons(int index, int y) {
        int right = left + boxWidth() - 8;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjust(index, 1))
                .bounds(right - 16, y, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjust(index, -1))
                .bounds(right - 76, y, 16, 16).build());
    }

    /**
     * Changes the total by the coin's value, then re-splits it into as few
     * coins as possible - 8 spurs roll over into a bevel, and "-" on an empty
     * small coin breaks a bigger one - so rows never pile up huge counts.
     */
    private void adjust(int index, int direction) {
        long step = hasShiftDown() ? 10 : 1;
        long total = Math.max(0, total() + direction * step * denominations.get(index).value());
        for (int i : byValueDescending()) {
            long value = denominations.get(i).value();
            counts[i] = value > 0 ? total / value : 0;
            total -= counts[i] * value;
        }
    }

    private List<Integer> byValueDescending() {
        return java.util.stream.IntStream.range(0, denominations.size()).boxed()
                .sorted((a, b) -> Long.compare(denominations.get(b).value(), denominations.get(a).value()))
                .toList();
    }

    private long total() {
        long total = 0;
        for (int i = 0; i < denominations.size(); i++) {
            total += counts[i] * denominations.get(i).value();
        }
        return total;
    }

    private void confirm() {
        long total = total();
        if (requirePositive && total <= 0) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("message.lc_ftb_hook.marketplace_invalid_price"), false);
            }
            return;
        }
        onConfirm.accept(total);
        onClose();
    }

    @Override
    public void onClose() {
        if (onBack != null) {
            onBack.run();
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + boxWidth(), top + boxHeight(), BORDER_COLOR);
        graphics.fill(left + 1, top + 1, left + boxWidth() - 1, top + boxHeight() - 1, CONTENT_COLOR);
        graphics.fill(left + 1, top + HEADER_HEIGHT, left + boxWidth() - 1, top + HEADER_HEIGHT + 1, BORDER_COLOR);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left + boxWidth() / 2, top + 7, 0xFFFFFF);
        int right = left + boxWidth() - 8;
        for (int i = 0; i < denominations.size(); i++) {
            int y = top + HEADER_HEIGHT + 4 + i * ROW_HEIGHT;
            graphics.drawString(font, denominations.get(i).name(), left + 10, y + 4, 0xD8DEE9);
            graphics.drawCenteredString(font, Long.toString(counts[i]), right - 38, y + 4, 0xFFFFFF);
        }
        int totalY = top + HEADER_HEIGHT + 4 + denominations.size() * ROW_HEIGHT + 4;
        Component total = Component.translatable("gui.lc_ftb_hook.price_entry.total", Money.formatOrFree(total()));
        graphics.drawString(font, total, left + 10, totalY, 0xEBCB8B);
    }

    private int boxWidth() {
        return Math.max(WIDTH, font.width(title) + 24);
    }

    private int boxHeight() {
        return HEADER_HEIGHT + 4 + denominations.size() * ROW_HEIGHT + FOOTER_HEIGHT;
    }
}
