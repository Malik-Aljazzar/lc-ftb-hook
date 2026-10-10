package dev.malik.lcftbhook.client;

import dev.malik.lcftbhook.client.ftb.FtbClientRefresh;
import dev.malik.lcftbhook.client.gui.LcScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/**
 * Backend-neutral entry point for "synced data changed, redraw what shows
 * it": this mod's own screens are notified directly; FTB Chunks' map and FTB's
 * own screens only when FTB Chunks is installed.
 */
public final class ClientUiRefresh {
    private ClientUiRefresh() {
    }

    public static void redrawMap() {
        if (ftbChunksLoaded()) {
            FtbClientRefresh.redrawMap();
        }
    }

    public static void refreshOpenScreens() {
        notifyOwnScreen();
        if (ftbChunksLoaded()) {
            FtbClientRefresh.refreshOpenScreens();
        }
    }

    public static void refreshTeamScreen() {
        if (ftbChunksLoaded()) {
            FtbClientRefresh.refreshTeamScreen();
        }
    }

    public static void refreshRegionList() {
        notifyOwnScreen();
    }

    public static void refreshWarState() {
        notifyOwnScreen();
    }

    private static void notifyOwnScreen() {
        if (Minecraft.getInstance().screen instanceof LcScreen screen) {
            screen.onDataChanged();
        }
    }

    private static boolean ftbChunksLoaded() {
        return ModList.get().isLoaded("ftbchunks");
    }
}
