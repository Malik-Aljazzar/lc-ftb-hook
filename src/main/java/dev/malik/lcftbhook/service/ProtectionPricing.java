package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.RegionPropertyKey;
import dev.malik.lcftbhook.data.TeamPendingState;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Region-based protection billing. Every region (including the auto-created
 * Default) prices its own non-minimum protection properties per chunk it
 * contains. Only when freeChunksExemptFromUpkeep is on, the team-wide free
 * chunk allowance is consumed against the highest price-per-chunk region(s)
 * first; otherwise every claimed chunk is billable.
 */
public final class ProtectionPricing {
    private ProtectionPricing() {
    }

    public record RegionChunkCount(UUID regionId, int totalChunks, int billableChunks) {
    }

    public record ChunkCounts(int totalChunks, Map<UUID, RegionChunkCount> perRegion) {
        public static final ChunkCounts EMPTY = new ChunkCounts(0, Map.of());
    }

    public static ChunkCounts countBillableChunks(MinecraftServer server, UUID teamId) {
        return countBillableChunks(server, teamId, new TeamPendingState());
    }

    public static ChunkCounts countBillableChunks(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);

        Map<UUID, Integer> raw = new LinkedHashMap<>();
        int total = 0;
        for (ClaimPort.ChunkKey chunk : Backends.claim().claimedChunks(server, teamId)) {
            total++;
            String key = ChunkPosKey.encode(chunk);
            UUID regionId = effectiveRegion(savedData, teamId, key, pendingState);
            raw.merge(regionId, 1, Integer::sum);
        }

        List<UUID> byPriceDesc = new ArrayList<>(raw.keySet());
        byPriceDesc.sort(Comparator.comparingLong((UUID id) ->
                regionBasePrice(savedData, teamId, id, pendingState)).reversed());

