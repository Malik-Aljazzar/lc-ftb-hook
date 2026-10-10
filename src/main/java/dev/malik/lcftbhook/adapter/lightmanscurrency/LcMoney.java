package dev.malik.lcftbhook.adapter.lightmanscurrency;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;

public final class LcMoney {
    private LcMoney() {
    }

    public static MoneyValue fromCopper(long amount) {
        if (amount <= 0) {
            return MoneyValue.empty();
        }
        return CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount);
    }

    /**
     * The account's balance on the "main" (Coins) chain only, ignoring any
     * balance on the separate "Emeralds" chain (the two are kept apart - chunks
     * are never priced in emerald coins).
     * <p>
     * {@code MoneyStorage.valueOf(String)} does NOT take a chain id despite
     * how it looks - it looks a value up by {@code MoneyValue.getUniqueName()},
     * which for a {@link CoinValue} is {@code type + "!" + chain} (see
     * {@code MoneyValue.generateCustomUniqueName}), not the bare chain string.
     * {@code account.getMoneyStorage().valueOf(CoinAPI.MAIN_CHAIN)} therefore
     * always missed (key "main" never matches the real
     * "lightmanscurrency:...!main" key) and silently returned empty -
     * showing "nothing" everywhere real Coins-chain money existed. Matching
     * on {@link CoinValue#getChain()} directly instead of trying to
     * reconstruct the internal key is what actually works.
     */
    public static MoneyValue mainChainValue(IBankAccount account) {
        for (MoneyValue value : account.getMoneyStorage().allValues()) {
            if (value instanceof CoinValue coinValue && CoinAPI.MAIN_CHAIN.equals(coinValue.getChain())) {
                return value;
            }
        }
        return MoneyValue.empty();
    }
}
