package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Protection upkeep with the {@link UpkeepDeceleration} curve: every
 * protection setting is counted over all billable chunks of the team it is
 * active on - regions only decide which settings a chunk has, not how it is
 * priced - and each setting's count is billed through the curve. The total is
 * rounded down once for the whole team and never exceeds the linear,
 * per-region price ({@code linearCopper}), so turning the discount on can only
 * lower a bill.
 */
final class DeceleratedProtectionPricing {
    private DeceleratedProtectionPricing() {
    }

    /** The billed protection copper: the curve's total, capped at the linear price. */
    static long billedCopper(
            Iterable<ProtectionPricing.RegionChunkCount> regions,
            Function<java.util.UUID, Region> regionLookup,
            Map<String, String> pendingOverrides,
            long linearCopper,
            UpkeepDeceleration curve
    ) {
        if (!curve.enabled()) {
            return linearCopper;
        }
        Map<ProtectionProperty, Long> chunksPerSetting = chunksPerSetting(regions, regionLookup, pendingOverrides);
        double total = 0.0D;
        for (Map.Entry<ProtectionProperty, Long> entry : chunksPerSetting.entrySet()) {
            total += curve.settingCopper(entry.getKey().configPrice(), entry.getValue(), ProtectionProperty.PRICE_UNIT_CHUNKS);
        }
        return Math.min(linearCopper, (long) Math.floor(total));
    }

    /** Per active protection setting: how many billable chunks of the team it covers. */
    static Map<ProtectionProperty, Long> chunksPerSetting(
            Iterable<ProtectionPricing.RegionChunkCount> regions,
            Function<java.util.UUID, Region> regionLookup,
            Map<String, String> pendingOverrides
    ) {
        Map<ProtectionProperty, Long> chunks = new LinkedHashMap<>();
        for (ProtectionPricing.RegionChunkCount rc : regions) {
            if (rc.billableChunks() <= 0) {
                continue;
            }
            Region region = regionLookup.apply(rc.regionId());
            for (ProtectionProperty property : ProtectionProperty.values()) {
                if (property.configPrice() > 0 && ProtectionPricing.isActive(region, property, pendingOverrides)) {
                    chunks.merge(property, (long) rc.billableChunks(), Long::sum);
                }
            }
        }
        return chunks;
    }
}