        int remainingAllowance = FreeChunkAllowance.upkeepAllowance();
        Map<UUID, RegionChunkCount> perRegion = new LinkedHashMap<>();
        for (UUID regionId : byPriceDesc) {
            int count = raw.get(regionId);
            int consumed = Math.min(remainingAllowance, count);
            remainingAllowance -= consumed;
            int billable = count - consumed;
            perRegion.put(regionId, new RegionChunkCount(regionId, count, billable));
        }
        return new ChunkCounts(total, perRegion);
    }

    /** The region a chunk is billed/attributed to right now - its pending reassignment target if one is queued, otherwise its live assignment. */
    public static UUID effectiveRegion(
            FtbHookSavedData savedData,
            UUID teamId,
            String chunkKey,
            TeamPendingState pendingState
    ) {
        if (pendingState.isPendingRegionAssignment(chunkKey)) {
            return pendingState.pendingRegionAssignmentTarget(chunkKey);
        }
        return savedData.getChunkRegion(teamId, chunkKey);
    }

    /** Sum of config prices for every non-minimum property on this region, given a pending-overlay map. */
    public static long regionBasePrice(Region region, Map<String, String> pendingOverrides) {
        long base = 0L;
        for (ProtectionProperty property : ProtectionProperty.values()) {
            if (isActive(region, property, pendingOverrides)) {
                base += property.configPrice();
            }
        }
        return base;
    }

    /** Whether this property is above its minimum on the region - the pending-overlay value if one is queued. */
    static boolean isActive(Region region, ProtectionProperty property, Map<String, String> pendingOverrides) {
        String key = RegionPropertyKey.encode(region.id(), property.id());
        String value = pendingOverrides.containsKey(key) ? pendingOverrides.get(key) : region.propertyValue(property);
        return !property.isAtMinimum(value);
    }

    private static long regionBasePrice(FtbHookSavedData savedData, UUID teamId, UUID regionId, TeamPendingState pendingState) {
        Region region = savedData.getRegion(teamId, regionId);
        if (region == null) {
            region = Region.createDefault();
        }
        return regionBasePrice(region, pendingState.pendingProperties());
    }

    /**
     * The protection upkeep actually billed: the linear per-region price,
     * lowered by the upkeep discount for large chunk amounts when
     * upkeepDeceleration is on (see {@link DeceleratedProtectionPricing}).
     * Every billing, pending-vs-immediate and war cost decision goes through
     * here, so they all see the same discounted figure.
     */
    public static long calculateProtectionCopper(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            ChunkCounts counts
    ) {
        long linear = linearProtectionCopper(server, teamId, pendingState, counts);
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        return DeceleratedProtectionPricing.billedCopper(counts.perRegion().values(),
                regionId -> regionOrDefault(savedData, teamId, regionId), pendingState.pendingProperties(),
                linear, UpkeepDeceleration.configured());
    }

    /** What the upkeep discount for large chunk amounts saves: linear price minus billed price (0 when off). */
    public static long protectionDiscountCopper(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            ChunkCounts counts
    ) {
        return linearProtectionCopper(server, teamId, pendingState, counts)
                - calculateProtectionCopper(server, teamId, pendingState, counts);
    }

    /**
     * The discount for the same pricing as {@link #calculateTotalUpkeepCopper}
     * (live: only properties that are actually billed; with
     * {@code pricingOverrides}: that overlay) - 0 when protection isn't charged.
     */
    public static long protectionDiscountForPricing(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            @javax.annotation.Nullable Map<String, String> pricingOverrides
    ) {
        if (!UpkeepGating.shouldChargeProtection(server, teamId)) {
            return 0L;
        }
        Map<String, String> pricing = pricingOverrides != null
                ? pricingOverrides
                : ProtectionRollbackService.pricingProperties(server, teamId, pendingState);
        return protectionDiscountCopper(server, teamId, withPricing(pendingState, pricing),
                countBillableChunks(server, teamId, pendingState));
    }

    /**
     * Per active, priced protection setting: how many billable chunks of the
     * team it covers - exactly the counts live billing feeds into the upkeep
     * discount curve (for {@code /lc_ftb_hook upkeep_discount}).
     */
    public static Map<ProtectionProperty, Long> billedChunksPerSetting(MinecraftServer server, UUID teamId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(teamId);
        Map<String, String> pricing = ProtectionRollbackService.pricingProperties(server, teamId, pendingState);
        ChunkCounts counts = countBillableChunks(server, teamId, pendingState);
        return DeceleratedProtectionPricing.chunksPerSetting(counts.perRegion().values(),
                regionId -> regionOrDefault(savedData, teamId, regionId), pricing);
    }

    private static TeamPendingState withPricing(TeamPendingState pendingState, Map<String, String> pricing) {
        return new TeamPendingState(pricing,
                pendingState.pendingForceLoads(), pendingState.pendingForceUnloads(),
                pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds());
    }

    private static Region regionOrDefault(FtbHookSavedData savedData, UUID teamId, UUID regionId) {
        Region region = savedData.getRegion(teamId, regionId);
        return region != null ? region : Region.createDefault();
    }

    /** The plain per-region price: sum of each region's active property prices x its billable chunks. */
    private static long linearProtectionCopper(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            ChunkCounts counts
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        long copper = 0L;
        for (RegionChunkCount rc : counts.perRegion().values()) {
            if (rc.billableChunks() <= 0) {
                continue;
            }
            long base = regionBasePrice(savedData, teamId, rc.regionId(), pendingState);
            if (base > 0) {
                // base is priced per ProtectionProperty.PRICE_UNIT_CHUNKS
                // chunks, not per single chunk - floor-divide AFTER
                // multiplying by the actual chunk count, never before (that
                // would round every sub-unit property to 0 regardless of
                // chunk count, defeating the point of the smaller unit).
                copper += (base * rc.billableChunks()) / ProtectionProperty.PRICE_UNIT_CHUNKS;
            }
        }
        return copper;
    }

    public static long calculateForceLoadCopper(int forceLoadCount) {
        long forceLoadPriceTenths = dev.malik.lcftbhook.config.LCFtbHookConfig.SERVER.forceLoadUpkeepPrice.get();
        if (forceLoadPriceTenths > 0 && forceLoadCount > 0) {
            // Tenths of a copper (see LCFtbHookConfig's [general] note) - same
            // "multiply first, floor-divide once at the end" shape as
            // calculateProtectionCopper above, not per-chunk floor then sum.
            return Math.floorDiv(forceLoadPriceTenths * forceLoadCount, 10L);
        }
        return 0L;
    }

    public static long calculateTotalUpkeepCopper(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        ChunkCounts counts = countBillableChunks(server, teamId, pendingState);
        long protectionCopper = UpkeepGating.shouldChargeProtection(server, teamId)
                ? calculateProtectionCopper(
                        server,
                        teamId,
                        new TeamPendingState(ProtectionRollbackService.pricingProperties(server, teamId, pendingState),
                                pendingState.pendingForceLoads(), pendingState.pendingForceUnloads(),
                                pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds()),
                        counts
                )
                : 0L;
        return protectionCopper + ForceLoadBilling.chargeableCopper(server, teamId, pendingState);
    }

    public static long calculateTotalUpkeepCopper(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            Map<String, String> pricingOverrides
    ) {
        ChunkCounts counts = countBillableChunks(server, teamId, pendingState);
        TeamPendingState overlay = new TeamPendingState(pricingOverrides,
                pendingState.pendingForceLoads(), pendingState.pendingForceUnloads(),
                pendingState.pendingRegionAssignments(), pendingState.pendingWarDeclares(), pendingState.pendingWarEnds());
        long protectionCopper = UpkeepGating.shouldChargeProtection(server, teamId)
                ? calculateProtectionCopper(server, teamId, overlay, counts)
                : 0L;
        return protectionCopper + ForceLoadBilling.chargeableCopper(server, teamId, pendingState);
    }

    public static int countEffectiveForceLoads(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        int count = Backends.claim().forceLoadedChunks(server, teamId).size();
        count += pendingState.pendingForceLoads().size();
        count -= pendingState.pendingForceUnloads().size();
        return Math.max(count, 0);
    }
}
