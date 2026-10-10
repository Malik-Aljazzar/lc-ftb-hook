package dev.malik.lcftbhook.client.ftb;

import dev.ftb.mods.ftbchunks.client.map.MapManager;

/**
 * The FTB-specific halves of {@link dev.malik.lcftbhook.client.ClientUiRefresh}:
 * redrawing FTB Chunks' map and FTB's own screens. Only ever loaded when FTB
 * Chunks is installed.
 */
public final class FtbClientRefresh {
    private FtbClientRefresh() {
    }

    public static void redrawMap() {
        MapManager.getInstance().ifPresent(manager -> manager.updateAllRegions(false));
    }

    public static void refreshOpenScreens() {
        PendingStateUiRefresh.refreshOpenScreens();
    }

    public static void refreshTeamScreen() {
        TeamUiRefresh.refreshMyTeamScreenIfOpen();
    }
}
