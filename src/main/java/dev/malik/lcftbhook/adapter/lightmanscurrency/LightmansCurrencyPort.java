package dev.malik.lcftbhook.adapter.lightmanscurrency;

import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.currency.CurrencyAccount;
import dev.malik.lcftbhook.port.currency.CurrencyPort;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.ChainData;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.teams.ITeam;
import io.github.lightman314.lightmanscurrency.common.data.types.BankDataCache;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * {@link CurrencyPort} backed by Lightman's Currency. Works with any claim
 * backend: party membership/ranks come from {@code Backends.claim()} and are
 * mirrored into a real LC team by {@link LcTeamSync}; a solo team's account is
 * the player's own personal LC account (solo team id == player UUID in every
 * claim backend).
 */
public final class LightmansCurrencyPort implements CurrencyPort {
    @Override
    public CurrencyAccount teamAccount(MinecraftServer server, UUID teamId) {
        return new LcAccount(resolveTeamAccount(server, teamId));
    }

    @Override
    public CurrencyAccount playerAccount(MinecraftServer server, UUID playerId) {
        IBankAccount account = PlayerBankReference.of(playerId).get();
        if (account == null) {
            throw new IllegalStateException("Missing personal bank account for player " + playerId);
        }
        return new LcAccount(account);
    }

    /**
     * Mirrors the team's own LC bank-access level (owner-only by default, but
     * the owner can open it up to admins or all members in LC's team screen).
     * Until the LC team exists, falls back to officer-or-better.
     */
    @Override
    public boolean canPlayerSpend(MinecraftServer server, UUID teamId, UUID playerId) {
        if (!Backends.claim().isPartyTeam(server, teamId)) {
            return true;
        }
        LcTeamSync.ensureLinked(server, teamId);
        ITeam lcTeam = LcTeamSync.getLcTeam(server, teamId);
        if (lcTeam == null) {
            return Backends.claim().rankOf(server, teamId, playerId).isOfficerOrBetter();
        }
        int limit = lcTeam.getBankLimit();
        if (limit < 1) {
            return lcTeam.isMember(playerId);
        }
        return limit < 2 ? lcTeam.isAdmin(playerId) : lcTeam.isOwner(playerId);
    }

    @Override
    public void ensureTeamAccountExists(MinecraftServer server, UUID teamId) {
        LcTeamSync.ensureLinked(server, teamId);
    }

    @Override
    public void onTeamDeleted(MinecraftServer server, UUID teamId) {
        LcTeamSync.onTeamDeleted(server, teamId);
    }

    @Override
    public void reconcile(MinecraftServer server) {
        LcTeamLinks.reconcile(server);
    }

    @Override
    public CurrencyAccount serverAccount(MinecraftServer server) {
        IBankAccount account = LcServerAccount.get();
        if (account == null) {
            throw new IllegalStateException("Server account is unavailable (LC bank data not loaded yet)");
        }
        return new LcAccount(account);
    }

    @Override
    @Nullable
    public Component focusServerAccount(ServerPlayer op) {
        BankDataCache data = BankDataCache.TYPE.get(false);
        if (data == null) {
            return null;
        }
        data.setSelectedAccount(op, LcServerAccount.reference());
        return Component.literal("Selected as your active account - open any ATM to view/manage it.");
    }

    @Override
    public Component format(long amount) {
        return LcMoney.fromCopper(amount).getText();
    }

    /**
     * The main chain's coins, minus LC's own Emerald Coin: chunks are never
     * priced in it (project rule - previously enforced at confirm time in
     * the price screens; not offering it at all is simpler for players).
     */
    @Override
    public List<Denomination> denominations() {
        ChainData chain = CoinAPI.getApi().ChainData(CoinAPI.MAIN_CHAIN);
        if (chain == null) {
            return List.of(new Denomination(Component.literal("Copper"), 1L));
        }
        return chain.getCoreChain().stream()
                .filter(entry -> !isEmeraldCoin(entry.getCoin()))
                .sorted(ChainData.SORT_HIGHEST_VALUE_FIRST)
                .map(entry -> new Denomination(entry.getName(), entry.getCoreValue()))
                .toList();
    }

    private static boolean isEmeraldCoin(net.minecraft.world.item.Item coin) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(coin);
        return id.getNamespace().equals("lightmanscurrency") && id.getPath().contains("emerald");
    }

    /**
     * A party's account is its mirrored LC team's; resolvable via the stored
     * link even after the party itself was deleted (disband settlement).
     */
    private static IBankAccount resolveTeamAccount(MinecraftServer server, UUID teamId) {
        if (Backends.claim().isPartyTeam(server, teamId)) {
            IBankAccount party = LcTeamSync.getBankAccount(server, teamId);
            if (party == null) {
                String reason = LcTeamSync.whyUnlinked(teamId);
                throw new IllegalStateException("Missing LC team bank account for party " + teamId
                        + (reason != null ? " - " + reason : ""));
            }
            return party;
        }
        IBankAccount linked = LcTeamSync.getLinkedBankAccount(server, teamId);
        if (linked != null) {
            return linked;
        }
        IBankAccount personal = PlayerBankReference.of(teamId).get();
        if (personal == null) {
            throw new IllegalStateException("No bank account for team " + teamId);
        }
        return personal;
    }

    private record LcAccount(IBankAccount account) implements CurrencyAccount {
        @Override
        public long balanceCopper() {
            return LcMoney.mainChainValue(account).getCoreValue();
        }

        @Override
        public boolean canAfford(long copper) {
            return copper <= 0 || account.getMoneyStorage().containsValue(LcMoney.fromCopper(copper));
        }

        @Override
        public void withdraw(long copper, String label) {
            if (copper <= 0) {
                return;
            }
            MoneyValue withdrawn = account.withdrawMoney(LcMoney.fromCopper(copper));
            LcTransactionLog.logWithdraw(account, label, withdrawn);
        }

        @Override
        public void deposit(long copper, String label) {
            if (copper <= 0) {
                return;
            }
            MoneyValue amount = LcMoney.fromCopper(copper);
            account.depositMoney(amount);
            LcTransactionLog.logDeposit(account, label, amount);
        }

        @Override
        public boolean canReceive(long copper) {
            if (copper <= 0) {
                return true;
            }
            try {
                return Math.addExact(balanceCopper(), copper) >= 0;
            } catch (ArithmeticException overflow) {
                return false;
            }
        }

        @Override
        public String ownerDisplayName() {
            return account.getName().getString();
        }

        @Override
        public Component balanceText() {
            return account.getMoneyStorage().getAllValueText();
        }

        @Override
        public boolean isEmpty() {
            return account.getMoneyStorage().isEmpty();
        }

        @Override
        public void transferAllTo(CurrencyAccount target, String label) {
            if (!(target instanceof LcAccount lcTarget)) {
                throw new IllegalArgumentException("Cannot transfer between different currency backends");
            }
            for (MoneyValue value : account.getMoneyStorage().allValues()) {
                if (value.isEmpty()) {
                    continue;
                }
                account.withdrawMoney(value);
                LcTransactionLog.logWithdraw(account, label, value);
                lcTarget.account().depositMoney(value);
                LcTransactionLog.logDeposit(lcTarget.account(), label, value);
            }
        }
    }
}
