package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ProtectionValueFormat;
import dev.malik.lcftbhook.data.PrivacyLevel;
import dev.malik.lcftbhook.data.ProtectionProperty;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared drawing/cycling for the protection rows of Region Settings and the private chunk screen. */
final class ProtectionRows {
    static final int ROW_HEIGHT = 18;
    static final int PENDING_ROW_HEIGHT = 28;

    /** Whether the advanced protections are expanded - remembered across screens for the session. */
    static boolean showAdvanced;

    /**
     * @param shown     main protections plus advanced ones that are set
     * @param collapsed advanced protections that are not set - behind the "Advanced" row
     */
    record Layout(List<ProtectionProperty> shown, List<ProtectionProperty> collapsed) {
    }

    private ProtectionRows() {
    }

    static Layout layout(java.util.function.Predicate<ProtectionProperty> isSet) {
        List<ProtectionProperty> primary = new java.util.ArrayList<>();
        List<ProtectionProperty> advancedSet = new java.util.ArrayList<>();
        List<ProtectionProperty> collapsed = new java.util.ArrayList<>();
        for (ProtectionProperty property : ProtectionProperty.values()) {
            (property.isPrimary() ? primary : isSet.test(property) ? advancedSet : collapsed).add(property);
        }
        primary.addAll(advancedSet);
        return new Layout(primary, collapsed);
    }

    /** The "Advanced protections (N)" expander row. */
    static void drawAdvancedToggle(GuiGraphics graphics, Font font, int x, int y, int w, int h, boolean hovered, int count) {
        graphics.fill(x, y, x + w, y + h, hovered ? LcScreen.COLOR_ROW_HOVER : 0x802E3440);
        Component label = Component.literal(showAdvanced ? "▾ " : "▸ ")
                .append(Component.translatable("gui.lc_ftb_hook.protection_advanced", count))
                .withStyle(ChatFormatting.GRAY);
        graphics.drawString(font, label, x + 6, y + 5, LcScreen.COLOR_TEXT);
    }

    /** Access fields cycle Public -> Allies -> Team -> Private -> Public, toggles flip. */
    static String nextValue(ProtectionProperty property, String current) {
        if (property.isPrivacyMode()) {
            PrivacyLevel[] values = PrivacyLevel.values();
            return values[(PrivacyLevel.valueOf(current).ordinal() + 1) % values.length].name();
        }
        return "true".equals(current) ? "false" : "true";
    }

    static Component label(ProtectionProperty property) {
        return Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + property.id());
    }

    /** Value text, green while protected, red while unprotected. */
    static Component value(ProtectionProperty property, String serialized) {
        boolean protectedValue = !property.isAtMinimum(serialized);
        return ProtectionValueFormat.formatValue(serialized, property).copy()
                .withStyle(protectedValue ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    static void background(GuiGraphics graphics, int x, int y, int w, int h, boolean hovered) {
        graphics.fill(x, y, x + w, y + h, hovered ? LcScreen.COLOR_ROW_HOVER : LcScreen.COLOR_ROW);
    }

    /** Label on the left, value right-aligned (leaving {@code rightInset} free for a button). */
    static void labelAndValue(GuiGraphics graphics, Font font, Component label, Component value,
                              int x, int y, int w, int rightInset) {
        int valueWidth = font.width(value);
        String labelText = font.plainSubstrByWidth(label.getString(), Math.max(0, w - valueWidth - rightInset - 12));
        graphics.drawString(font, Component.literal(labelText).withStyle(label.getStyle()), x + 6, y + 5, LcScreen.COLOR_TEXT);
        graphics.drawString(font, value, x + w - rightInset - valueWidth, y + 5, LcScreen.COLOR_TEXT);
    }

    /** "Sable Sublevels - Coming soon": placeholder for a protection that isn't implemented yet. */
    static void drawComingSoon(GuiGraphics graphics, Font font, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, LcScreen.COLOR_ROW);
        Component label = Component.translatable("gui.lc_ftb_hook.regions.sable_sublevels").withStyle(ChatFormatting.GRAY);
        Component value = Component.translatable("gui.lc_ftb_hook.protection_coming_soon").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        labelAndValue(graphics, font, label, value, x, y, w, 6);
    }

    static List<Component> comingSoonTooltip() {
        return List.of(Component.translatable("gui.lc_ftb_hook.protection_coming_soon_tooltip")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
