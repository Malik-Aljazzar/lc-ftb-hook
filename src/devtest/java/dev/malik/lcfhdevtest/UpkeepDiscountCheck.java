package dev.malik.lcfhdevtest;

import com.mojang.logging.LogUtils;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec.Role;
import dev.malik.lcftbhook.service.ProtectionPricing;
import dev.malik.lcftbhook.service.ProtectionRollbackService;
import dev.malik.lcftbhook.service.UpkeepDeceleration;
import dev.malik.lcftbhook.service.UpkeepGating;
import dev.malik.lcftbhook.service.UpkeepSummaryService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /lcfhtest upkeep <team>}: recomputes a team's protection upkeep
 * independently and checks the real pricing chain against it, with the
 * server's current upkeepDeceleration settings. For the duration of the check
 * every region of the team gets its block edit / explosion / mob griefing
 * fields protected (restored afterwards), so there is something to price.
 * Checks: linear = sum over regions of floor(base x chunks / 10); billed =
 * linear when off, else min(linear, floor(sum over settings of price x E(N) / 10));
 * discount = linear - billed; the summary sent to clients carries the same
 * billed figure and discount.
 */
final class UpkeepDiscountCheck {
    private static final Logger LOG = LogUtils.getLogger();
    private static final List<Role> ROLES = List.of(Role.BLOCK_EDIT, Role.EXPLOSIONS, Role.MOB_GRIEFING);

    private UpkeepDiscountCheck() {
    }

    static int run(CommandSourceStack source, String teamName) {
        MinecraftServer server = source.getServer();
        UUID teamId = teamNamed(server, teamName);
        List<String> report = new ArrayList<>();
        if (teamId == null) {
            report.add("FAIL no team named " + teamName);
            return print(source, report, 1);
        }
        FtbHookSavedData data = FtbHookSavedData.get(server);
        Map<UUID, Region> originals = new LinkedHashMap<>(data.getRegions(teamId));
        int failures;
        try {
            protectAll(data, teamId, originals);
            failures = check(server, teamId, report);
        } catch (RuntimeException e) {
            report.add("FAIL aborted: " + e);
            LOG.error("[lcfhtest] upkeep check aborted", e);
            failures = 1;
        } finally {
            originals.values().forEach(region -> data.updateRegion(teamId, region));
            report.add("regions restored");
        }
        return print(source, report, failures);
    }

    private static int check(MinecraftServer server, UUID teamId, List<String> report) {
        UpkeepDeceleration curve = UpkeepDeceleration.configured();
        TeamPendingState empty = new TeamPendingState();
        Map<String, String> pricing = ProtectionRollbackService.pricingProperties(server, teamId, empty);
        TeamPendingState priced = new TeamPendingState(pricing, empty.pendingForceLoads(), empty.pendingForceUnloads(),
                empty.pendingRegionAssignments(), empty.pendingWarDeclares(), empty.pendingWarEnds());
        ProtectionPricing.ChunkCounts counts = ProtectionPricing.countBillableChunks(server, teamId, empty);

        long linear = linear(server, teamId, counts, pricing);
        Map<ProtectionProperty, Long> perSetting = ProtectionPricing.billedChunksPerSetting(server, teamId);
        double curveTotal = 0.0D;
        for (Map.Entry<ProtectionProperty, Long> e : perSetting.entrySet()) {
            curveTotal += e.getKey().configPrice() * curve.effectiveChunks(e.getValue()) / ProtectionProperty.PRICE_UNIT_CHUNKS;
            report.add("  setting " + e.getKey().id() + ": " + e.getValue() + " chunks -> billed as "
                    + String.format(java.util.Locale.ROOT, "%.1f", curve.effectiveChunks(e.getValue())));
        }
        long expectedBilled = curve.enabled() ? Math.min(linear, (long) Math.floor(curveTotal)) : linear;
        long billed = ProtectionPricing.calculateProtectionCopper(server, teamId, priced, counts);
        long discount = ProtectionPricing.protectionDiscountForPricing(server, teamId, empty, null);
        boolean charged = UpkeepGating.shouldChargeProtection(server, teamId);
        UpkeepSummaryService.UpkeepSummary summary = UpkeepSummaryService.compute(server, teamId);

        report.add("curve " + curve + ", protection charged=" + charged);
        report.add("linear=" + linear + " billed=" + billed + " expectedBilled=" + expectedBilled + " discount=" + discount
                + " summaryProtection=" + summary.protectionCurrentCopper() + " summaryDiscount=" + summary.protectionDiscountCurrentCopper());
        int failures = 0;
        failures += expect(report, billed == expectedBilled, "billed matches the independent recomputation");
        failures += expect(report, billed <= linear, "never more than the linear price");
        failures += expect(report, !curve.enabled() ? billed == linear : true, "off = linear");
        failures += expect(report, discount == (charged ? linear - billed : 0), "discount = linear - billed");
        failures += expect(report, summary.protectionDiscountCurrentCopper() == discount, "summary carries the discount");
        failures += expect(report, !charged || summary.protectionCurrentCopper() == billed, "summary protection = billed");
        return failures;
    }

    private static long linear(MinecraftServer server, UUID teamId, ProtectionPricing.ChunkCounts counts, Map<String, String> pricing) {
        FtbHookSavedData data = FtbHookSavedData.get(server);
        long total = 0L;
        for (ProtectionPricing.RegionChunkCount rc : counts.perRegion().values()) {
            Region region = data.getRegion(teamId, rc.regionId());
            if (region != null && rc.billableChunks() > 0) {
                total += ProtectionPricing.regionBasePrice(region, pricing) * rc.billableChunks() / ProtectionProperty.PRICE_UNIT_CHUNKS;
            }
        }
        return total;
    }

    private static void protectAll(FtbHookSavedData data, UUID teamId, Map<UUID, Region> originals) {
        for (Region original : originals.values()) {
            Region region = original;
            for (Role role : ROLES) {
                for (ProtectionProperty property : ProtectionProperty.withRole(role)) {
                    region = region.withProperty(property.id(), property.isPrivacyMode() ? "TEAM" : "false");
                }
            }
            data.updateRegion(teamId, region);
        }
    }

    private static UUID teamNamed(MinecraftServer server, String name) {
        ClaimPort claims = Backends.claim();
        for (UUID team : claims.allTeams(server)) {
            if (claims.teamDisplayName(server, team).equalsIgnoreCase(name) && claims.claimedChunkCount(server, team) > 0) {
                return team;
            }
        }
        return null;
    }

    private static int expect(List<String> report, boolean ok, String what) {
        report.add((ok ? "PASS " : "FAIL ") + what);
        return ok ? 0 : 1;
    }

    private static int print(CommandSourceStack source, List<String> report, int failures) {
        report.add(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        for (String line : report) {
            LOG.info("[lcfhtest] {}", line);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return failures == 0 ? 1 : 0;
    }
}
