package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.data.PlayerAccessList;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.network.SetChunkAccessListPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-protection whitelist/blacklist of one privately owned chunk. Every
 * change (mode, add, remove) is sent right away - no separate save step.
 */
public class PlayerAccessListScreen extends LcScreen {
    private static final int ROW_HEIGHT = 16;
    private static final int REMOVE_WIDTH = 14;

    private final String chunkKey;
    private final ProtectionProperty property;
    private final Set<UUID> players = new LinkedHashSet<>();
    private boolean whitelist;
    private Button modeButton;

    public PlayerAccessListScreen(@Nullable Screen parent, String chunkKey, ProtectionProperty property) {
        super(Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + property.id()), parent);
        this.chunkKey = chunkKey;
        this.property = property;
        PlayerAccessList current = ClientChunkOwnership.get(chunkKey).accessLists().getOrDefault(property.id(), PlayerAccessList.EMPTY);
        this.whitelist = current.whitelist();
        this.players.addAll(current.players());
    }

    @Override
    protected int preferredWidth(int screenWidth) {
        return 220;
    }

    @Override
    protected int preferredHeight(int screenHeight) {
        return 200;
    }

    @Override
    protected void initContent() {
        int y = top + HEADER_HEIGHT + 6;
        modeButton = button(modeLabel(), left + PAD, y, boxWidth - PAD * 2, b -> {
            whitelist = !whitelist;
            modeButton.setMessage(modeLabel());
            push();
        });
        y += 22;
        EditBox nameBox = addRenderableWidget(new EditBox(font, left + PAD, y, boxWidth - PAD * 2 - 56, 18, Component.empty()));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.translatable("gui.lc_ftb_hook.access_list.name_ghost"));
        button(Component.translatable("gui.lc_ftb_hook.access_list.add"), left + boxWidth - PAD - 50, y, 50, b -> {
            addPlayer(nameBox.getValue().trim());
            nameBox.setValue("");
        });
        int listTop = y + 24;
        addList(new RowListWidget<>(left + PAD, listTop, boxWidth - PAD * 2, top + boxHeight - PAD - listTop,
                () -> List.copyOf(players), id -> ROW_HEIGHT, this::renderRow)
                .onClick((id, localX, localY) -> {
                    if (localX >= boxWidth - PAD * 2 - 6 - REMOVE_WIDTH && players.remove(id)) {
                        push();
                    }
                }));
    }

    private void renderRow(GuiGraphics graphics, UUID playerId, int x, int y, int w, int h, boolean hovered) {
        graphics.fill(x, y, x + w, y + h, hovered ? COLOR_ROW_HOVER : COLOR_ROW);
        graphics.drawString(font, resolveName(playerId), x + 4, y + 4, COLOR_TEXT);
        graphics.drawString(font, Component.literal("x").withStyle(ChatFormatting.RED), x + w - REMOVE_WIDTH + 4, y + 4, COLOR_TEXT);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        if (players.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.lc_ftb_hook.access_list.empty").withStyle(ChatFormatting.GRAY),
                    left + boxWidth / 2, top + HEADER_HEIGHT + 58, COLOR_TEXT);
        }
    }

    private Component modeLabel() {
        return Component.translatable(whitelist ? "gui.lc_ftb_hook.access_list.mode_whitelist" : "gui.lc_ftb_hook.access_list.mode_blacklist");
    }

    private void addPlayer(String name) {
        if (name.isEmpty()) {
            return;
        }
        var connection = Minecraft.getInstance().getConnection();
        var info = connection != null ? connection.getPlayerInfo(name) : null;
        if (info == null) {
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("message.lc_ftb_hook.access_list.player_not_found", name));
            return;
        }
        if (players.add(info.getProfile().getId())) {
            push();
        }
    }

    private void push() {
        PacketDistributor.sendToServer(new SetChunkAccessListPayload(chunkKey, property.id(), whitelist, Set.copyOf(players)));
    }

    private static String resolveName(UUID playerId) {
        var connection = Minecraft.getInstance().getConnection();
        var info = connection != null ? connection.getPlayerInfo(playerId) : null;
        return info != null ? info.getProfile().getName() : playerId.toString().substring(0, 8);
    }
}
