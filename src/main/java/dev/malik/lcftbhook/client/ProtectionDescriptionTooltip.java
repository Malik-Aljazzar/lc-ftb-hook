package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared "what does this protection do, and what does it cost" tooltip -
 * name, a plain-language description, the Public/Allies/Team/Private legend
 * for access fields, and (only where it's true, see {@code showPrice}) the
 * price. Used by every screen that edits a protection.
 */
public final class ProtectionDescriptionTooltip {
    private ProtectionDescriptionTooltip() {
    }

    /**
     * @param showPrice false for the private chunk override screen - loosening
     *                  one's own chunk is free, upkeep only bills the region's
     *                  own value, so a price there would be misleading
     */
    public static List<Component> lines(ProtectionProperty property, boolean showPrice) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("message.lc_ftb_hook.upkeep_priority.protection." + property.id())
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        lines.add(Component.translatable("gui.lc_ftb_hook.protection_description." + property.id())
                .withStyle(ChatFormatting.GRAY));
        if (property.isPrivacyMode()) {
            lines.add(Component.translatable("gui.lc_ftb_hook.protection_privacy_tier_legend")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (!showPrice) {
            lines.add(Component.translatable("gui.lc_ftb_hook.protection_override_no_extra_cost")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return lines;
        }
        Long price = ClientClaimPrices.protectionPrice(property.id());
        Component priceValue = price != null && price > 0
                ? ProtectionPriceDisplay.formatPricePerChunk(price).copy()
                        .append(Component.translatable("gui.lc_ftb_hook.protection_price_per_chunk_suffix"))
                        .append(Component.literal(" / "))
                        .append(ProtectionPriceDisplay.upkeepPeriodLabel())
                : Component.translatable("gui.lc_ftb_hook.price_free");
        lines.add(priceValue.copy().withStyle(ChatFormatting.GOLD));
        return lines;
    }
}
