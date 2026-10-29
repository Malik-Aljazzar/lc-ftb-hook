package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.data.ForceLoadMode;
import dev.malik.lcftbhook.data.Region;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The "Force-loads" row of {@link RegionSettingsScreen}: shows the mode in
 * effect, cycles through the modes the server allows, and explains them.
 * Uses the same fallback rule as the server ({@link ForceLoadMode#effective})
 * so a region whose mode was disabled shows what actually applies.
 */
final class ForceLoadModeRow {
    private ForceLoadModeRow() {
    }

    static Component label() {
        return Component.translatable("gui.lc_ftb_hook.regions.force_load_mode");
    }

    static Component value(@Nullable Region region) {
        if (region == null) {
            return Component.literal("?");
        }
        Optional<ForceLoadMode> effective = effectiveMode(region);
        if (effective.isEmpty()) {
            return Component.translatable("gui.lc_ftb_hook.force_load_mode.resting").withStyle(ChatFormatting.RED);
        }
        Component shown = name(effective.get()).copy().withStyle(ChatFormatting.AQUA);
        return effective.get() == region.forceLoadMode() ? shown
                : shown.copy().append(Component.translatable("gui.lc_ftb_hook.force_load_mode.fallback_suffix")
                        .withStyle(ChatFormatting.GRAY));
    }

    /** The next allowed mode after the chosen one, or empty if there's nothing else to pick. */
    static Optional<ForceLoadMode> next(Region region) {
        List<ForceLoadMode> enabled = ClientRegions.enabledForceLoadModes();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        int index = enabled.indexOf(region.forceLoadMode());
        ForceLoadMode next = enabled.get((index + 1) % enabled.size());
        return next == region.forceLoadMode() ? Optional.empty() : Optional.of(next);
    }

    static List<Component> tooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.lc_ftb_hook.force_load_mode.tooltip.intro"));
        for (ForceLoadMode mode : ClientRegions.enabledForceLoadModes()) {
            lines.add(name(mode).copy().withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("gui.lc_ftb_hook.force_load_mode." + mode.id() + ".description")
                            .withStyle(ChatFormatting.GRAY)));
        }
        lines.add(Component.translatable("gui.lc_ftb_hook.force_load_mode.tooltip.billing").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static Optional<ForceLoadMode> effectiveMode(Region region) {
        return region.forceLoadMode().effective(ClientRegions.enabledForceLoadModes()::contains);
    }

    private static Component name(ForceLoadMode mode) {
        return Component.translatable("gui.lc_ftb_hook.force_load_mode." + mode.id());
    }
}
