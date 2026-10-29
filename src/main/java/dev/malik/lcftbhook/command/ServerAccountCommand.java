package dev.malik.lcftbhook.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.bank.AccountTransfer;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import dev.malik.lcftbhook.port.currency.CurrencyPort;
import dev.malik.lcftbhook.service.ClaimPriceSync;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Op-facing access to the server-wide sink account (upkeep and claim
 * payments land there). Without arguments it reports the balance, then lets
 * the active currency backend do whatever it offers on top (Lightman's
 * Currency: selects the account in the op's ATM so it can be managed through
 * LC's own UI). The subcommands work with every currency backend:
 * <ul>
 *   <li>{@code withdraw <amount> [<player>]} - pays into a player's personal account (default: the op's own)</li>
 *   <li>{@code withdraw_team <amount> <player>} - pays into the account of that player's team</li>
 *   <li>{@code deposit <amount>} - moves money from the op's personal account into the server account</li>
 *   <li>{@code card} - binds the held bank card to the server account (Numismatics)</li>
 * </ul>
 * Amounts are in the backend's smallest coin (copper / spur). Every movement
 * is logged to the server log, since this account holds the whole server's
 * collected money.
 */
public final class ServerAccountCommand {
    private static final String PAYOUT_LABEL = "Server Account Payout";
    private static final String DEPOSIT_LABEL = "Server Account Deposit";

