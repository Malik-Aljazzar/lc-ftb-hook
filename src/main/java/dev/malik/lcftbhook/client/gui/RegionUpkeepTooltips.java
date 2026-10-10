package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ClientClaimPrices;
import dev.malik.lcftbhook.client.ClientPendingState;
import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.client.ClientUpkeepSummary;
import dev.malik.lcftbhook.client.ProtectionValueFormat;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import dev.malik.lcftbhook.service.UpkeepSummaryService.UpkeepSummary;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "How did we get this number" upkeep breakdowns: per region (Region
 * Settings) and for the whole team (Region List) - every active or pending
 * protection with its price, the billable chunk multiplier, force-loads,
 * wars, and the totals now vs. after pending changes.
 */
final class RegionUpkeepTooltips {
    private RegionUpkeepTooltips() {
    }

    /** One region: its protections, chunk multiplier, force-loads and total. */
    static List<Component> forRegion(UUID regionId) {
        List<Component> lines = new ArrayList<>();
        Region region = ClientRegions.get(regionId);
        if (region == null) {
            return lines;
        }
        lines.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_title").withStyle(ChatFormatting.BOLD));
        boolean any = false;
        for (ProtectionProperty property : ProtectionProperty.values()) {
            MutableComponent line = propertyLine(region, property);
            if (line != null) {
                lines.add(Component.literal("- ").append(line));
                any = true;
            }
        }
        if (!any) {
            lines.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_none").withStyle(ChatFormatting.GRAY));
        }
        int billable = ClientRegions.billableChunkCount(regionId);
        int total = ClientRegions.chunkCount(regionId);
        lines.add((total != billable
                ? Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_chunks_free", billable, total)
                : Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_chunks", billable)).withStyle(ChatFormatting.GRAY));
        Component forceLoadLine = forceLoadLine(regionId);
        if (forceLoadLine != null) {
            lines.add(forceLoadLine);
        }
        long current = ClientRegions.currentUpkeepCopper(regionId) + ClientRegions.forceLoadCurrentUpkeepCopper(regionId);
        long pending = ClientRegions.pendingUpkeepCopper(regionId) + ClientRegions.forceLoadPendingUpkeepCopper(regionId);
        lines.add((pending != current
                ? Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_total_pending", Money.formatOrFree(current), Money.formatOrFree(pending))
                : Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_total", Money.formatOrFree(current))).withStyle(ChatFormatting.GOLD));
        lines.add(ProtectionPriceDisplay.nextUpkeepWithPeriodLabel().copy().withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** The whole team: every region's protections and force-loads, wars, grand total. */
    static List<Component> forTeam() {
        UpkeepSummary summary = ClientUpkeepSummary.get();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_title").withStyle(ChatFormatting.BOLD));
        boolean any = false;
        for (UUID regionId : ClientRegions.regionOrder()) {
            any |= addRegionSection(lines, regionId);
        }
        if (!any) {
            lines.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_none").withStyle(ChatFormatting.GRAY));
        }
        if (summary.warIncomingCount() > 0) {
            lines.add(breakdown("gui.lc_ftb_hook.regions.upkeep_tooltip_war_incoming", summary.warIncomingCurrentCopper(),
                    summary.warIncomingPendingCopper(), summary.warIncomingCount()));
        }
        if (summary.warOutgoingCount() > 0) {
            lines.add(breakdown("gui.lc_ftb_hook.regions.upkeep_tooltip_war_outgoing", summary.warOutgoingCurrentCopper(),
                    summary.warOutgoingPendingCopper(), summary.warOutgoingCount()));
        }
        lines.add(Component.translatable("gui.lc_ftb_hook.regions.upkeep_tooltip_grand_total").append(": ")
                .append(totalLine(teamCurrent(summary), teamPending(summary))).withStyle(ChatFormatting.GOLD));
        lines.add(ProtectionPriceDisplay.nextUpkeepWithPeriodLabel().copy().withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    static long teamCurrent(UpkeepSummary s) {
        return s.protectionCurrentCopper() + s.warIncomingCurrentCopper() + s.warOutgoingCurrentCopper() + s.forceLoadCurrentCopper();
    }

    static long teamPending(UpkeepSummary s) {
        return s.protectionPendingCopper() + s.warIncomingPendingCopper() + s.warOutgoingPendingCopper() + s.forceLoadPendingCopper();
    }

    /** "Upkeep: X" or "Upkeep: X -> Y" when pending changes alter it. */
    static Component upkeepLine(long current, long pending) {
        return (pending != current
                ? Component.translatable("gui.lc_ftb_hook.regions.upkeep_pending", Money.formatOrFree(current), Money.formatOrFree(pending))
                : Component.translatable("gui.lc_ftb_hook.regions.upkeep", Money.formatOrFree(current))).withStyle(ChatFormatting.GOLD);
    }

    private static boolean addRegionSection(List<Component> lines, UUID regionId) {
        Region region = ClientRegions.get(regionId);
        int billable = ClientRegions.billableChunkCount(regionId);
        if (region == null || billable <= 0) {
            return false;
        }
        List<Component> propertyLines = new ArrayList<>();
        long liveBase = 0L;
        long pendingBase = 0L;
        for (ProtectionProperty property : ProtectionProperty.values()) {
            MutableComponent line = propertyLine(region, property);
            if (line == null) {
                continue;
            }
            propertyLines.add(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY).append(line));
            long price = priceOf(property);
            String pendingValue = ClientPendingState.getPendingRegionProperty(regionId, property);
            liveBase += property.isAtMinimum(region.propertyValue(property)) ? 0 : price;
            pendingBase += pendingValue != null ? (property.isAtMinimum(pendingValue) ? 0 : price)
                    : (property.isAtMinimum(region.propertyValue(property)) ? 0 : price);
        }
        Component forceLoadLine = forceLoadLine(regionId);
        if (propertyLines.isEmpty() && forceLoadLine == null) {
            return false;
        }
        lines.add(Component.literal(region.name()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        lines.addAll(propertyLines);
        if (!propertyLines.isEmpty()) {
            long live = liveBase * billable / ProtectionProperty.PRICE_UNIT_CHUNKS;
            long pending = pendingBase * billable / ProtectionProperty.PRICE_UNIT_CHUNKS;
            lines.add((live != pending
                    ? Component.translatable("message.lc_ftb_hook.upkeep_detail.build_formula_pending", billable, totalLine(live, pending))
                    : Component.translatable("message.lc_ftb_hook.upkeep_detail.build_formula",
                            Money.format(Math.floorDiv(liveBase, 10L)), billable, Money.format(live))).withStyle(ChatFormatting.GRAY));
        }
        if (forceLoadLine != null) {
            lines.add(forceLoadLine);
        }
        return true;
    }

    /**
     * "Force-load: 1 -> 2 chunk(s) x 2g5i = 2g5i -> 5g" - the count includes
     * queued force-loads, so the total after settlement adds up; null without
     * any force-load (active or queued) in the region.
     */
    @javax.annotation.Nullable
    private static Component forceLoadLine(UUID regionId) {
        int now = ClientRegions.forceLoadCount(regionId);
        int after = ClientPendingState.forceLoadCountAfterPending(regionId);
        long current = ClientRegions.forceLoadCurrentUpkeepCopper(regionId);
        long pending = ClientRegions.forceLoadPendingUpkeepCopper(regionId);
        if (now == 0 && after == 0 && current == 0 && pending == 0) {
            return null;
        }
        long perChunk = now > 0 ? current / now : (after > 0 ? pending / after : 0L);
        String count = now == after ? String.valueOf(now) : now + " -> " + after;
        return Component.translatable("gui.lc_ftb_hook.regions.force_load_tooltip_line", count, Money.formatOrFree(perChunk))
                .append(" = ").append(totalLine(current, pending))
                .withStyle(now == after && current == pending ? ChatFormatting.GRAY : ChatFormatting.GOLD);
    }

    /** "Label: value (price/chunk)" or "Label: live -> pending (price)"; null while neither is active. */
    private static MutableComponent propertyLine(Region region, ProtectionProperty property) {
        String live = region.propertyValue(property);
        String pending = ClientPendingState.getPendingRegionProperty(region.id(), property);
        boolean hasPending = pending != null && !pending.equals(live);
        if (property.isAtMinimum(live) && !(hasPending && !property.isAtMinimum(pending))) {
            return null;
        }
        long price = priceOf(property);
        Component priceText = price > 0 ? ProtectionPriceDisplay.formatPricePerChunk(price) : Component.translatable("gui.lc_ftb_hook.price_free");
        MutableComponent line = ProtectionRows.label(property).copy().append(": ").append(ProtectionValueFormat.formatValue(live, property));
        if (hasPending) {
            line.append(" -> ").append(ProtectionValueFormat.formatValue(pending, property));
        }
        return line.append(Component.literal(" (").append(priceText)
                        .append(Component.translatable("gui.lc_ftb_hook.protection_price_per_chunk_suffix")).append(")"))
                .withStyle(hasPending ? ChatFormatting.GOLD : ChatFormatting.GRAY);
    }

    private static long priceOf(ProtectionProperty property) {
        Long price = ClientClaimPrices.protectionPrice(property.id());
        return price != null ? price : 0L;
    }

    private static Component breakdown(String labelKey, long current, long pending, int count) {
        return Component.literal("- ").append(Component.translatable(labelKey, count)).append(": ")
                .append(totalLine(current, pending))
                .withStyle(current == pending ? ChatFormatting.GRAY : ChatFormatting.GOLD);
    }

    private static MutableComponent totalLine(long current, long pending) {
        return current == pending
                ? Money.formatOrFree(current).copy()
                : Component.literal("").append(Money.formatOrFree(current)).append(" -> ").append(Money.formatOrFree(pending));
    }
}
