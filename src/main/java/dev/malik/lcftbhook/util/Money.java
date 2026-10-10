package dev.malik.lcftbhook.util;

import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Backend-agnostic money display. Every amount is in the active currency
 * backend's smallest unit ("copper"); the actual coin notation comes from
 * {@code Backends.currency().format(...)}, so this works on server and
 * client alike regardless of which currency mod is installed.
 */
public final class Money {
    private Money() {
    }

    /** The amount in coin notation, or empty text for zero/negative (for "+X"-style suffixes that may vanish). */
    public static Component format(long amount) {
        if (amount <= 0) {
            return Component.empty();
        }
        return Backends.currency().format(amount);
    }

    /** Like {@link #format}, but a genuine zero shows the plain "Free" label instead of nothing. */
    public static Component formatOrFree(long amount) {
        if (amount <= 0) {
            return Component.translatable("gui.lc_ftb_hook.price_free");
        }
        return Backends.currency().format(amount);
    }

    /** A price: zero shows a green "Free". */
    public static Component formatPrice(long amount) {
        if (amount <= 0) {
            return Component.translatable("gui.lc_ftb_hook.price_free").withStyle(ChatFormatting.GREEN);
        }
        return Backends.currency().format(amount);
    }

    /** An amount of money someone has or receives: zero shows "nothing". */
    public static Component formatValue(long amount) {
        if (amount <= 0) {
            return Component.translatable("message.lc_ftb_hook.balance_empty");
        }
        return Backends.currency().format(amount);
    }

    public static Component formatBalance(CurrencyAccount account) {
        if (account.isEmpty()) {
            return Component.translatable("message.lc_ftb_hook.balance_empty");
        }
        return account.balanceText();
    }
}
