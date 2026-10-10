package dev.malik.lcftbhook.client.xaero;

import dev.malik.lcftbhook.client.ChunkActions;
import dev.malik.lcftbhook.client.ClientClaims;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.ClaimedChunkInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.gui.GuiMap;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Appends the region/marketplace actions ({@link ChunkActions}) for the
 * selected chunks to Xaero World Map's right-click menu. A plain class, NOT
 * part of {@code GuiMapMarketplaceMixin}: lambdas declared in a mixin get
 * relocated into {@code GuiMap} and intermittently broke the merged class
 * (see {@link LcRightClickOption}); the mixin only calls {@link #populate}
 * via reflection.
 */
public final class XaeroMarketplaceMenu {
    private XaeroMarketplaceMenu() {
    }

    public static void populate(GuiMap self, MapTileSelection selection, ArrayList<RightClickOption> options) {
        ResourceKey<Level> dimension = XaeroMapDimension.current();
        if (dimension == null) {
            return;
        }
        List<String> chunkKeys = new ArrayList<>();
        List<UUID> teams = new ArrayList<>();
        for (int cx = selection.getLeft(); cx <= selection.getRight(); cx++) {
            for (int cz = selection.getTop(); cz <= selection.getBottom(); cz++) {
                ClaimedChunkInfo claim = ClientClaims.chunk(dimension, cx, cz);
                if (claim != null) {
                    chunkKeys.add(ChunkPosKey.encode(dimension.location(), cx, cz));
                    teams.add(claim.teamId());
                }
            }
        }
        for (ChunkActions.ChunkAction action : ChunkActions.forSelection(chunkKeys, teams)) {
            options.add(new LcRightClickOption(action.label().getString(), options.size(), self, action.label(), action.run()));
        }
    }
}
