package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec.Role;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.UUID;

/**
 * Live protections on a war opponent, aggregated across every one of their
 * regions. A team is vulnerable in a given aspect the moment ANY of its
 * regions leaves ANY protection with that role (of the active claim mod)
 * unprotected, even if others are protected.
 */
public record WarTargetProtections(
        boolean blockEditProtected,
        boolean explosionProtected,
        boolean pvpProtected
) {
    public boolean hasWarVulnerability() {
        return !blockEditProtected || !explosionProtected || !pvpProtected;
    }

    public static WarTargetProtections live(MinecraftServer server, UUID teamId) {
        Collection<Region> regions = FtbHookSavedData.get(server).getRegions(teamId).values();
        return new WarTargetProtections(
                allProtected(regions, Role.BLOCK_EDIT),
                allProtected(regions, Role.EXPLOSIONS),
                allProtected(regions, Role.PVP)
        );
    }

    private static boolean allProtected(Collection<Region> regions, Role role) {
        for (ProtectionProperty property : ProtectionProperty.withRole(role)) {
            for (Region region : regions) {
                if (region.isAtMinimum(property)) {
                    return false;
                }
            }
        }
        return true;
    }
}
