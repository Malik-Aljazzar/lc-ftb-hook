package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ClientWarState;
import dev.malik.lcftbhook.network.WarEntryStatus;
import dev.malik.lcftbhook.network.WarTeamEntry;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Tooltips of the war screen: the team's war cost summary and each opponent's details. */
final class WarTooltips {
    enum Section { INCOMING, OUTGOING, DECLARE }

    private WarTooltips() {
    }

    static List<Component> costSummary() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.lc_ftb_hook.war.cost_tooltip.title").withStyle(ChatFormatting.GOLD));
        lines.add(Component.empty());
        lines.add(Component.translatable("gui.lc_ftb_hook.war.cost_tooltip.base", Money.formatPrice(ClientWarState.baseUpkeepCopper())));
        if (ClientWarState.incomingWarCopper() > 0) {
            lines.add(Component.translatable("gui.lc_ftb_hook.war.cost_tooltip.incoming", Money.formatPrice(ClientWarState.incomingWarCopper())));
        }
        if (ClientWarState.outgoingWarCopper() > 0) {
            lines.add(Component.translatable("gui.lc_ftb_hook.war.cost_tooltip.outgoing", Money.formatPrice(ClientWarState.outgoingWarCopper())));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("gui.lc_ftb_hook.war.cost_tooltip.total",
                Money.formatPrice(ClientWarState.baseUpkeepCopper() + ClientWarState.totalWarCopper()),
                ProtectionPriceDisplay.upkeepPeriodLabel()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.empty());
        double multiplier = ClientWarState.warCostMultiplier();
        String formatted = Math.rint(multiplier) == multiplier ? String.valueOf((long) multiplier) : String.valueOf(multiplier);
        lines.add(Component.translatable("gui.lc_ftb_hook.war.cost_tooltip.multiplier", formatted).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    static List<Component> entry(WarTeamEntry entry, Section section) {
        List<Component> lines = new ArrayList<>();
        boolean pendingDeclare = entry.status() == WarEntryStatus.PENDING_DECLARE;
        if (pendingDeclare && section == Section.DECLARE) {
            lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.pending_declare").withStyle(ChatFormatting.GOLD));
        } else if (pendingDeclare && section == Section.INCOMING) {
            lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.pending_incoming").withStyle(ChatFormatting.GOLD));
        } else if (entry.status() == WarEntryStatus.PENDING_END && section == Section.OUTGOING) {
            lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.pending_end").withStyle(ChatFormatting.GOLD));
        }
        costDetails(lines, entry);
        if (section == Section.DECLARE && entry.opponentPendingDeclareOnViewer()) {
            lines.add(Component.empty());
            lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.opponent_pending_declare").withStyle(ChatFormatting.YELLOW));
        }
        if (section == Section.OUTGOING) {
            vulnerabilities(lines, entry);
        }
        if (entry.isPending() && section != Section.INCOMING && ClientWarState.canManageWar()) {
            lines.add(Component.empty());
            lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.click_to_cancel").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** "Cost: X / period" for a row, or "Pending". */
    static Component costLabel(WarTeamEntry entry) {
        if (entry.isPending()) {
            return Component.translatable("gui.lc_ftb_hook.pending").withStyle(ChatFormatting.GOLD);
        }
        if (entry.warCostCopper() <= 0) {
            return Money.formatPrice(entry.warCostCopper());
        }
        return Component.translatable("gui.lc_ftb_hook.war.entry_cost", Money.formatPrice(entry.warCostCopper()),
                ProtectionPriceDisplay.upkeepPeriodLabel()).withStyle(ChatFormatting.GOLD);
    }

    private static void costDetails(List<Component> lines, WarTeamEntry entry) {
        Component period = ProtectionPriceDisplay.upkeepPeriodLabel();
        lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.base", Money.formatPrice(entry.targetBaseUpkeepCopper()), period));
        if (entry.warCostCopper() <= 0) {
            lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.no_claims_yet").withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.cost", Money.formatPrice(entry.warCostCopper()), period)
                .withStyle(ChatFormatting.GOLD));
    }

    private static void vulnerabilities(List<Component> lines, WarTeamEntry entry) {
        if (!entry.hasWarVulnerability()) {
            return;
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.vulnerabilities_heading").withStyle(ChatFormatting.GOLD));
        if (!entry.blockEditProtected()) {
            lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.missing_block_edit").withStyle(ChatFormatting.YELLOW));
        }
        if (!entry.explosionProtected()) {
            lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.missing_explosions").withStyle(ChatFormatting.YELLOW));
        }
        if (!entry.pvpProtected()) {
            lines.add(Component.translatable("gui.lc_ftb_hook.war.entry_tooltip.missing_pvp").withStyle(ChatFormatting.YELLOW));
        }
    }
}
