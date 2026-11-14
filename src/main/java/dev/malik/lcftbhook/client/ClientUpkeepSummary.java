package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.service.UpkeepSummaryService.UpkeepSummary;

/** Client-side cache of the viewer's own team's upkeep totals - see {@link UpkeepSummary}. */
public final class ClientUpkeepSummary {
    private static UpkeepSummary summary = new UpkeepSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    private ClientUpkeepSummary() {
    }

    public static void update(UpkeepSummary updated) {
        summary = updated;
    }

    public static UpkeepSummary get() {
        return summary;
    }
}
