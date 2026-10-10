package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.client.ClientUpkeepSummary;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.CreateRegionPayload;
import dev.malik.lcftbhook.network.DeleteRegionPayload;
import dev.malik.lcftbhook.network.ReorderRegionsPayload;
import dev.malik.lcftbhook.network.RequestClaimPricesPayload;
import dev.malik.lcftbhook.network.RequestRegionsPayload;
import dev.malik.lcftbhook.service.UpkeepSummaryService.UpkeepSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The team's regions, top = dismantled LAST (the one you most want to keep
 * protected). The stored order is the reverse (index 0 = dismantled first).
 * Drag a row by its grip to reorder, click it to open its settings.
 */
public class RegionListScreen extends LcScreen {
    private static final int ROW_HEIGHT = 22;
    private static final int GRIP_WIDTH = 14;
    private static final int DELETE_WIDTH = 16;

    @Nullable
    private List<UUID> dragOrder;
    private int upkeepY;

    public RegionListScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.lc_ftb_hook.regions.title"), parent);
        PacketDistributor.sendToServer(new RequestRegionsPayload());
        PacketDistributor.sendToServer(new RequestClaimPricesPayload());
    }

    @Override
    protected int preferredWidth(int screenWidth) {
        return Math.max(280, screenWidth * 3 / 5);
    }

    @Override
    protected int preferredHeight(int screenHeight) {
        return screenHeight * 3 / 4;
    }

    @Override
    protected void initContent() {
        hoverArea(left + boxWidth - 22, top + 3, 18, 16, RegionListScreen::infoTooltip);
        upkeepY = top + HEADER_HEIGHT + 8;
        hoverArea(left + PAD, upkeepY, boxWidth - PAD * 2, 10, RegionUpkeepTooltips::forTeam);
        int listTop = upkeepY + 16;
        int footerY = top + boxHeight - 26;
        addList(new RowListWidget<>(left + PAD, listTop, boxWidth - PAD * 2, footerY - 6 - listTop,
                this::visualRows, region -> ROW_HEIGHT, this::renderRow)
                .onClick(this::click)
                .reorderable(GRIP_WIDTH, new DragReorder()));
        EditBox nameBox = addRenderableWidget(new EditBox(font, left + PAD, footerY, boxWidth - PAD * 2 - 96, 18, Component.empty()));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.translatable("gui.lc_ftb_hook.regions.create_ghost"));
        button(Component.translatable("gui.lc_ftb_hook.regions.create"), left + boxWidth - PAD - 90, footerY, 90, b -> {
            String name = nameBox.getValue().trim();
            if (!name.isEmpty()) {
                PacketDistributor.sendToServer(new CreateRegionPayload(name));
                nameBox.setValue("");
            }
        });
    }

    /** Display order: dismantled last at the top; the drag preview while dragging. */
    private List<Region> visualRows() {
        List<UUID> order = dragOrder != null ? dragOrder : reversed(ClientRegions.regionOrder());
        return order.stream().map(ClientRegions::get).filter(Objects::nonNull).toList();
    }

    private void renderRow(GuiGraphics graphics, Region region, int x, int y, int w, int h, boolean hovered) {
        graphics.fill(x, y, x + w, y + h, hovered ? COLOR_ROW_HOVER : COLOR_ROW);
        graphics.drawString(font, "≡", x + 4, y + 7, hovered ? COLOR_TEXT : COLOR_MUTED);
        graphics.drawString(font, Component.literal(region.name())
                .withStyle(region.isDefault() ? ChatFormatting.GRAY : ChatFormatting.WHITE), x + GRIP_WIDTH + 4, y + 3, COLOR_TEXT);
        graphics.drawString(font, Component.translatable("gui.lc_ftb_hook.regions.chunk_count", ClientRegions.chunkCount(region.id()))
                .withStyle(ChatFormatting.GRAY), x + GRIP_WIDTH + 4, y + 12, COLOR_TEXT);
        if (!region.isDefault()) {
            graphics.drawString(font, Component.literal("x").withStyle(ChatFormatting.RED), x + w - DELETE_WIDTH + 5, y + 7, COLOR_TEXT);
        }
    }

    private void click(Region region, int localX, int localY) {
        int rowWidth = boxWidth - PAD * 2 - 6;
        if (!region.isDefault() && localX >= rowWidth - DELETE_WIDTH) {
            PacketDistributor.sendToServer(new DeleteRegionPayload(region.id()));
            return;
        }
        Minecraft.getInstance().setScreen(new RegionSettingsScreen(this, region.id()));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "?", left + boxWidth - 15, top + 7, COLOR_MUTED);
        UpkeepSummary summary = ClientUpkeepSummary.get();
        graphics.drawString(font, RegionUpkeepTooltips.upkeepLine(RegionUpkeepTooltips.teamCurrent(summary),
                RegionUpkeepTooltips.teamPending(summary)), left + PAD, upkeepY, COLOR_TEXT);
    }

    private static List<Component> infoTooltip() {
        return List.of(
                Component.translatable("gui.lc_ftb_hook.regions.info_title").withStyle(ChatFormatting.BOLD),
                Component.translatable("gui.lc_ftb_hook.regions.info_line1"),
                Component.translatable("gui.lc_ftb_hook.regions.info_line2"),
                Component.translatable("gui.lc_ftb_hook.regions.info_line3"));
    }

    private static List<UUID> reversed(List<UUID> order) {
        List<UUID> copy = new ArrayList<>(order);
        Collections.reverse(copy);
        return copy;
    }

    /** Reorders a preview while dragging; sends the new (dismantle-first) order on release. */
    private final class DragReorder implements RowListWidget.Reorder<Region> {
        @Override
        public void begin() {
            dragOrder = reversed(ClientRegions.regionOrder());
        }

        @Override
        public void moveTo(Region row, int newIndex) {
            if (dragOrder != null && dragOrder.remove(row.id())) {
                dragOrder.add(Math.min(newIndex, dragOrder.size()), row.id());
            }
        }

        @Override
        public void end() {
            if (dragOrder != null) {
                PacketDistributor.sendToServer(new ReorderRegionsPayload(reversed(dragOrder)));
                dragOrder = null;
            }
        }
    }
}
