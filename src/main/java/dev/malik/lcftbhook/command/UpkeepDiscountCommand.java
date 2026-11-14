package dev.malik.lcftbhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.service.ProtectionPricing;
import dev.malik.lcftbhook.service.TeamCatalog;
import dev.malik.lcftbhook.service.UpkeepDeceleration;
import dev.malik.lcftbhook.service.UpkeepGating;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /lc_ftb_hook upkeep_discount}: the one place that explains the
 * "upkeep discount for large chunk amounts" (the upkeep screens only show its
 * amount) - how it works, the server's current curve with examples, and for a
 * player whose team bills protection: each setting's chunk count, what it is
 * billed as, and the team's discount per upkeep.
 */
public final class UpkeepDiscountCommand {
    private static final long[] EXAMPLES = {200, 500, 1000};
    private static final String KEY = "message.lc_ftb_hook.upkeep_discount.";

    private UpkeepDiscountCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LCFtbHook.MOD_ID)
                .then(Commands.literal("upkeep_discount")
                        .executes(UpkeepDiscountCommand::explain)));
    }

    private static int explain(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UpkeepDeceleration curve = UpkeepDeceleration.configured();
        MutableComponent message = Component.translatable(KEY + "title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (!curve.enabled()) {
            message.append("\n").append(Component.translatable(KEY + "off").withStyle(ChatFormatting.GRAY));
        } else {
            appendHowItWorks(message, curve);
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                appendTeam(message, player, curve);
            }
        }
        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static void appendHowItWorks(MutableComponent message, UpkeepDeceleration curve) {
        line(message, Component.translatable(KEY + "counting"));
        line(message, Component.translatable(KEY + "curve", curve.threshold(), formula(curve)));
        line(message, Component.translatable(KEY + "fair"));
        StringBuilder examples = new StringBuilder();
        for (long chunks : EXAMPLES) {
            if (!examples.isEmpty()) {
                examples.append(", ");
            }
            examples.append(chunks).append(" -> ").append(oneDecimal(curve.effectiveChunks(chunks)))
                    .append(" (").append(percentOff(curve, chunks)).append(")");
        }
        line(message, Component.translatable(KEY + "examples", examples.toString()));
    }

    /** The team's own figures - only for players who may see the team's upkeep (same rule as upkeep_details). */
    private static void appendTeam(MutableComponent message, ServerPlayer player, UpkeepDeceleration curve) {
        MinecraftServer server = player.server;
        UUID teamId = TeamCatalog.teamOf(server, player.getUUID());
        if (teamId == null || !FtbHookSavedData.isAvailable(server) || !UpkeepGating.shouldChargeProtection(server, teamId)
                || (TeamCatalog.isParty(server, teamId) && !Backends.currency().canPlayerSpend(server, teamId, player.getUUID()))) {
            return;
        }
        message.append("\n\n").append(Component.translatable(KEY + "team").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        Map<ProtectionProperty, Long> perSetting = ProtectionPricing.billedChunksPerSetting(server, teamId);
        if (perSetting.isEmpty()) {
            line(message, Component.translatable(KEY + "team_none"));
            return;
        }
        for (Map.Entry<ProtectionProperty, Long> entry : perSetting.entrySet()) {
            long chunks = entry.getValue();
            line(message, Component.translatable(KEY + "team_setting",
                    Component.translatable("gui.lc_ftb_hook.protection_short." + entry.getKey().id()),
                    chunks, oneDecimal(curve.effectiveChunks(chunks)), percentOff(curve, chunks)));
        }
        long discount = ProtectionPricing.protectionDiscountForPricing(server, teamId,
                FtbHookSavedData.get(server).getPendingState(teamId), null);
        line(message, Component.translatable(KEY + "team_total",
                Component.literal("-").append(Money.formatValue(discount)).withStyle(ChatFormatting.GREEN)));
    }

    /** The curve with the server's numbers filled in, e.g. "64 + 128 * ln(1 + (n - 64) / 128)". */
    static String formula(UpkeepDeceleration curve) {
        String t = String.valueOf(curve.threshold());
        String s = trim(curve.scale());
        String log = s + " * ln(1 + (n - " + t + ") / " + s + ")";
        if (curve.floor() <= 0.0D) {
            return t + " + " + log;
        }
        if (curve.floor() >= 1.0D) {
            return "n";
        }
        return t + " + " + trim(curve.floor()) + " * (n - " + t + ") + " + trim(1.0D - curve.floor()) + " * " + log;
    }

    private static String percentOff(UpkeepDeceleration curve, long chunks) {
        long off = Math.round(100.0D * (1.0D - curve.effectiveChunks(chunks) / chunks));
        return "-" + off + "%";
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String trim(double value) {
        return Math.rint(value) == value ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "");
    }

    private static void line(MutableComponent message, Component text) {
        message.append("\n").append(Component.literal("- ").withStyle(ChatFormatting.DARK_GRAY))
                .append(text.copy().withStyle(ChatFormatting.GRAY));
    }
}
