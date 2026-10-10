package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.ProtectionDismantleOrder.DismantleStep;
import dev.malik.lcftbhook.util.UpkeepPeriodFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

public final class UpkeepMessageBuilder {
    private static final String DETAILS_COMMAND = "/" + LCFtbHook.MOD_ID + " upkeep_details";

    private UpkeepMessageBuilder() {
    }

    public static Component buildUnaffordableRestorationMessage(
            MinecraftServer server, UUID teamId, List<DismantleStep> unaffordable, List<String> unaffordableWarNames, int unaffordableForceLoadCount
    ) {
        MutableComponent msg = Component.empty();
        boolean wroteAnything = false;

        if (!unaffordable.isEmpty()) {
            msg.append(Component.literal("⌛ ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("message.lc_ftb_hook.unaffordable_restoration_header", unaffordable.size())
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
            appendProtectionList(server, teamId, msg, unaffordable, ChatFormatting.YELLOW);
            wroteAnything = true;
        }

        if (unaffordableForceLoadCount > 0) {
            if (wroteAnything) msg.append("\n");
            msg.append(Component.literal("⌛ ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("message.lc_ftb_hook.unaffordable_force_load_header", unaffordableForceLoadCount)
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
            wroteAnything = true;
        }

        if (!unaffordableWarNames.isEmpty()) {
            if (wroteAnything) msg.append("\n");
            msg.append(Component.literal("⌛ ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("message.lc_ftb_hook.unaffordable_war_header", unaffordableWarNames.size())
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
            for (String warName : unaffordableWarNames) {
                msg.append("\n");
                msg.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
                msg.append(Component.translatable("message.lc_ftb_hook.war_suspended", warName).withStyle(ChatFormatting.YELLOW));
            }
            wroteAnything = true;
        }

        if (wroteAnything) {
            msg.append("\n");
            msg.append(Component.translatable("message.lc_ftb_hook.unaffordable_restoration_hint").withStyle(ChatFormatting.GRAY));
        }
        return msg;
    }

    public static Component buildRestorationSummary(MinecraftServer server, UUID teamId, List<DismantleStep> restored, List<String> restoredWarNames) {
        MutableComponent msg = Component.empty();
        boolean wroteAnything = false;

        if (!restored.isEmpty()) {
            msg.append(Component.translatable("message.lc_ftb_hook.restoration_header", restored.size())
                    .withStyle(ChatFormatting.GREEN));
            appendProtectionList(server, teamId, msg, restored, ChatFormatting.WHITE);
            wroteAnything = true;
        }

        for (String warName : restoredWarNames) {
            if (wroteAnything) msg.append("\n");
            msg.append(Component.translatable("message.lc_ftb_hook.war_active", warName)
                    .withStyle(ChatFormatting.GRAY));
            wroteAnything = true;
        }

        return msg;
    }

    public static Component buildSuspensionSummary(MinecraftServer server, UUID teamId, List<DismantleStep> suspended, List<String> suspendedWarNames) {
        MutableComponent msg = Component.empty();
        boolean wroteAnything = false;

        if (!suspended.isEmpty()) {
            msg.append(Component.translatable("message.lc_ftb_hook.suspension_header", suspended.size())
                    .withStyle(ChatFormatting.YELLOW));
            appendProtectionList(server, teamId, msg, suspended, ChatFormatting.WHITE);
            wroteAnything = true;
        }

        if (!suspendedWarNames.isEmpty()) {
            if (wroteAnything) msg.append("\n");
            msg.append(Component.translatable("message.lc_ftb_hook.suspension_wars_header", suspendedWarNames.size())
                    .withStyle(ChatFormatting.YELLOW));
            for (String warName : suspendedWarNames) {
                msg.append("\n");
                msg.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
                msg.append(Component.translatable("message.lc_ftb_hook.war_suspended", warName).withStyle(ChatFormatting.WHITE));
            }
            wroteAnything = true;
        }

        if (wroteAnything) {
            msg.append("\n");
            msg.append(Component.translatable("message.lc_ftb_hook.suspension_hint").withStyle(ChatFormatting.GRAY));
        }

        return msg;
    }

    private static void appendProtectionList(MinecraftServer server, UUID teamId, MutableComponent msg, List<DismantleStep> steps, ChatFormatting color) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        for (DismantleStep step : steps) {
            msg.append("\n");
            msg.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            String labelKey = "message.lc_ftb_hook.upkeep_priority.protection." + step.property().id();
            Region region = savedData.getRegion(teamId, step.regionId());
            String regionName = region != null ? region.name() : Region.DEFAULT_NAME;
            msg.append(Component.translatable(labelKey).withStyle(color)
                    .copy().append(Component.literal(" (" + regionName + ")").withStyle(ChatFormatting.GRAY)));
        }
    }

    public static Component buildSummary(UpkeepBreakdown breakdown) {
        Component amount = dev.malik.lcftbhook.util.Money.formatValue(breakdown.totalCostCopper());
        Component period = UpkeepPeriodFormat.format(breakdown.periodMinutes());

        MutableComponent message = Component.translatable("message.lc_ftb_hook.upkeep_paid", amount, period)
                .withStyle(ChatFormatting.WHITE);
        message.append(Component.literal(" "));
        message.append(buildSeeMoreButton());
        return message;
    }

    public static Component buildDetails(UpkeepBreakdown breakdown, int minutesUntilNextUpkeep) {
        MutableComponent message = Component.empty();

        message.append(Component.translatable("message.lc_ftb_hook.upkeep_detail.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        message.append("\n");

        // Period and next-payment countdown used to be two separate lines,
        // which read as pure duplication whenever you check right after a
        // settlement (countdown resets to the full period). They're still
        // two different numbers in general - just fold the period into the
        // next-payment line as context instead of giving it its own line.
        Component nextPaymentValue = styled(nextPaymentValueText(minutesUntilNextUpkeep), ChatFormatting.AQUA).copy()
                .append(Component.literal(" ("))
                .append(Component.translatable(
                        "message.lc_ftb_hook.upkeep_detail.every_period",
                        styled(UpkeepPeriodFormat.format(breakdown.periodMinutes()), ChatFormatting.AQUA)
                ))
                .append(Component.literal(")"));
        appendLine(message, "message.lc_ftb_hook.upkeep_detail.next_payment", nextPaymentValue);
        message.append("\n");

        if (breakdown.chunkCount() > 0) {
            appendLine(message, "message.lc_ftb_hook.upkeep_detail.chunks",
                    Component.literal(String.valueOf(breakdown.chunkCount())).withStyle(ChatFormatting.GREEN));
        }

        if (breakdown.forceLoadCount() > 0) {
            appendLine(message, "message.lc_ftb_hook.upkeep_detail.forceloads",
                    Component.literal(String.valueOf(breakdown.forceLoadCount())).withStyle(ChatFormatting.GREEN));
        }

        for (UpkeepBreakdown.RegionProtectionSection section : breakdown.regionSections()) {
            appendRegionSection(message, section);
        }

        appendWarSection(message, breakdown);

        appendPendingSection(message, breakdown);

        message.append("\n");
        appendLine(message, "message.lc_ftb_hook.upkeep_detail.total",
                dev.malik.lcftbhook.util.Money.formatValue(breakdown.totalCostCopper()).copy().withStyle(ChatFormatting.GREEN));

        return message;
    }

    private static void appendRegionSection(MutableComponent message, UpkeepBreakdown.RegionProtectionSection section) {
        if (section.lines().isEmpty() || section.protectionCopper() <= 0 || section.billableChunks() <= 0) {
            return;
        }

        message.append("\n");
        message.append(Component.literal(section.regionName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        message.append("\n");

        for (UpkeepBreakdown.ProtectionLine line : section.lines()) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(styled(Component.translatable(line.labelKey()), ChatFormatting.YELLOW));
            message.append(Component.literal(" +").withStyle(ChatFormatting.GRAY));
            message.append(dev.malik.lcftbhook.util.Money.formatValue(line.pricePerChunk())
                    .copy().withStyle(ChatFormatting.GOLD));
            message.append(Component.translatable("gui.lc_ftb_hook.protection_price_per_chunk_suffix")
                    .withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }

        message.append(formatFormula(
                "message.lc_ftb_hook.upkeep_detail.build_formula",
                // basePrice is the raw config sum, denominated in tenths of a
                // copper per chunk (see LCFtbHookConfig's [general] note) -
                // divided here so the displayed rate is the real per-chunk
                // price, not 10x too large.
                dev.malik.lcftbhook.util.Money.formatValue(Math.floorDiv(section.basePrice(), 10L)),
                section.billableChunks(),
                dev.malik.lcftbhook.util.Money.formatValue(section.protectionCopper())
        ));
        message.append("\n");
    }

    private static void appendWarSection(MutableComponent message, UpkeepBreakdown breakdown) {
        if (breakdown.totalWarCopper() <= 0) {
            return;
        }

        message.append("\n");
        message.append(Component.translatable("message.lc_ftb_hook.upkeep_detail.war_heading").withStyle(ChatFormatting.YELLOW));
        message.append("\n");

        for (UpkeepBreakdown.WarLine line : breakdown.warLines()) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(Component.literal(line.displayName()).withStyle(ChatFormatting.YELLOW));
            message.append(Component.literal(" — ").withStyle(ChatFormatting.GRAY));
            message.append(Component.translatable(
                    line.incoming()
                            ? "message.lc_ftb_hook.upkeep_detail.war_incoming_line"
                            : "message.lc_ftb_hook.upkeep_detail.war_outgoing_line",
                    dev.malik.lcftbhook.util.Money.formatValue(line.warCostCopper())
            ).withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }

        if (breakdown.incomingWarCopper() > 0) {
            int k = breakdown.incomingWarCount();
            double l = WarUpkeepMath.warExponent();
            message.append(Component.translatable(
                    "message.lc_ftb_hook.upkeep_detail.war_incoming_formula",
                    k,
                    trimExponent(l),
                    dev.malik.lcftbhook.util.Money.formatValue(breakdown.baseUpkeepCopper()),
                    WarUpkeepMath.formatExtraTermSum(k, l),
                    dev.malik.lcftbhook.util.Money.formatValue(breakdown.incomingWarCopper())
            ).withStyle(ChatFormatting.GRAY));
            message.append("\n");
        }
        if (breakdown.outgoingWarCopper() > 0) {
            message.append(Component.translatable(
                    "message.lc_ftb_hook.upkeep_detail.war_outgoing_total",
                    dev.malik.lcftbhook.util.Money.formatValue(breakdown.outgoingWarCopper())
            ).withStyle(ChatFormatting.GRAY));
            message.append("\n");
        }

        long expectedTotal = breakdown.baseUpkeepCopper() + breakdown.totalWarCopper();
        if (breakdown.baseUpkeepCopper() > 0 || breakdown.totalWarCopper() > 0) {
            message.append(Component.translatable(
                    "message.lc_ftb_hook.upkeep_detail.war_total_formula",
                    dev.malik.lcftbhook.util.Money.formatValue(breakdown.baseUpkeepCopper()),
                    dev.malik.lcftbhook.util.Money.formatValue(breakdown.incomingWarCopper()),
                    dev.malik.lcftbhook.util.Money.formatValue(breakdown.outgoingWarCopper()),
                    dev.malik.lcftbhook.util.Money.formatValue(expectedTotal)
            ).withStyle(ChatFormatting.DARK_GRAY));
            message.append("\n");
        }
    }

    private static String trimExponent(double l) {
        if (Math.rint(l) == l) {
            return String.valueOf((long) l);
        }
        return String.format("%.2f", l);
    }

    private static void appendPendingSection(MutableComponent message, UpkeepBreakdown breakdown) {
        if (!breakdown.hasPendingItems()) {
            return;
        }

        message.append("\n");
        message.append(Component.translatable("message.lc_ftb_hook.upkeep_detail.pending_heading")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        message.append("\n");
        message.append(Component.translatable("message.lc_ftb_hook.upkeep_detail.pending_hint")
                .withStyle(ChatFormatting.GRAY));
        message.append("\n");

        for (UpkeepBreakdown.PendingProtectionLine line : breakdown.pendingProtections()) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            Component label = Component.translatable(line.labelKey())
                    .copy().append(Component.literal(" (" + line.regionName() + ")"));
            String messageKey = line.dismantled()
                    ? "message.lc_ftb_hook.upkeep_detail.pending_protection_dismantled"
                    : "message.lc_ftb_hook.upkeep_detail.pending_protection_queued";
            message.append(Component.translatable(messageKey, label, line.desiredValue())
                    .withStyle(line.dismantled() ? ChatFormatting.RED : ChatFormatting.GOLD));
            message.append("\n");
        }

        for (UpkeepBreakdown.PendingWarLine line : breakdown.pendingWars()) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            String messageKey = line.endWar()
                    ? "message.lc_ftb_hook.upkeep_detail.pending_war_end"
                    : "message.lc_ftb_hook.upkeep_detail.pending_war_declare";
            message.append(Component.translatable(messageKey, line.displayName())
                    .withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }

        if (breakdown.pendingForceLoadCount() > 0) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(Component.translatable(
                    "message.lc_ftb_hook.upkeep_detail.pending_forceload",
                    breakdown.pendingForceLoadCount()
            ).withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }

        if (breakdown.pendingForceUnloadCount() > 0) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(Component.translatable(
                    "message.lc_ftb_hook.upkeep_detail.pending_forceunload",
                    breakdown.pendingForceUnloadCount()
            ).withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }

        if (breakdown.pendingRegionAssignmentCount() > 0) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(Component.translatable(
                    "message.lc_ftb_hook.upkeep_detail.pending_region_assignments",
                    breakdown.pendingRegionAssignmentCount()
            ).withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }
    }

    private static Component buildSeeMoreButton() {
        return Component.translatable("message.lc_ftb_hook.upkeep_see_more")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, DETAILS_COMMAND))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.lc_ftb_hook.upkeep_see_more_hover")
                                        .withStyle(ChatFormatting.GRAY)
                        )));
    }

    private static void appendLine(MutableComponent message, String labelKey, Component value) {
        message.append(Component.translatable(labelKey).withStyle(ChatFormatting.GRAY));
        message.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY));
        message.append(value);
        message.append("\n");
    }

    private static Component formatFormula(String key, Component unitPrice, int count, Component subtotal) {
        return Component.translatable(key, unitPrice, count, subtotal)
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component styled(Component component, ChatFormatting... formats) {
        return component.copy().withStyle(formats);
    }

    private static Component nextPaymentValueText(int minutesUntilNextUpkeep) {
        if (minutesUntilNextUpkeep < 0) {
            return Component.translatable("gui.lc_ftb_hook.regions.upkeep_value_unknown");
        }
        if (minutesUntilNextUpkeep == 0) {
            return Component.translatable("gui.lc_ftb_hook.regions.upkeep_value_due_now");
        }
        return UpkeepPeriodFormat.format(minutesUntilNextUpkeep);
    }
}
