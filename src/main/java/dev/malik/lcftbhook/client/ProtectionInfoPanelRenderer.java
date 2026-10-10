package dev.malik.lcftbhook.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.UUID;

/** Draws a fixed-position dark info panel (owner/region/sale/protection lines, plus a skin head for private owners). */
public final class ProtectionInfoPanelRenderer {
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int HEAD_SIZE = 16;

    private ProtectionInfoPanelRenderer() {
    }

    public static int height(ProtectionInfoLines.Content content) {
        if (content.lines().isEmpty()) {
            return 0;
        }
        boolean hasHead = content.skinOwner() != null;
        int contentHeight = Math.max(hasHead ? HEAD_SIZE : 0, content.lines().size() * LINE_HEIGHT);
        return PADDING * 2 + contentHeight;
    }

    /**
     * Width that fits every line of all given panels (at least {@code minWidth},
     * at most {@code maxWidth}) - stacked panels share it so their edges line
     * up, and it grows for long lines such as multi-coin amounts or OPAC's
     * longer protection names instead of letting them spill over the edge.
     */
    public static int width(int minWidth, int maxWidth, ProtectionInfoLines.Content... contents) {
        Font font = Minecraft.getInstance().font;
        int widest = minWidth;
        for (ProtectionInfoLines.Content content : contents) {
            int headOffset = content.skinOwner() != null ? HEAD_SIZE + 6 : 0;
            for (Component line : content.lines()) {
                widest = Math.max(widest, PADDING * 2 + headOffset + font.width(line));
            }
        }
        return Math.min(widest, Math.max(minWidth, maxWidth));
    }

    public static void draw(GuiGraphics graphics, ProtectionInfoLines.Content content, int panelX, int panelY, int panelWidth) {
        if (content.lines().isEmpty()) {
            return;
        }
        PlayerSkin skin = content.skinOwner() != null ? resolveSkin(content.skinOwner()) : null;

        Font font = Minecraft.getInstance().font;
        int textX = panelX + PADDING + (skin != null ? HEAD_SIZE + 6 : 0);
        int contentHeight = Math.max(skin != null ? HEAD_SIZE : 0, content.lines().size() * LINE_HEIGHT);
        int panelHeight = PADDING * 2 + contentHeight;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xB0000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0x60FFFFFF);

        if (skin != null) {
            PlayerFaceRenderer.draw(graphics, skin, panelX + PADDING, panelY + PADDING, HEAD_SIZE);
        }

        int y = panelY + PADDING;
        for (Component line : content.lines()) {
            graphics.drawString(font, line, textX, y, 0xFFFFFF);
            y += LINE_HEIGHT;
        }
    }

    @Nullable
    private static PlayerSkin resolveSkin(UUID playerId) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            PlayerInfo info = connection.getPlayerInfo(playerId);
            if (info != null) {
                return info.getSkin();
            }
        }
        // Same offline/never-seen-this-session fallback as
        // ProtectionInfoLines.resolveName - once a profile with a real
        // texture property has arrived via ClientPlayerProfiles, feed it
        // through the client's own SkinManager exactly like vanilla does
        // for tab-list players. getInsecureSkin is synchronous/non-blocking:
        // it returns the cached skin once loaded, or a deterministic
        // Steve/Alex default in the meantime (never null).
        var resolved = ClientPlayerProfiles.get(playerId);
        return resolved != null ? Minecraft.getInstance().getSkinManager().getInsecureSkin(resolved) : null;
    }
}