    private ServerAccountCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LCFtbHook.MOD_ID)
                .then(Commands.literal("server_account")
                        .requires(src -> src.hasPermission(2))
                        .executes(ServerAccountCommand::execute)
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> withdrawToPlayer(ctx, null))
                                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                                .executes(ctx -> withdrawToPlayer(ctx, singleProfile(ctx))))))
                        .then(Commands.literal("withdraw_team")
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                                .executes(ctx -> withdrawToTeam(ctx, singleProfile(ctx))))))
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ServerAccountCommand::deposit)))
                        .then(Commands.literal("card").executes(ServerAccountCommand::bindCard))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CurrencyAccount account = serverAccount(source);
        if (account == null) {
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Server account balance: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Money.formatBalance(account).copy().withStyle(ChatFormatting.GOLD)), false);

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            Component followUp = Backends.currency().focusServerAccount(player);
            if (followUp != null) {
                source.sendSuccess(() -> followUp.copy().withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    /** {@code target} null = the op running the command. */
    private static int withdrawToPlayer(CommandContext<CommandSourceStack> context, @Nullable GameProfile target) {
        CommandSourceStack source = context.getSource();
        UUID recipientId;
        String recipientName;
        if (target != null) {
            recipientId = target.getId();
            recipientName = target.getName();
        } else {
            ServerPlayer self = source.getPlayer();
            if (self == null) {
                source.sendFailure(Component.translatable("message.lc_ftb_hook.server_account.player_required"));
                return 0;
            }
            recipientId = self.getUUID();
            recipientName = self.getGameProfile().getName();
        }
        CurrencyAccount to;
        try {
            to = Backends.currency().playerAccount(source.getServer(), recipientId);
        } catch (IllegalStateException missing) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.server_account.no_account", recipientName));
            return 0;
        }
        return payOut(context, to, recipientName, List.of(recipientId));
    }

    /** Pays into the account of {@code member}'s team - a party's shared account, or a solo player's own. */
    private static int withdrawToTeam(CommandContext<CommandSourceStack> context, GameProfile member) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        UUID teamId = Backends.claim().teamIdForPlayer(server, member.getId());
        if (teamId == null || Backends.claim().isServerTeam(server, teamId)) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.server_account.no_team", member.getName()));
            return 0;
        }
        String teamName = Backends.claim().teamDisplayName(server, teamId);
        String recipientName = teamName == null || teamName.isBlank() ? member.getName() : teamName;
        CurrencyAccount to;
        try {
            to = Backends.currency().teamAccount(server, teamId);
        } catch (IllegalStateException missing) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.server_account.no_account", recipientName));
            return 0;
        }
        return payOut(context, to, recipientName, Backends.claim().members(server, teamId));
    }

    private static int payOut(CommandContext<CommandSourceStack> context, CurrencyAccount to, String recipientName,
                              Collection<UUID> notify) {
        CommandSourceStack source = context.getSource();
        CurrencyAccount from = serverAccount(source);
        if (from == null) {
            return 0;
        }
        long amount = LongArgumentType.getLong(context, "amount");
        if (!TeamBankCommand.reportTransfer(source, AccountTransfer.move(from, to, amount, PAYOUT_LABEL), amount, from)) {
            return 0;
        }
        LCFtbHook.LOGGER.info("{} paid {} from the server account to {} (server account now {}).",
                source.getTextName(), amount, recipientName, from.balanceCopper());
        source.sendSuccess(() -> Component.translatable("message.lc_ftb_hook.server_account.paid",
                Money.formatValue(amount), recipientName, Money.formatBalance(from)), true);

        MinecraftServer server = source.getServer();
        ServerPlayer executor = source.getPlayer();
        for (UUID id : notify) {
            ServerPlayer recipient = server.getPlayerList().getPlayer(id);
            if (recipient == null) {
                continue;
            }
            if (recipient != executor) {
                recipient.sendSystemMessage(Component.translatable("message.lc_ftb_hook.server_account.received",
                        Money.formatValue(amount), recipientName));
            }
            ClaimPriceSync.syncToPlayer(recipient);
        }
        return 1;
    }

    private static int deposit(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.server_account.player_required"));
            return 0;
        }
        CurrencyAccount to = serverAccount(source);
        if (to == null) {
            return 0;
        }
        CurrencyAccount from;
        try {
            from = Backends.currency().playerAccount(source.getServer(), player.getUUID());
        } catch (IllegalStateException missing) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.server_account.no_account", player.getGameProfile().getName()));
            return 0;
        }
        long amount = LongArgumentType.getLong(context, "amount");
        if (!TeamBankCommand.reportTransfer(source, AccountTransfer.move(from, to, amount, DEPOSIT_LABEL), amount, from)) {
            return 0;
        }
        LCFtbHook.LOGGER.info("{} deposited {} into the server account (now {}).",
                source.getTextName(), amount, to.balanceCopper());
        source.sendSuccess(() -> Component.translatable("message.lc_ftb_hook.server_account.deposited",
                Money.formatValue(amount), Money.formatBalance(to)), true);
        ClaimPriceSync.syncToPlayer(player);
        return 1;
    }

    private static int bindCard(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.lc_ftb_hook.server_account.card_player_required"));
            return 0;
        }
        CurrencyPort currency = Backends.currency();
        switch (currency.bindServerAccountCard(player)) {
            case BOUND -> {
                LCFtbHook.LOGGER.info("{} bound a bank card to the server account.", source.getTextName());
                source.sendSuccess(() -> Component.translatable("message.lc_ftb_hook.server_account.card_bound"), true);
                return 1;
            }
            case NO_CARD -> source.sendFailure(Component.translatable("message.lc_ftb_hook.team_bank.card_missing"));
            case UNSUPPORTED -> source.sendFailure(Component.translatable("message.lc_ftb_hook.server_account.card_unsupported"));
        }
        return 0;
    }

    @Nullable
    private static CurrencyAccount serverAccount(CommandSourceStack source) {
        try {
            return Backends.currency().serverAccount(source.getServer());
        } catch (IllegalStateException unavailable) {
            source.sendFailure(Component.literal("Server account is unavailable right now."));
            return null;
        }
    }

    /**
     * Exactly one player who has joined this server before. In offline mode
     * every name resolves to a profile, so without the check a typo would
     * pay into an account nobody can ever reach.
     */
    private static GameProfile singleProfile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
        }
        GameProfile profile = profiles.iterator().next();
        MinecraftServer server = context.getSource().getServer();
        if (server.getPlayerList().getPlayer(profile.getId()) == null
                && !Files.exists(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(profile.getId() + ".dat"))) {
            throw UNKNOWN_PLAYER.create(profile.getName());
        }
        return profile;
    }

    private static final DynamicCommandExceptionType UNKNOWN_PLAYER = new DynamicCommandExceptionType(name ->
            Component.translatable("message.lc_ftb_hook.server_account.unknown_player", name));
}
