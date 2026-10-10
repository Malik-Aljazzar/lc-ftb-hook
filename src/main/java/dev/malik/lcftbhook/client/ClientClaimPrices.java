package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.bank.ClaimResultIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

public final class ClientClaimPrices {
    public static final Set<String> LC_CLAIM_RESULT_IDS = Set.of(
            ClaimResultIds.INSUFFICIENT_FUNDS,
            ClaimResultIds.INSUFFICIENT_FUNDS_BULK,
            "message.lc_ftb_hook.claim_rank_denied"
    );


    private static long claimPrice = -1L;
    private static long forceLoadUpkeepPrice = -1L;
    private static int upkeepPeriodMinutes = -1;
    private static int minutesUntilNextUpkeep = -1;
    private static int secondsUntilNextUpkeepAtSync = -1;
    private static long secondsUntilNextUpkeepSyncedAtMillis;
    private static int freeChunks;
    private static int claimedChunks;
    private static boolean protectionFieldsSynced;
    private static boolean balanceSynced;
    private static boolean balanceEmpty = true;
    @Nullable
    private static String balanceText;
    private static boolean privateBalanceEmpty = true;
    @Nullable
    private static String privateBalanceText;
    private static boolean hasCountryBalance;
    private static boolean countryBalanceEmpty = true;
    @Nullable
    private static String countryBalanceText;
    private static int lastUpdateTotalChunks = 1;
    private static Map<String, Integer> lastProblems = Map.of();
    // Defaults true so menu entries aren't briefly hidden before the first
    // sync lands - purely a display timing nicety, never the actual
    // permission boundary (the server independently re-checks
    // BankAccountHelper.canPurchaseForTeam for every claim/unclaim/
    // force-load/unload request regardless of what this says).
    private static boolean canManageClaims = true;

    private ClientClaimPrices() {
    }

    public static void update(
            long claim,
            long forceLoadUpkeep,
            int upkeepPeriod,
            int minutesUntilNext,
            int secondsUntilNext,
            int free,
            int claimed,
            boolean syncedBalance,
            boolean emptyBalance,
            String balance,
            boolean emptyPrivateBalance,
            String privateBalance,
            boolean countryBalanceAvailable,
            boolean emptyCountryBalance,
            String countryBalance,
            java.util.List<dev.malik.lcftbhook.data.ProtectionProperty> protectionFields,
            boolean canManageClaimsValue
    ) {
        claimPrice = claim;
        forceLoadUpkeepPrice = forceLoadUpkeep;
        upkeepPeriodMinutes = upkeepPeriod;
        minutesUntilNextUpkeep = minutesUntilNext;
        secondsUntilNextUpkeepAtSync = secondsUntilNext;
        secondsUntilNextUpkeepSyncedAtMillis = System.currentTimeMillis();
        freeChunks = free;
        claimedChunks = claimed;
        balanceSynced = syncedBalance;
        balanceEmpty = emptyBalance;
        balanceText = balance;
        privateBalanceEmpty = emptyPrivateBalance;
        privateBalanceText = privateBalance;
        hasCountryBalance = countryBalanceAvailable;
        countryBalanceEmpty = emptyCountryBalance;
        countryBalanceText = countryBalance;
        dev.malik.lcftbhook.data.ProtectionCatalog.setClientFields(protectionFields);
        protectionFieldsSynced = true;
        canManageClaims = canManageClaimsValue;
    }

    /** Whether the local player currently has spending access on their team's account (mirrors the team's own configured Lightman's Currency access level) - claim/unclaim/force-load/unload all require this server-side. */
    public static boolean canManageClaims() {
        return canManageClaims;
    }

    public static int upkeepPeriodMinutes() {
        return upkeepPeriodMinutes;
    }

    /** -1 if not yet synced (server just started, or no sync has landed yet). */
    public static int minutesUntilNextUpkeep() {
        return minutesUntilNextUpkeep;
    }

