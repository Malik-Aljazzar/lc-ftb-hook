package dev.malik.lcftbhook.client.xaero;

import dev.malik.lcftbhook.client.ProtectionInfoLines;
import dev.malik.lcftbhook.client.ProtectionInfoPanelRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Fixed-position info panel on the left side of the World Map, shown while
 * hovering a claimed chunk - replaces ftbxaerocompat's plain bottom-center
 * tooltip (suppressed in {@link ClaimsHighlighterTooltipMixin}) with owner
 * (+ skin head for private owners), region, for-sale status/price, and every
 * protection property.
 * <p>
 * Plain (non-mixin) class - see {@code XaeroMarketplaceMenu}'s javadoc for
 * why {@code GuiMapClaimInfoPanelMixin} only calls into this via reflection
 * instead of containing this logic itself.
 */
public final class XaeroClaimInfoPanel {
    static final int PANEL_X = 8;
    static final int PANEL_Y = 60;
    private static final int MIN_PANEL_WIDTH = 170;

    private XaeroClaimInfoPanel() {
    }

    public static void render(GuiGraphics graphics, int mouseBlockPosX, int mouseBlockPosZ) {
        // Always-visible money panel (claim price/balance/force-load
        // upkeep), with the hover panel stacked directly below it.
        var moneyContent = new ProtectionInfoLines.Content(dev.malik.lcftbhook.client.ClientClaimPrices.moneyLines(false), null);
        ResourceKey<Level> dimension = XaeroMapDimension.current();
        ProtectionInfoLines.Content content = dimension == null
                ? ProtectionInfoLines.Content.EMPTY
                : ProtectionInfoLines.build(dimension, mouseBlockPosX >> 4, mouseBlockPosZ >> 4);
        int width = ProtectionInfoPanelRenderer.width(MIN_PANEL_WIDTH, graphics.guiWidth() / 2 - PANEL_X, moneyContent, content);

        int moneyHeight = ProtectionInfoPanelRenderer.height(moneyContent);
        int hoverPanelY = PANEL_Y;
        if (moneyHeight > 0) {
            ProtectionInfoPanelRenderer.draw(graphics, moneyContent, PANEL_X, PANEL_Y, width);
            hoverPanelY = PANEL_Y + moneyHeight + 4;
        }
        ProtectionInfoPanelRenderer.draw(graphics, content, PANEL_X, hoverPanelY, width);
    }
}
