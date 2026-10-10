package dev.malik.lcftbhook.adapter.lightmanscurrency;

import net.neoforged.neoforge.common.NeoForge;

/**
 * Registers the listeners that only make sense with Lightman's Currency
 * installed. Called from the mod constructor behind a
 * {@code ModList.isLoaded("lightmanscurrency")} check, so none of these
 * classes is ever loaded on a server without LC.
 */
public final class LcIntegration {
    private LcIntegration() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(new LcTaxCollectorPlacementHandler());
    }
}
