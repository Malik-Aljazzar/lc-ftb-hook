package dev.malik.lcftbhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import dev.malik.lcftbhook.util.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Op-facing access to the server-wide sink account (upkeep and claim
 * payments land there). Reports the balance, then lets the active currency
 * backend do whatever it offers on top (Lightman's Currency: selects the
 * account in the op's ATM so it can be managed through LC's own UI).
 */
public final class ServerAccountCommand {
    private ServerAccountCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LCFtbHook.MOD_ID)
                .then(Commands.literal("server_account")
                        .requires(src -> src.hasPermission(2))
                        .executes(ServerAccountCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CurrencyAccount account;
        try {
            account = Backends.currency().serverAccount(source.getServer());
        } catch (IllegalStateException unavailable) {
            source.sendFailure(Component.literal("Server account is unavailable right now."));
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
}
