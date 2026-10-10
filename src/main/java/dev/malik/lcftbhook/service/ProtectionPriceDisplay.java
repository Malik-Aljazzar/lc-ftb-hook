package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.client.ClientClaimPrices;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;

/**
 * Small shared formatting helpers still needed after protection editing moved
 * out of FTB's generic properties screen and into the Region screens (see
 * plan A6) - the upkeep-period label (used by the war screen, upkeep
 * commands, and the FTB Chunks claim screen tooltip) and the claim-visibility
 * config-id normalizer.
 */
public final class ProtectionPriceDisplay {
    private ProtectionPriceDisplay() {
    }

    /**
     * @param configuredPrice a protection property's raw config value
     *                        (denominated in tenths of a copper, per single
     *                        chunk - see the header of
     *                        {@code ProtectionPriceConfig.FILE_NAME}) - always
     *                        rounded DOWN to the nearest whole copper here,
     *                        matching how the real per-region bill is rounded.
     */
    public static Component formatPricePerChunk(long configuredPrice) {
        return dev.malik.lcftbhook.util.Money.format(Math.floorDiv(configuredPrice, 10L));
    }

    public static Component upkeepPeriodLabel() {
        int minutes = FMLEnvironment.dist == Dist.CLIENT ? ClientClaimPrices.upkeepPeriodMinutes() : -1;
        if (minutes <= 0) {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                return formatUpkeepPeriodLabel(60);
            }
            minutes = dev.malik.lcftbhook.config.LCFtbHookConfig.SERVER.upkeepPeriodMinutes.get();
        }
        return formatUpkeepPeriodLabel(minutes);
    }

    /**
     * "In X:YY" (live mm:ss, ticking every frame from a wall-clock-delta
     * interpolation of the last sync - see {@link ClientClaimPrices#liveSecondsUntilNextUpkeep()})
     * for the next upkeep settlement pass - server-only value (there's no
     * single "next due" for a config-side default the way there is for the
     * period length itself), so on the server this always reports unknown;
     * only meaningful client-side after a sync.
     */
    public static Component nextUpkeepLabel() {
        int seconds = FMLEnvironment.dist == Dist.CLIENT ? ClientClaimPrices.liveSecondsUntilNextUpkeep() : -1;
        if (seconds < 0) {
            return Component.translatable("gui.lc_ftb_hook.regions.upkeep_next_unknown");
        }
        if (seconds == 0) {
            return Component.translatable("gui.lc_ftb_hook.regions.upkeep_next_due_now");
        }
        return Component.translatable("gui.lc_ftb_hook.regions.upkeep_next_payment", formatMmSs(seconds));
    }

    private static String formatMmSs(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" + seconds : String.valueOf(seconds));
    }

    /**
     * {@code nextUpkeepLabel()} ("Next payment in X") and
     * {@code upkeepPeriodLabel()} ("X", meant to follow a "per" label) used
     * to always get shown as two separate tooltip lines - which, checked
     * right after a settlement, both say the same "X" and read as pure
     * duplication. Folds the period into the next-payment line as
     * parenthetical context instead, matching how {@code UpkeepMessageBuilder}
     * (the {@code /upkeep_details} command output) already does it.
     */
    public static Component nextUpkeepWithPeriodLabel() {
        return nextUpkeepLabel().copy()
                .append(" (")
                .append(Component.translatable("message.lc_ftb_hook.upkeep_detail.every_period", upkeepPeriodLabel()))
                .append(")");
    }

    public static Component formatUpkeepPeriodLabel(int minutes) {
        if (minutes <= 1) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.one_minute");
        }
        if (minutes < 60) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.minutes", minutes);
        }
        if (minutes == 60) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.one_hour");
        }
        if (minutes % 60 == 0) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.hours", minutes / 60);
        }
        if (minutes == 1440) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.one_day");
        }
        if (minutes % 1440 == 0) {
            return Component.translatable("message.lc_ftb_hook.upkeep_period.days", minutes / 1440);
        }
        return Component.translatable("message.lc_ftb_hook.upkeep_period.minutes", minutes);
    }

    @Nullable
    public static String normalizePropertyKey(String configId) {
        if (configId == null || configId.isBlank()) {
            return null;
        }

        String key = configId;
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        int slash = key.lastIndexOf('/');
        if (slash >= 0) {
            key = key.substring(slash + 1);
        }
        int dot = key.lastIndexOf('.');
        if (dot >= 0) {
            key = key.substring(dot + 1);
        }
        return key.isBlank() ? null : key;
    }
}
