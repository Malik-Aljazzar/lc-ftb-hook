package dev.malik.lcftbhook.adapter.numismatics;

import dev.malik.lcftbhook.util.BuiltInResourcePacks;
import net.neoforged.bus.api.IEventBus;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Coin amounts are written with the coins' item icons instead of their names
 * ("3[cog] 2[spur]"). Each coin is a private-use character that a built-in
 * resource pack ({@code resourcepacks/numismatics_coin_icons}) adds to the
 * default font, pointing at Numismatics' own coin textures - so the icons
 * work in any text, including chat and strings formatted on the server. The
 * pack is only added with Numismatics installed, since its textures are
 * borrowed from there.
 */
public final class NumismaticsCoinIcons {
    private static final String PACK_NAME = "numismatics_coin_icons";

    /** Coin translation key -> its glyph; must match the pack's {@code font/default.json}. */
    private static final Map<String, String> GLYPHS = Map.of(
            "item.numismatics.spur", "\uE400",
            "item.numismatics.bevel", "\uE401",
            "item.numismatics.sprocket", "\uE402",
            "item.numismatics.cog", "\uE403",
            "item.numismatics.crown", "\uE404",
            "item.numismatics.sun", "\uE405"
    );

    private NumismaticsCoinIcons() {
    }

    /** Null for a coin without an icon (a future Numismatics coin) - callers fall back to its name. */
    @Nullable
    static String glyph(String translationKey) {
        return GLYPHS.get(translationKey);
    }

    public static void register(IEventBus modEventBus) {
        BuiltInResourcePacks.register(modEventBus, PACK_NAME, "Numismatics coin icons");
    }
}