    /**
     * Exact seconds remaining until the next upkeep settlement, interpolated
     * from wall-clock time elapsed since the last sync landed - lets a
     * tooltip tick a live mm:ss countdown every frame without needing a
     * per-second network sync. -1 if not yet synced.
     */
    public static int liveSecondsUntilNextUpkeep() {
        if (secondsUntilNextUpkeepAtSync < 0) {
            return -1;
        }
        long elapsedSeconds = (System.currentTimeMillis() - secondsUntilNextUpkeepSyncedAtMillis) / 1000L;
        return (int) Math.max(0L, secondsUntilNextUpkeepAtSync - elapsedSeconds);
    }

    /** The synced price of a protection of the server's active claim backend, null if unknown. */
    @Nullable
    public static Long protectionPrice(String propertyKey) {
        for (dev.malik.lcftbhook.data.ProtectionProperty property : dev.malik.lcftbhook.data.ProtectionProperty.values()) {
            if (property.id().equals(propertyKey)) {
                return property.configPrice();
            }
        }
        return null;
    }

    public static boolean protectionPricesSynced() {
        return protectionFieldsSynced;
    }

    private static long lastRefreshRequestMillis;
    private static final long REFRESH_REQUEST_INTERVAL_MILLIS = 3000L;

    /**
     * @param showCtrlHint whether to append the "Hold Ctrl to see more" tip -
     *                      only accurate where a Ctrl-hold overlay actually
     *                      still exists (FTB Chunks' own claim manager); Xaero's
     *                      World Map marketplace colors are always-on now, so
     *                      it should pass false there.
     */
    public static java.util.List<Component> moneyLines(boolean showCtrlHint) {
        // This is called every render frame by an always-visible panel
        // (XaeroClaimInfoPanel) as well as whenever the FTB Chunks claim
        // screen is open, but the balance/price fields only ever change when
        // a SyncClaimPricesPayload arrives, which nothing here triggers on
        // its own - a balance change from outside this mod's own claim/
        // force-load/war actions (e.g. depositing into a Lightman's Currency
        // terminal, or upkeep silently withdrawing its payment) never
        // refreshes what's shown here otherwise, since none of THIS mod's
        // own resync triggers fire for those. Polling the server for a fresh
        // sync every few seconds while this panel is actually being drawn
        // keeps the displayed balance from going stale indefinitely, without
        // spamming a request every single frame.
        long now = System.currentTimeMillis();
        if (now - lastRefreshRequestMillis >= REFRESH_REQUEST_INTERVAL_MILLIS) {
            lastRefreshRequestMillis = now;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.malik.lcftbhook.network.RequestClaimPricesPayload());
        }
        if (!isSynced()) {
            return java.util.List.of();
        }
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(styledLabel("private_balance").append(" ").append(styledValue(formatPrivateBalance())));
        // Claim price, the country account balance, and the force-load
        // upkeep price/period are all officers/owner concerns (only they can
        // actually claim, force-load, or spend from the team account - see
        // BankAccountHelper/RegionService's own rank checks) - a plain
        // member only ever needs to see their own private balance here.
        if (dev.malik.lcftbhook.client.ClientTeamRank.isOfficerOrBetter()) {
            lines.add(styledLabel("claim").append(" ").append(styledValue(formatEffectiveClaimPrice())));
            // Only shown for officers/the owner of an actual party - a solo
            // player's "team" is just their own personal account, which is
            // already covered by the private balance line above, so a second
            // (identical) line would be pointless there.
            if (hasCountryBalance) {
                lines.add(styledLabel("country_balance").append(" ").append(styledValue(formatCountryBalance())));
            }
            // Own line for the label, since "Force-load upkeep <price> per <period>"
            // together is wider than the panel and was spilling past its edge.
            lines.add(styledLabel("upkeep"));
            lines.add(styledValue(formatPrice(forceLoadUpkeepPrice))
                    .append(" ").append(Component.literal(formatUpkeepPeriod(upkeepPeriodMinutes).getString()).withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
        if (showCtrlHint) {
            // Two lines for the same reason as the upkeep split above - the
            // full hint text is wider than the panel.
            lines.add(Component.translatable("gui.lc_ftb_hook.label.ctrl_hint_line1")
                    .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
            lines.add(Component.translatable("gui.lc_ftb_hook.label.ctrl_hint_line2")
                    .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
        }
        return lines;
    }

    private static MutableComponent styledLabel(String key) {
        return label(key).copy().withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    private static MutableComponent styledValue(Component value) {
        return value.copy().withStyle(net.minecraft.ChatFormatting.GOLD);
    }

    public static boolean isSynced() {
        return claimPrice >= 0L
                && forceLoadUpkeepPrice >= 0L
                && upkeepPeriodMinutes > 0
                && balanceSynced
                && protectionPricesSynced();
    }

    public static boolean isLcClaimResult(String resultId) {
        return LC_CLAIM_RESULT_IDS.contains(resultId);
    }

    public static void noteChunkUpdate(int totalChunks, int changedChunks, Map<String, Integer> problems) {
        lastUpdateTotalChunks = totalChunks;
        lastProblems = problems;
    }

    public static MutableComponent claimProblemLine(String resultId) {
        if (ClaimResultIds.INSUFFICIENT_FUNDS_BULK.equals(resultId)) {
            int count = lastProblems.getOrDefault(resultId, lastUpdateTotalChunks);
            return insufficientFundsBulkMessage(count);
        }
        if (ClaimResultIds.INSUFFICIENT_FUNDS.equals(resultId)) {
            int count = lastProblems.getOrDefault(resultId, 1);
            if (count > 1) {
                return Component.translatable(
                        "message.lc_ftb_hook.insufficient_funds_bulk_claim",
                        formatEffectiveClaimPrice(),
                        count,
                        formatBalance()
                );
            }
            return insufficientFundsMessage();
        }
        return Component.translatable(resultId);
    }

    public static MutableComponent insufficientFundsMessage() {
        return Component.translatable(
                ClaimResultIds.INSUFFICIENT_FUNDS,
                formatEffectiveClaimPrice(),
                formatBalance()
        );
    }

    public static MutableComponent insufficientFundsBulkMessage(int chunkCount) {
        long unitPrice = nextClaimUnitPrice();
        return Component.translatable(
                ClaimResultIds.INSUFFICIENT_FUNDS_BULK,
                formatPrice(unitPrice * Math.max(chunkCount, 1)),
                chunkCount,
                formatBalance()
        );
    }

    private static long nextClaimUnitPrice() {
        return claimedChunks < freeChunks ? 0L : claimPrice;
    }

    private static Component formatEffectiveClaimPrice() {
        return formatPrice(nextClaimUnitPrice());
    }

    private static Component label(String key) {
        return Component.translatable("gui.lc_ftb_hook.label." + key);
    }

    private static Component formatBalance() {
        if (balanceEmpty) {
            return Component.translatable("message.lc_ftb_hook.balance_empty");
        }
        return dev.malik.lcftbhook.util.IconText.of(balanceText == null ? "" : balanceText);
    }

    private static Component formatPrivateBalance() {
        if (privateBalanceEmpty) {
            return Component.translatable("message.lc_ftb_hook.balance_empty");
        }
        return dev.malik.lcftbhook.util.IconText.of(privateBalanceText == null ? "" : privateBalanceText);
    }

    private static Component formatCountryBalance() {
        if (countryBalanceEmpty) {
            return Component.translatable("message.lc_ftb_hook.balance_empty");
        }
        return dev.malik.lcftbhook.util.IconText.of(countryBalanceText == null ? "" : countryBalanceText);
    }

    private static Component formatPrice(long amount) {
        if (amount <= 0L) {
            return Component.translatable("gui.lc_ftb_hook.price_free");
        }
        return dev.malik.lcftbhook.util.Money.format(amount);
    }

    private static Component formatUpkeepPeriod(int minutes) {
        if (minutes % 1440 == 0) {
            int days = minutes / 1440;
            return days == 1
                    ? Component.translatable("gui.lc_ftb_hook.period.per_day")
                    : Component.translatable("gui.lc_ftb_hook.period.per_days", days);
        }
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours == 1
                    ? Component.translatable("gui.lc_ftb_hook.period.per_hour")
                    : Component.translatable("gui.lc_ftb_hook.period.per_hours", hours);
        }
        return minutes == 1
                ? Component.translatable("gui.lc_ftb_hook.period.per_minute")
                : Component.translatable("gui.lc_ftb_hook.period.per_minutes", minutes);
    }
}
