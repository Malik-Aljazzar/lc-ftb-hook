package dev.malik.lcftbhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import dev.malik.lcftbhook.port.currency.CurrencyPort;
import dev.malik.lcftbhook.service.ClaimPriceSync;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Moves money between a player's personal account and their party's team
 * account, for any currency backend. Needed because Numismatics has no UI to
 * reach an account by id - without this a Numismatics party could never fund
 * its team account. Also works with Lightman's Currency (where the LC ATM is
 * the usual way). Amounts are in the backend's smallest coin (copper / spur).
 */
public final class TeamBankCommand {
    private TeamBankCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LCFtbHook.MOD_ID)
                .then(Commands.literal("team_bank")
                        .then(Commands.literal("balance").executes(TeamBankCommand::balance))
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> transfer(ctx, true))))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> transfer(ctx, false))))
                        .then(Commands.literal("card").executes(TeamBankCommand::bindCard))));
    }

    private static int bindCard(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        UUID teamId = partyOf(context.getSource(), player);
        if (teamId == null) {
            return 0;
        }
        CurrencyPort currency = Backends.currency();
        if (!currency.canPlayerSpend(player.server, teamId, player.getUUID())) {
            context.getSource().sendFailure(Component.translatable("message.lc_ftb_hook.team_bank.card_denied"));
            return 0;
        }
        switch (currency.bindTeamCard(player, teamId)) {
            case BOUND -> {
                context.getSource().sendSuccess(() -> Component.translatable("message.lc_ftb_hook.team_bank.card_bound",
                        Backends.claim().teamDisplayName(player.server, teamId)), false);
                return 1;
            }
            case NO_CARD -> context.getSource().sendFailure(Component.translatable("message.lc_ftb_hook.team_bank.card_missing"));
            case UNSUPPORTED -> context.getSource().sendFailure(Component.translatable("message.lc_ftb_hook.team_bank.card_unsupported"));
        }
        return 0;
    }

    private static int balance(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        UUID teamId = partyOf(context.getSource(), player);
        if (teamId == null) {
            return 0;
        }
        CurrencyAccount account = Backends.currency().teamAccount(player.server, teamId);
        context.getSource().sendSuccess(() -> Component.translatable("message.lc_ftb_hook.team_bank.balance", Money.formatBalance(account)), false);
        return 1;
    }

    private static int transfer(CommandContext<CommandSourceStack> context, boolean deposit) {
        ServerPlayer player = context.getSource().getPlayer();
        UUID teamId = partyOf(context.getSource(), player);
        if (teamId == null) {
            return 0;
        }
        MinecraftServer server = player.server;
        CurrencyPort currency = Backends.currency();
        if (!deposit && !currency.canPlayerSpend(server, teamId, player.getUUID())) {
            context.getSource().sendFailure(Component.translatable("message.lc_ftb_hook.team_bank.denied"));
            return 0;
        }
        long amount = LongArgumentType.getLong(context, "amount");
        CurrencyAccount team = currency.teamAccount(server, teamId);
        CurrencyAccount personal = currency.playerAccount(server, player.getUUID());
        CurrencyAccount from = deposit ? personal : team;
        CurrencyAccount to = deposit ? team : personal;
        if (!from.canAfford(amount)) {
            context.getSource().sendFailure(Component.translatable("message.lc_ftb_hook.insufficient_funds",
                    Money.formatValue(amount), Money.formatBalance(from)));
            return 0;
        }
        String label = deposit ? "Team Deposit" : "Team Withdrawal";
        from.withdraw(amount, label);
        to.deposit(amount, label);
        context.getSource().sendSuccess(() -> Component.translatable(
                deposit ? "message.lc_ftb_hook.team_bank.deposited" : "message.lc_ftb_hook.team_bank.withdrawn",
                Money.formatValue(amount), Money.formatBalance(team)), false);
        ClaimPriceSync.syncToPlayer(player);
        return 1;
    }

    /** The player's party, or null (with a message) - a solo player's team account IS their personal account. */
    @Nullable
    private static UUID partyOf(CommandSourceStack source, @Nullable ServerPlayer player) {
        if (player == null) {
            source.sendFailure(Component.literal("Players only."));
            return null;
        }
        UUID teamId = Backends.claim().teamIdForPlayer(player.server, player.getUUID());
        if (teamId == null || !Backends.claim().isPartyTeam(player.server, teamId)) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.team_bank.no_party"));
            return null;
        }
        return teamId;
    }
}
