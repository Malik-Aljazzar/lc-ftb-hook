package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.network.WarEntryStatus;

import java.util.UUID;

/**
 * One opponent row in a war list (incoming/outgoing/available-target) -
 * built by {@link WarViewBuilder}, consumed by {@code WarStateSync} and
 * {@code UpkeepBreakdown}.
 */
public record WarTeamView(
        UUID teamId,
        String displayName,
        long targetBaseUpkeepCopper,
        long warCostCopper,
        WarEntryStatus status,
        boolean opponentPendingDeclareOnViewer,
        boolean blockEditProtected,
        boolean explosionProtected,
        boolean pvpProtected
) {
    public WarTeamView(
            UUID teamId,
            String displayName,
            long targetBaseUpkeepCopper,
            long warCostCopper
    ) {
        this(teamId, displayName, targetBaseUpkeepCopper, warCostCopper, WarEntryStatus.ACTIVE, false, true, true, true);
    }

    public boolean hasWarVulnerability() {
        return !blockEditProtected || !explosionProtected || !pvpProtected;
    }
}
