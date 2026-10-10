package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.config.ProtectionPriceConfig;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.port.Backends;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Configurable order in which a region's protections are disabled when
 * upkeep cannot be paid, and the order regions themselves are dismantled in:
 * index 0 of the team's stored regionOrder first. RegionListScreen displays
 * this reversed (dismantled-last at the visual top), so don't read "first"
 * here as "top of the GUI list".
 */
public final class ProtectionDismantleOrder {
    private ProtectionDismantleOrder() {
    }

    public record DismantleStep(UUID regionId, ProtectionProperty property) {
    }

    public static List<ProtectionProperty> propertyOrder() {
        List<? extends String> configured = ProtectionPriceConfig.dismantleOrder(Backends.claim().protectionFields().id());
        List<ProtectionProperty> resolved = new ArrayList<>();
        for (String id : configured) {
            ProtectionProperty property = tryResolve(id);
            if (property != null && !resolved.contains(property)) {
                resolved.add(property);
            }
        }
        for (ProtectionProperty property : ProtectionProperty.values()) {
            if (!resolved.contains(property)) {
                resolved.add(property);
            }
        }
        return resolved;
    }

    /** Outer loop: team region order (index 0 dismantled first). Inner loop: {@link #propertyOrder()}. */
    public static List<DismantleStep> fullDismantleOrder(MinecraftServer server, UUID teamId) {
        FtbHookSavedData savedData = FtbHookSavedData.get(server);
        List<ProtectionProperty> properties = propertyOrder();
        List<DismantleStep> steps = new ArrayList<>();
        for (UUID regionId : savedData.getRegionOrder(teamId)) {
            for (ProtectionProperty property : properties) {
                steps.add(new DismantleStep(regionId, property));
            }
        }
        return steps;
    }

    public static List<DismantleStep> restoreOrder(MinecraftServer server, UUID teamId) {
        List<DismantleStep> order = new ArrayList<>(fullDismantleOrder(server, teamId));
        Collections.reverse(order);
        return order;
    }

    private static ProtectionProperty tryResolve(String id) {
        try {
            return ProtectionProperty.byId(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
