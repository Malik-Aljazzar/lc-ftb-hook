package dev.malik.lcftbhook.adapter.numismatics;

import dev.malik.lcftbhook.LCFtbHook;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Numismatics' coin ladder (Spur 1, Bevel 8, Sprocket 16, Cog 64, Crown 512,
 * Sun 4096 at the time of writing), read from its {@code Coin} enum at
 * runtime. Reflection on purpose: {@code Coin} implements an interface from
 * Create itself, so referencing it directly would put Create on this mod's
 * compile classpath for nothing more than a value table. Reading the real
 * enum also keeps up if Numismatics ever changes its coins.
 */
final class NumismaticsCoins {
    private static final String COIN_CLASS = "dev.ithundxr.createnumismatics.content.backend.Coin";
    private static volatile List<CoinInfo> cached;

    record CoinInfo(int value, String translationKey) {
        /**
         * The coin's item icon - kept white so the icon shows its own colors
         * whatever style the surrounding text has - or its name if it has none.
         */
        Component label() {
            String icon = NumismaticsCoinIcons.glyph(translationKey);
            return icon != null
                    ? Component.literal(icon).withStyle(style -> style.withColor(0xFFFFFF))
                    : Component.translatable(translationKey);
        }

        boolean hasIcon() {
            return NumismaticsCoinIcons.glyph(translationKey) != null;
        }
    }

    private NumismaticsCoins() {
    }

    /** Highest value first. */
    static List<CoinInfo> highToLow() {
        List<CoinInfo> coins = cached;
        if (coins == null) {
            coins = load();
            cached = coins;
        }
        return coins;
    }

    /** "2[sun] 3[cog] 1[spur]" with coin icons - greedy, highest coin first, zero amounts omitted. */
    static Component format(long spurs) {
        MutableComponent text = Component.empty();
        long remaining = spurs;
        boolean first = true;
        for (CoinInfo coin : highToLow()) {
            long count = remaining / coin.value();
            if (count <= 0) {
                continue;
            }
            remaining -= count * coin.value();
            if (!first) {
                text.append(" ");
            }
            // "3[cog]" with an icon, "3 Cog" for a coin without one.
            text.append(Component.literal(coin.hasIcon() ? String.valueOf(count) : count + " ")).append(coin.label());
            first = false;
        }
        return text;
    }

    private static List<CoinInfo> load() {
        List<CoinInfo> coins = new ArrayList<>();
        try {
            Class<?> coinClass = Class.forName(COIN_CLASS);
            for (Object constant : coinClass.getEnumConstants()) {
                int value = coinClass.getField("value").getInt(constant);
                String key = (String) coinClass.getMethod("getTranslationKey").invoke(constant);
                coins.add(new CoinInfo(value, key));
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            LCFtbHook.LOGGER.error("Could not read Numismatics coin values - falling back to plain spurs", e);
            coins.clear();
            coins.add(new CoinInfo(1, "item.numismatics.spur"));
        }
        coins.sort(Comparator.comparingInt(CoinInfo::value).reversed());
        return List.copyOf(coins);
    }
}
