package dev.malik.lcftbhook.client.ftb;

import dev.ftb.mods.ftblibrary.math.XZ;

/** Duck interface for alt-drag chunk type toggling; must live outside mixin packages. */
public interface ChunkScreenPanelAltToggleAccess {
    void lcFtbHook$selectForAltToggle(XZ chunkPos);

    void lcFtbHook$releaseAltToggleSelection();
}
