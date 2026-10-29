package dev.malik.lcftbhook.bank;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import net.minecraft.server.MinecraftServer;

/**
 * Deposits an already-withdrawn "sink" payment (upkeep, chunk-claim price,
 * ...) into the active currency backend's server account instead of letting
 * the money vanish.
 * <p>
 * Guards against overflow before every deposit (Lightman's Currency's own
 * {@code CoinValue.addValue} is plain {@code long + long} with no overflow
 * check; Numismatics balances are {@code int}) and simply skips the deposit -
 * burning the money, exactly like every sink did before this account existed -
 * rather than risk a negative balance.
 */
public final class ServerAccountDeposit {
    private ServerAccountDeposit() {
    }

    public static void deposit(MinecraftServer server, long amount, String sourceLabel) {
        if (amount <= 0) {
            return;
        }
        CurrencyAccount account;
        try {
            account = Backends.currency().serverAccount(server);
        } catch (IllegalStateException unavailable) {
            LCFtbHook.LOGGER.warn("Server account unavailable - burning {} ({})", amount, sourceLabel);
            return;
        }
        if (!account.canReceive(amount)) {
            LCFtbHook.LOGGER.warn("Server account balance would overflow ({} + {}) - burning this payment instead.",
                    account.balanceCopper(), amount);
            return;
        }
        account.deposit(amount, sourceLabel);
    }
}
