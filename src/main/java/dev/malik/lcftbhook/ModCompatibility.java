package dev.malik.lcftbhook;

import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates that required companion mods are present, and warns (without
 * blocking startup) when their version differs from what this hook was
 * built against. Companion mods only ever get a patch/hotfix bump between
 * our releases, which does not break the small surface of their API this
 * hook actually uses - hard-failing on every such bump (as this used to
 * do) forced a hook update for every unrelated point release of FTB
 * Chunks or Lightman's Currency, see
 * https://github.com/ManOfKott/lc-ftb-hook/issues/1
 */
public final class ModCompatibility {
    /** Must match {@code ftb_chunks_version} in gradle.properties. */
    public static final String REQUIRED_FTB_CHUNKS_VERSION = "2101.1.22";
    /** Must match {@code lightmanscurrency_version} in gradle.properties. */
    public static final String REQUIRED_LIGHTMANS_CURRENCY_VERSION = "1.21-2.3.0.5";

    private ModCompatibility() {
    }

    public static void validateOrThrow() {
        List<String> missing = new ArrayList<>();
        List<String> mismatched = new ArrayList<>();

        requireAnyOf(missing, "a claim mod", "ftbchunks", "openpartiesandclaims");
        requireAnyOf(missing, "a currency mod", "lightmanscurrency", "numismatics");
        if (ModList.get().isLoaded("ftbchunks")) {
            checkVersion("ftbchunks", "FTB Chunks", REQUIRED_FTB_CHUNKS_VERSION, missing, mismatched);
        }
        if (ModList.get().isLoaded("lightmanscurrency")) {
            checkVersion("lightmanscurrency", "Lightman's Currency", REQUIRED_LIGHTMANS_CURRENCY_VERSION, missing, mismatched);
        }
        logOptionalVersion("numismatics", "Numismatics");
        logOptionalVersion("openpartiesandclaims", "Open Parties and Claims");
        logOptionalVersion("xaeroworldmap", "Xaero's World Map");

        for (String warning : mismatched) {
            LCFtbHook.LOGGER.warn(warning);
        }

        if (missing.isEmpty()) {
            return;
        }

        for (String error : missing) {
            LCFtbHook.LOGGER.error(error);
        }

        throw new IllegalStateException(
                "LC FTB Hook cannot load: " + String.join(" | ", missing)
        );
    }

    /**
     * Purely informational, never blocks or even warns - Xaero's World Map
     * integration is entirely optional (see {@code lc_ftb_hook_xaero.mixins.json},
     * {@code required: false}, and the try/catch guards around every actual
     * touchpoint in {@code client.xaero}/{@code mixin.client.xaero}) and is
     * built to degrade gracefully on its own if a newer version changes
     * something underneath it. This just puts the detected version in the
     * log so a future bug report against Xaero integration has it on hand.
     */
    private static void logOptionalVersion(String modId, String displayName) {
        ModList.get().getModContainerById(modId).ifPresent(container ->
                LCFtbHook.LOGGER.info("{} detected: version {}", displayName, container.getModInfo().getVersion()));
    }

    /** mods.toml can't express "at least one of" - each alternative is optional there, so it's checked here. */
    private static void requireAnyOf(List<String> missingOut, String what, String... modIds) {
        for (String modId : modIds) {
            if (ModList.get().isLoaded(modId)) {
                return;
            }
        }
        missingOut.add("No " + what + " installed - need one of: " + String.join(", ", modIds) + ".");
    }

    private static void checkVersion(
            String modId,
            String displayName,
            String requiredVersion,
            List<String> missingOut,
            List<String> mismatchOut
    ) {
        Optional<String> installed = ModList.get()
                .getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
        if (installed.isEmpty()) {
            missingOut.add(displayName + " (" + modId + ") is missing.");
            return;
        }

        if (!requiredVersion.equals(installed.get())) {
            mismatchOut.add(
                    displayName + " version differs from what LC FTB Hook was built against "
                            + "(built for " + requiredVersion + ", found " + installed.get() + "). "
                            + "This is usually fine for a patch-level update; please report an issue "
                            + "if something actually breaks."
            );
        }
    }
}
