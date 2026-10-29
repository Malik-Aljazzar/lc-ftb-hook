package dev.malik.lcftbhook.port.currency;

import net.minecraft.network.chat.Component;

/**
 * One team's or player's account, resolved through {@link CurrencyPort}.
 * Amounts are always in the backend's smallest unit ("copper") - no
 * currency-mod-specific money type crosses this boundary, so business logic
 * never needs to know which currency mod is actually backing it.
 */
public interface CurrencyAccount {
    long balanceCopper();

    boolean canAfford(long copper);

    /**
     * Withdraws exactly {@code copper} and records it under {@code label} in
     * whatever transaction log the backend provides. Caller is expected to
     * have already checked {@link #canAfford} - this does not re-check or
     * clamp.
     */
    void withdraw(long copper, String label);

    void deposit(long copper, String label);

    /**
     * Whether {@link #deposit} of {@code copper} would land in full - false
     * when the backend's balance would overflow (Numismatics balances are
     * {@code int}; Lightman's Currency adds {@code long}s unchecked), in which
     * case a deposit would be skipped or corrupt the balance.
     */
    boolean canReceive(long copper);

    String ownerDisplayName();

    /**
     * The full balance for display - may include more than
     * {@link #balanceCopper()} (e.g. Lightman's Currency balances on other coin
     * chains). Empty-balance wording is the caller's job.
     */
    Component balanceText();

    /** Whether the account holds nothing at all (on any coin chain the backend has). */
    boolean isEmpty();

    /**
     * Moves the ENTIRE balance - every coin chain the backend has, not just
     * {@link #balanceCopper()} - into {@code target} (same backend), e.g. when a
     * party is disbanded.
     */
    void transferAllTo(CurrencyAccount target, String label);
}
