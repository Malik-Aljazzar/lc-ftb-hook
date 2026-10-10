package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ChunkActions;
import dev.malik.lcftbhook.client.ChunkActions.ChunkAction;
import dev.malik.lcftbhook.client.ClientClaims;
import dev.malik.lcftbhook.client.ClientWarState;
import dev.malik.lcftbhook.client.ProtectionInfoLines;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.ClaimedChunkInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.List;

/**
 * This mod's own entry point (keybind), independent of any claim mod's UI:
 * Regions, Wars, and everything that can be done with the chunk the player
 * stands in (region assignment, selling, buying, private settings).
 */
public class MainMenuScreen extends LcScreen {
    private static final int ACTION_ROW = 18;

    private List<Component> chunkInfo = List.of();
    private List<ChunkAction> chunkActions = List.of();
    private int infoTop;

    public MainMenuScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.lc_ftb_hook.menu.title"), parent);
    }

    @Override
    protected int preferredWidth(int screenWidth) {
        return 260;
    }

    @Override
    protected int preferredHeight(int screenHeight) {
        return screenHeight * 3 / 4;
    }

    @Override
    protected void initContent() {
        int y = top + HEADER_HEIGHT + 8;
        int half = (boxWidth - PAD * 3) / 2;
        Button regions = button(Component.translatable("gui.lc_ftb_hook.regions.title"), left + PAD, y, half,
                b -> Minecraft.getInstance().setScreen(new RegionListScreen(this)));
        regions.active = ClientClaims.isOwnOfficer();
        if (!regions.active) {
            regions.setTooltip(Tooltip.create(Component.translatable("gui.lc_ftb_hook.menu.officers_only")));
        }
        Button wars = button(Component.translatable("gui.lc_ftb_hook.war.title"), left + PAD * 2 + half, y, half,
                b -> Minecraft.getInstance().setScreen(new WarScreen(this)));
        wars.active = ClientWarState.warModuleEnabled();
        loadCurrentChunk();
        infoTop = y + 28;
        int listTop = infoTop + 14 + chunkInfo.size() * 10;
        addList(new RowListWidget<>(left + PAD, listTop, boxWidth - PAD * 2, top + boxHeight - PAD - listTop,
                () -> chunkActions, action -> ACTION_ROW,
                (graphics, action, x, rowY, w, h, hovered) -> {
                    graphics.fill(x, rowY, x + w, rowY + h, hovered ? COLOR_ROW_HOVER : COLOR_ROW);
                    graphics.drawString(font, action.label(), x + 6, rowY + 5, COLOR_TEXT);
                })
                .onClick((action, localX, localY) -> action.run().accept(this)));
    }

    /** Info and actions for the chunk the player is standing in. */
    private void loadCurrentChunk() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ChunkPos pos = player.chunkPosition();
        var dimension = player.level().dimension();
        chunkInfo = ProtectionInfoLines.build(dimension, pos.x, pos.z).lines();
        ClaimedChunkInfo claim = ClientClaims.chunk(dimension, pos.x, pos.z);
        chunkActions = claim == null ? List.of() : ChunkActions.forSelection(
                List.of(ChunkPosKey.encode(dimension.location(), pos.x, pos.z)), List.of(claim.teamId()));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.lc_ftb_hook.menu.this_chunk"), left + PAD, infoTop, COLOR_TEXT);
        if (chunkInfo.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.lc_ftb_hook.menu.unclaimed"), left + PAD, infoTop + 12, COLOR_MUTED);
            return;
        }
        int y = infoTop + 12;
        for (Component line : chunkInfo) {
            graphics.drawString(font, font.plainSubstrByWidth(line.getString(), boxWidth - PAD * 2), left + PAD, y, COLOR_TEXT_DIM);
            y += 10;
        }
    }

    @Override
    public void onDataChanged() {
        rebuild();
    }
}
