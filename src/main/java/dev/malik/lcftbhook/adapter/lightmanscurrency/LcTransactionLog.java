package dev.malik.lcftbhook.adapter.lightmanscurrency;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.common.notifications.types.bank.DepositWithdrawNotification;

import javax.annotation.Nullable;

/**
 * Every place this mod moves money through an {@link IBankAccount} needs to
 * push a log entry the same way LC's own bank UI/trader/tax code always does
 * (confirmed via decompile - {@code BankAPIImpl}/{@code TraderData}/
 * {@code TaxEntry} all explicitly push a {@link DepositWithdrawNotification}
 * right after withdrawing/depositing; {@code withdrawMoney}/{@code depositMoney}
 * alone never create one - only a {@code LowBalanceNotification}, and only if
 * the withdrawal happens to cross that threshold). Centralized here so every
 * automated transaction this mod makes is consistently logged, not just
 * whichever ones someone remembered to add a push for individually.
 */
public final class LcTransactionLog {
    private LcTransactionLog() {
    }

    public static void log(@Nullable IBankAccount account, String label, boolean isDeposit, MoneyValue amount) {
        if (account == null || amount == null || amount.isEmpty()) {
            return;
        }
        account.pushLocalNotification(new DepositWithdrawNotification.Custom(label, account.getName(), isDeposit, amount));
    }

    public static void logDeposit(@Nullable IBankAccount account, String label, MoneyValue amount) {
        log(account, label, true, amount);
    }

    public static void logWithdraw(@Nullable IBankAccount account, String label, MoneyValue amount) {
        log(account, label, false, amount);
    }
}
