package dev.malik.lcftbhook.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Base for this mod's own screens - plain vanilla widgets, no UI library, so
 * they work with any claim mod. A centered panel with a title bar and a back
 * button that returns to {@code parent}; subclasses lay out their widgets in
 * {@link #initContent()} relative to {@link #left}/{@link #top}.
 * <p>
 * List contents are read live every frame (suppliers), so synced data shows
 * up without rebuilding; {@link #onDataChanged()} is only for structural
 * changes.
 */
public abstract class LcScreen extends Screen {
    protected static final int HEADER_HEIGHT = 22;
    protected static final int PAD = 8;
    protected static final int COLOR_BORDER = 0xFF4C566A;
    protected static final int COLOR_PANEL = 0xF02E3440;
    protected static final int COLOR_ROW = 0xB43B4252;
    protected static final int COLOR_ROW_HOVER = 0xDC434C5E;
    protected static final int COLOR_TEXT = 0xECEFF4;
    protected static final int COLOR_TEXT_DIM = 0xD8DEE9;
    protected static final int COLOR_MUTED = 0x8F99AB;
    protected static final int COLOR_GREEN = 0xA3BE8C;
    protected static final int COLOR_RED = 0xBF616A;

    @Nullable
    protected final Screen parent;
    protected int left;
    protected int top;
    protected int boxWidth;
    protected int boxHeight;
    private final List<HoverArea> hoverAreas = new ArrayList<>();
    private final List<RowListWidget<?>> lists = new ArrayList<>();

    private record HoverArea(int x, int y, int w, int h, Supplier<List<Component>> lines) {
    }

    protected LcScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract int preferredWidth(int screenWidth);

    protected abstract int preferredHeight(int screenHeight);

    protected abstract void initContent();

    /** Extra drawing on top of the panel and widgets (text lines etc.). */
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /** Called when synced data changed; default keeps widgets, lists are live anyway. */
    public void onDataChanged() {
    }

    @Override
    protected final void init() {
        boxWidth = Math.min(width - 20, preferredWidth(width));
        boxHeight = Math.min(height - 20, preferredHeight(height));
        left = (width - boxWidth) / 2;
        top = (height - boxHeight) / 2;
        hoverAreas.clear();
        lists.clear();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> onClose())
                .bounds(left + 4, top + 3, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.back")))
                .build());
        initContent();
    }

    /** Rebuilds all widgets, e.g. when the set of controls depends on synced data. */
    protected void rebuild() {
        rebuildWidgets();
    }

    protected Button button(Component label, int x, int y, int w, Button.OnPress onPress) {
        return addRenderableWidget(Button.builder(label, onPress).bounds(x, y, w, 18).build());
    }

    /** A plain screen area that shows {@code lines} as tooltip while hovered. */
    protected void hoverArea(int x, int y, int w, int h, Supplier<List<Component>> lines) {
        hoverAreas.add(new HoverArea(x, y, w, h, lines));
    }

    protected <T> RowListWidget<T> addList(RowListWidget<T> list) {
        lists.add(list);
        return addRenderableWidget(list);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + boxWidth, top + boxHeight, COLOR_BORDER);
        graphics.fill(left + 1, top + 1, left + boxWidth - 1, top + boxHeight - 1, COLOR_PANEL);
        graphics.fill(left + 1, top + HEADER_HEIGHT, left + boxWidth - 1, top + HEADER_HEIGHT + 1, COLOR_BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left + boxWidth / 2, top + 7, COLOR_TEXT);
        renderContent(graphics, mouseX, mouseY);
        List<Component> tooltip = tooltipAt(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        for (RowListWidget<?> list : lists) {
            List<Component> lines = list.tooltipAt(mouseX, mouseY);
            if (!lines.isEmpty()) {
                return lines;
            }
        }
        for (HoverArea area : hoverAreas) {
            if (mouseX >= area.x && mouseX < area.x + area.w && mouseY >= area.y && mouseY < area.y + area.h) {
                return area.lines.get();
            }
        }
        return List.of();
    }
}
