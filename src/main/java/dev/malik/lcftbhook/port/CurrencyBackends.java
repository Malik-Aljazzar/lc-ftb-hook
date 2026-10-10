package dev.malik.lcftbhook.port;

import dev.malik.lcftbhook.adapter.lightmanscurrency.LightmansCurrencyPort;
import dev.malik.lcftbhook.adapter.numismatics.NumismaticsCurrencyPort;
import dev.malik.lcftbhook.port.currency.CurrencyPort;

import java.util.List;

/**
 * {@link CurrencyPort} candidates in priority order - Lightman's Currency
 * first (this mod's original, most-tested backend), then Numismatics. See
 * {@link BackendCandidate}'s javadoc for how a future third currency backend
 * would be added here.
 * <p>
 * Factories are lambdas, not constructor references ({@code Foo::new}), on
 * purpose: a constructor reference is resolved as soon as this list is built,
 * which would load every adapter class - and with it the classes of mods that
 * may not be installed. A lambda only touches the adapter when invoked.
 */
final class CurrencyBackends {
    static final List<BackendCandidate<CurrencyPort>> CANDIDATES = List.of(
            new BackendCandidate<>("LIGHTMANS_CURRENCY", "lightmanscurrency", () -> new LightmansCurrencyPort()),
            new BackendCandidate<>("NUMISMATICS", "numismatics", () -> new NumismaticsCurrencyPort())
    );

    private CurrencyBackends() {
    }
}
