package dev.malik.lcftbhook.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes a config price the way a server admin thinks about it - in the coins
 * of both supported currency mods: "= 80 copper; Lightman's Currency: 8 iron,
 * Numismatics: 1 cog 1 sprocket". Config comments are built before any
 * currency mod is queried, so the coin ladders are each mod's default ones
 * (a Lightman's Currency server may have customized its coin chain).
 */
final class CoinExamples {
    private record Coin(String name, long value) {
    }

    private static final List<Coin> LIGHTMANS = List.of(new Coin("netherite", 100_000), new Coin("diamond", 10_000),
            new Coin("emerald", 1_000), new Coin("gold", 100), new Coin("iron", 10), new Coin("copper", 1));
    private static final List<Coin> NUMISMATICS = List.of(new Coin("sun", 4_096), new Coin("crown", 512),
            new Coin("cog", 64), new Coin("sprocket", 16), new Coin("bevel", 8), new Coin("spur", 1));

    private CoinExamples() {
    }

    /** A value in tenths of a copper (the config's unit), e.g. 800 -> "80 copper; Lightman's Currency: 8 iron, Numismatics: 1 cog 1 sprocket". */
    static String ofTenths(long tenths) {
        return ofTenths(tenths, "");
    }

    /** Same, with a unit such as " per chunk" right after the copper amount. */
    static String ofTenths(long tenths, String unit) {
        long copper = tenths / 10;
        if (tenths % 10 != 0 || copper == 0) {
            return (tenths / 10.0) + " copper" + unit + " (Lightman's copper coins / Numismatics spurs)";
        }
        return copper + " copper" + unit + "; Lightman's Currency: " + split(copper, LIGHTMANS) + ", Numismatics: " + split(copper, NUMISMATICS);
    }

    private static String split(long amount, List<Coin> coins) {
        List<String> parts = new ArrayList<>();
        long remaining = amount;
        for (Coin coin : coins) {
            long count = remaining / coin.value();
            if (count > 0) {
                parts.add(count + " " + coin.name());
                remaining -= count * coin.value();
            }
        }
        return String.join(" ", parts);
    }
}
