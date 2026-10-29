package dev.malik.lcftbhook.bank;

import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;

/**
 * Moves an amount between two accounts of the active currency backend
 * without ever creating or destroying money: the target is checked for
 * room before anything moves, and the deposit only happens for what the
 * withdrawal actually took ({@link CurrencyAccount#withdraw} can't report a
 * refusal - Numismatics' {@code deduct} returning false is only logged).
 */
public final class AccountTransfer {
    private AccountTransfer() {
    }

    public enum Result { DONE, INSUFFICIENT_FUNDS, TARGET_FULL, FAILED }

    public static Result move(CurrencyAccount from, CurrencyAccount to, long amount, String label) {
        if (amount <= 0) {
            return Result.FAILED;
        }
        if (!from.canAfford(amount)) {
            return Result.INSUFFICIENT_FUNDS;
        }
        if (!to.canReceive(amount)) {
            return Result.TARGET_FULL;
        }
        long before = from.balanceCopper();
        from.withdraw(amount, label);
        long taken = before - from.balanceCopper();
        if (taken != amount) {
            LCFtbHook.LOGGER.error("Transfer of {} from {} to {} ({}) took {} instead - returning it, nothing transferred.",
                    amount, from.ownerDisplayName(), to.ownerDisplayName(), label, taken);
            if (taken > 0) {
                from.deposit(taken, label + " (reverted)");
            }
            return Result.FAILED;
        }
        to.deposit(amount, label);
        return Result.DONE;
    }
}
