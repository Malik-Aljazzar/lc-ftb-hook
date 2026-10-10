package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.data.RegionPropertyKey;
import dev.malik.lcftbhook.data.TeamPendingState;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Suspends and restores a region's protection values via the shared
 * pendingProperties queue. Region values ARE the live/enforced state (there
 * is no separate external property system to fight anymore), so "suspend"
 * means writing the minimum value directly into the region and remembering
 * the value to restore later; "restore" writes it back.
 */
public final class ProtectionRollbackService {
    private ProtectionRollbackService() {
    }

    public static boolean isLiveAtMinimum(FtbHookSavedData savedData, UUID teamId, UUID regionId, ProtectionProperty property) {
        Region region = savedData.getRegion(teamId, regionId);
        return region == null || region.isAtMinimum(property);
    }

    public static boolean isLiveProtectionBillable(FtbHookSavedData savedData, UUID teamId, UUID regionId, ProtectionProperty property) {
        return !isLiveAtMinimum(savedData, teamId, regionId, property);
    }

    public static boolean isDismantled(
            FtbHookSavedData savedData,
            UUID teamId,
            UUID regionId,
            ProtectionProperty property,
            TeamPendingState pendingState
    ) {
        String key = RegionPropertyKey.encode(regionId, property.id());
        String pendingValue = pendingState.pendingProperties().get(key);
        if (pendingValue == null) {
            return false;
        }
        // A genuine system-forced dismantle (suspendProtection) stores the
        // value to RESTORE once affordable, which is never the minimum tier.
        // If the pending value IS minimum, this is a normal user-queued
        // change that reduces/disables the protection, not a dismantle.
        if (property.isAtMinimum(pendingValue)) {
            return false;
        }
        return isLiveAtMinimum(savedData, teamId, regionId, property);
    }

    public static boolean hasPendingApply(
            FtbHookSavedData savedData,
            UUID teamId,
            UUID regionId,
            ProtectionProperty property,
            TeamPendingState pendingState
    ) {
        String key = RegionPropertyKey.encode(regionId, property.id());
        String serialized = pendingState.pendingProperties().get(key);
        if (serialized == null) {
            return false;
        }
        Region region = savedData.getRegion(teamId, regionId);
        String liveValue = region != null ? region.propertyValue(property) : property.minimumSerialized();
        return !serialized.equals(liveValue);
    }

    /**
     * Pending property entries that should count toward the currently-billed
     * price: excludes entries whose region is already at minimum (those are
     * dismantle-restore targets, unbilled until restored).
     */
    public static Map<String, String> pricingProperties(MinecraftServer server, UUID teamId, TeamPendingState pendingState) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        Map<String, String> pricing = new HashMap<>();
        for (var entry : pendingState.pendingProperties().entrySet()) {
            UUID regionId = RegionPropertyKey.regionId(entry.getKey());
            String propertyId = RegionPropertyKey.propertyId(entry.getKey());
            ProtectionProperty property;
            try {
                property = ProtectionProperty.byId(propertyId);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!isLiveAtMinimum(savedData, teamId, regionId, property)) {
                pricing.put(entry.getKey(), entry.getValue());
            }
        }
        return pricing;
    }

    public static Map<String, String> pricingWithAppliedPending(
            MinecraftServer server,
            UUID teamId,
            TeamPendingState pendingState,
            UUID regionId,
            ProtectionProperty property
    ) {
        Map<String, String> pricing = new HashMap<>(pricingProperties(server, teamId, pendingState));
        String key = RegionPropertyKey.encode(regionId, property.id());
        String serialized = pendingState.pendingProperties().get(key);
        if (serialized != null) {
            pricing.put(key, serialized);
        }
        return pricing;
    }

    public static TeamPendingState suspendProtection(
            MinecraftServer server,
            UUID teamId,
            UUID regionId,
            ProtectionProperty property,
            TeamPendingState pendingState
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        String key = RegionPropertyKey.encode(regionId, property.id());
        TeamPendingState updated = pendingState;
        Region region = savedData.getRegion(teamId, regionId);
        if (region == null) {
            region = Region.createDefault();
        }
        if (!pendingState.hasPendingProperty(key)) {
            String restoreValue = region.propertyValue(property);
            updated = pendingState.withPendingProperty(key, restoreValue);
        }
        savedData.updateRegion(teamId, region.withoutProperty(property.id()));
        return updated;
    }

    public static TeamPendingState restoreProtection(
            MinecraftServer server,
            UUID teamId,
            UUID regionId,
            ProtectionProperty property,
            TeamPendingState pendingState
    ) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        String key = RegionPropertyKey.encode(regionId, property.id());
        String serialized = pendingState.pendingProperties().get(key);
        if (serialized == null) {
            return pendingState;
        }
        Region region = savedData.getRegion(teamId, regionId);
        if (region == null) {
            region = Region.createDefault();
        }
        Region updatedRegion = property.isAtMinimum(serialized)
                ? region.withoutProperty(property.id())
                : region.withProperty(property.id(), serialized);
        savedData.updateRegion(teamId, updatedRegion);
        return pendingState.withoutPendingProperty(key);
    }
}
