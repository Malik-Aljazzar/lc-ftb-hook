package dev.malik.lcftbhook.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Scrollable vertical list of rows, drawn and hit-tested by callbacks - rows
 * are read from a supplier every frame, so synced data appears live. Click
 * handlers get the position inside the row, so a row can have several click
 * zones (e.g. a delete "x" on the right). Optional drag-to-reorder through a
 * grip on the row's left edge.
 */
public class RowListWidget<T> extends AbstractWidget {
    private static final int GAP = 2;
    private static final int SCROLLBAR_WIDTH = 4;

    public interface Renderer<T> {
        void render(GuiGraphics graphics, T row, int x, int y, int width, int height, boolean hovered);
    }

    public interface ClickHandler<T> {
        void onClick(T row, int localX, int localY);
    }

    public interface TooltipProvider<T> {
        List<Component> tooltip(T row, int localX, int localY);
    }

    /** Drag reordering: the rows are the current order, {@code moveTo} gets the dragged row's new index. */
    public interface Reorder<T> {
        void begin();

        void moveTo(T row, int newIndex);

        void end();
    }

    private final Supplier<List<T>> rows;
    private final ToIntFunction<T> rowHeight;
    private final Renderer<T> renderer;
    @Nullable
    private ClickHandler<T> clickHandler;
    @Nullable
    private TooltipProvider<T> tooltipProvider;
    @Nullable
    private Reorder<T> reorder;
    private int gripWidth;
    private double scroll;
    @Nullable
    private T dragging;
    private int dragStartIndex;
    private double dragStartMouseY;

    public RowListWidget(int x, int y, int width, int height, Supplier<List<T>> rows, ToIntFunction<T> rowHeight, Renderer<T> renderer) {
        super(x, y, width, height, Component.empty());
        this.rows = rows;
        this.rowHeight = rowHeight;
        this.renderer = renderer;
    }

    public RowListWidget<T> onClick(ClickHandler<T> handler) {
        this.clickHandler = handler;
        return this;
    }

    public RowListWidget<T> tooltip(TooltipProvider<T> provider) {
        this.tooltipProvider = provider;
        return this;
    }

    public RowListWidget<T> reorderable(int gripWidth, Reorder<T> reorder) {
        this.gripWidth = gripWidth;
        this.reorder = reorder;
        return this;
    }

    /** Usable row width (list width minus the scrollbar). */
    public int rowWidth() {
        return width - SCROLLBAR_WIDTH - 2;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clampScroll();
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        int y = getY() - (int) scroll;
        for (T row : rows.get()) {
            int h = rowHeight.applyAsInt(row);
            if (y + h >= getY() && y <= getY() + height) {
                boolean hovered = isHovered() && mouseY >= y && mouseY < y + h && mouseX < getX() + rowWidth();
                renderer.render(graphics, row, getX(), y, rowWidth(), h, hovered || row == dragging);
            }
            y += h + GAP;
        }
        graphics.disableScissor();
        renderScrollbar(graphics);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int content = contentHeight();
        if (content <= height) {
            return;
        }
        int barX = getX() + width - SCROLLBAR_WIDTH;
        int thumbHeight = Math.max(12, height * height / content);
        int thumbY = getY() + (int) ((height - thumbHeight) * (scroll / (content - height)));
        graphics.fill(barX, getY(), barX + SCROLLBAR_WIDTH, getY() + height, 0x60000000);
        graphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xC0D8DEE9);
    }

    private int contentHeight() {
        int total = 0;
        for (T row : rows.get()) {
            total += rowHeight.applyAsInt(row) + GAP;
        }
        return total;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - height)));
    }

    /** The row under the mouse and the mouse position inside it, or null. */
    @Nullable
    private Hit<T> hitAt(double mouseX, double mouseY) {
        if (mouseX < getX() || mouseX >= getX() + rowWidth() || mouseY < getY() || mouseY >= getY() + height) {
            return null;
        }
        int y = getY() - (int) scroll;
        List<T> current = rows.get();
        for (int i = 0; i < current.size(); i++) {
            int h = rowHeight.applyAsInt(current.get(i));
            if (mouseY >= y && mouseY < y + h) {
                return new Hit<>(current.get(i), i, (int) mouseX - getX(), (int) mouseY - y);
            }
            y += h + GAP;
        }
        return null;
    }

    private record Hit<T>(T row, int index, int localX, int localY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Hit<T> hit = button == 0 ? hitAt(mouseX, mouseY) : null;
        if (hit == null) {
            return false;
        }
        if (reorder != null && hit.localX < gripWidth) {
            dragging = hit.row;
            dragStartIndex = hit.index;
            dragStartMouseY = mouseY;
            reorder.begin();
        } else if (clickHandler != null) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            clickHandler.onClick(hit.row, hit.localX, hit.localY);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging == null || reorder == null) {
            return false;
        }
        int step = rowHeight.applyAsInt(dragging) + GAP;
        int delta = (int) Math.round((mouseY - dragStartMouseY) / step);
        reorder.moveTo(dragging, Math.max(0, Math.min(rows.get().size() - 1, dragStartIndex + delta)));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null && reorder != null) {
            dragging = null;
            reorder.end();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll -= scrollY * 12;
        clampScroll();
        return true;
    }

    /** Tooltip for the row under the mouse, empty if none. */
    public List<Component> tooltipAt(int mouseX, int mouseY) {
        Hit<T> hit = tooltipProvider != null && dragging == null ? hitAt(mouseX, mouseY) : null;
        return hit == null ? List.of() : tooltipProvider.tooltip(hit.row, hit.localX, hit.localY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
