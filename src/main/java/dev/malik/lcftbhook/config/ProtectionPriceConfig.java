package dev.malik.lcftbhook.config;

import dev.malik.lcftbhook.port.protection.ProtectionFieldSet;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The protection prices and dismantle order, in their own server config file
 * with one section per installed claim backend ([ftb], [opac]) - each backend
 * has its own set of protection fields, so each gets its own prices and order.
 */
public final class ProtectionPriceConfig {
    public static final String FILE_NAME = "lc_ftb_hook-protection-prices.toml";

    static final String DISMANTLE_ORDER = "dismantleOrder";

    private static final Map<String, Map<String, ModConfigSpec.LongValue>> PRICES = new LinkedHashMap<>();
    private static final Map<String, ModConfigSpec.ConfigValue<List<? extends String>>> DISMANTLE_ORDERS = new LinkedHashMap<>();
    private static ModConfigSpec spec;

    private ProtectionPriceConfig() {
    }

    /** Called once from the mod constructor with the field sets of every installed claim backend. */
    public static ModConfigSpec build(List<ProtectionFieldSet> sets) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "Protection prices per claim backend, added to upkeep PER CHUNK that has the protection active.",
                "Denominated in tenths of a copper per chunk: total for a region =",
                "floor(price * billableChunksWithThisProtectionActive / 10) copper.",
                "Copper = the active currency mod's smallest coin: a Lightman's Currency copper coin",
                "(iron = 10, gold = 100 copper) or a Numismatics spur (bevel = 8, sprocket = 16, cog = 64 spurs).",
                "Every default below is written out in both currencies.",
                "Only the section of the claim mod actually in use applies."
        );
        for (ProtectionFieldSet set : sets) {
            builder.comment("Protections of " + set.displayName()).push(set.id());
            Map<String, ModConfigSpec.LongValue> values = new LinkedHashMap<>();
            for (ProtectionFieldSpec field : set.fields()) {
                values.put(field.id(), builder
                        .comment(field.description(),
                                "Default: " + field.defaultPrice() + " (= " + CoinExamples.ofTenths(field.defaultPrice(), " per chunk") + ").")
                        .defineInRange(field.id(), field.defaultPrice(), 0L, Long.MAX_VALUE));
            }
            PRICES.put(set.id(), values);
            DISMANTLE_ORDERS.put(set.id(), defineDismantleOrder(builder, set));
            builder.pop();
        }
        spec = builder.build();
        return spec;
    }

    private static ModConfigSpec.ConfigValue<List<? extends String>> defineDismantleOrder(ModConfigSpec.Builder builder, ProtectionFieldSet set) {
        return builder.comment(
                "Which protections are switched off first when a team can't pay its upkeep.",
                "",
                "At every upkeep period a team pays for the protections its regions have active. If its",
                "account can't cover the bill, protections are switched off one step at a time until the",
                "rest is affordable again:",
                "  1. Regions are handled in the team's region-list order - the TOP region loses its",
                "     protections first (teams drag their least important region to the top).",
                "  2. Inside a region, protections are switched off in the order of this list - the FIRST",
                "     entry is dropped first, the LAST one is kept the longest.",
                "Once the team can pay again, protections come back in exactly the reverse order.",
                "",
                "Entries are the protection keys of this section (e.g. \"" + set.fields().get(0).id() + "\"). Protections",
                "missing from the list are dropped after the listed ones, in the order of this section;",
                "unknown entries are ignored. The default drops the advanced protections first, then the",
                "main ones, keeping PvP protection the longest."
        ).defineList(DISMANTLE_ORDER, set.defaultDismantleOrder(), entry -> entry instanceof String);
    }

    /** Protection ids of a claim backend in dismantle order (first = dropped first), empty for an unknown backend. */
    public static List<? extends String> dismantleOrder(String setId) {
        ModConfigSpec.ConfigValue<List<? extends String>> value = DISMANTLE_ORDERS.get(setId);
        if (value == null) {
            return List.of();
        }
        return spec != null && spec.isLoaded() ? value.get() : value.getDefault();
    }

    /** The configured price, or the field's default while the config isn't loaded (or the field unknown). */
    public static long price(String setId, ProtectionFieldSpec field) {
        ModConfigSpec.LongValue value = value(setId, field.id());
        return value != null && spec != null && spec.isLoaded() ? value.get() : field.defaultPrice();
    }

    @Nullable
    public static ModConfigSpec.LongValue value(String setId, String fieldId) {
        Map<String, ModConfigSpec.LongValue> values = PRICES.get(setId);
        return values == null ? null : values.get(fieldId);
    }

    /** setId -> fieldId -> value, for the runtime config command. */
    public static Map<String, Map<String, ModConfigSpec.LongValue>> all() {
        return PRICES;
    }
}
