package dev.malik.lcftbhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.malik.lcftbhook.LCFtbHook;
import dev.malik.lcftbhook.config.ForceLoadUpkeepMode;
import dev.malik.lcftbhook.config.LCFtbHookConfig;
import dev.malik.lcftbhook.config.ClaimPricingMode;
import dev.malik.lcftbhook.config.ProtectionUpkeepMode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Lets admins view/change every server config value that makes sense to
 * tune live - i.e. every scalar (Long/Int/Double/Boolean/Enum) value, using
 * exactly the config's own defined range/enum validation
 * ({@code ValueSpec.test}) so a bad input is rejected the same way an
 * invalid line in the .toml file would be. The {@code dismantleOrder} list
 * (a list, not a scalar) is intentionally not exposed here - reordering a
 * list through a single string argument doesn't fit this command's shape.
 * <p>
 * {@code ConfigValue.set(T)} updates the live, already-loaded config
 * immediately (no restart - none of our values use a restart-required
 * range) but does NOT persist to disk by itself; {@code ConfigValue.save()}
 * (confirmed via decompile) does that separately, so every successful set
 * here calls both.
 */
public final class RuntimeConfigCommand {
    private record Entry(java.util.function.Supplier<String> get, Function<String, String> set) {
    }

    private static final Map<String, Entry> ENTRIES = buildEntries();

    private RuntimeConfigCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LCFtbHook.MOD_ID)
                .then(Commands.literal("config")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("list").executes(RuntimeConfigCommand::list))
                        .then(Commands.literal("get")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(RuntimeConfigCommand::suggestKeys)
                                        .executes(RuntimeConfigCommand::get)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(RuntimeConfigCommand::suggestKeys)
                                        .then(Commands.argument("value", StringArgumentType.string())
                                                .executes(RuntimeConfigCommand::set))))));
    }

    private static CompletableFuture<Suggestions> suggestKeys(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String key : ENTRIES.keySet()) {
            if (key.toLowerCase().startsWith(remaining)) {
                builder.suggest(key);
            }
        }
        return builder.buildFuture();
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(ENTRIES.size() + " runtime-configurable value(s):").withStyle(ChatFormatting.YELLOW), false);
        for (Map.Entry<String, Entry> entry : ENTRIES.entrySet()) {
            source.sendSuccess(() -> Component.literal("  " + entry.getKey() + " = ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(entry.getValue().get().get()).withStyle(ChatFormatting.GOLD)), false);
        }
        return 1;
    }

    private static int get(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String key = StringArgumentType.getString(context, "key");
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            source.sendFailure(unknownKeyMessage(key));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(key + " = ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(entry.get().get()).withStyle(ChatFormatting.GOLD)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String key = StringArgumentType.getString(context, "key");
        String value = StringArgumentType.getString(context, "value");
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            source.sendFailure(unknownKeyMessage(key));
            return 0;
        }
        String error = entry.set().apply(value);
        if (error != null) {
            source.sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(key + " = ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(entry.get().get()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" (saved)").withStyle(ChatFormatting.DARK_GRAY)), true);
        return 1;
    }

    private static Component unknownKeyMessage(String key) {
        return Component.literal("Unknown config key: " + key + ". Use /" + LCFtbHook.MOD_ID + " config list to see all keys.")
                .withStyle(ChatFormatting.RED);
    }

    private static Map<String, Entry> buildEntries() {
        Map<String, Entry> map = new TreeMap<>();
        LCFtbHookConfig.Server c = LCFtbHookConfig.SERVER;

        registerLong(map, "claimPrice", c.claimPrice);
        registerEnum(map, "claimPricingMode", c.claimPricingMode, ClaimPricingMode.class);
        registerLong(map, "claimBasePrice", c.claimBasePrice);
        registerLong(map, "claimIncrementPrice", c.claimIncrementPrice);
        registerInt(map, "claimIncrementSize", c.claimIncrementSize);
        registerDouble(map, "claimPriceGrowthPercent", c.claimPriceGrowthPercent);
        registerInt(map, "freeChunks", c.freeChunks);
        registerDouble(map, "unclaimRefundRatio", c.unclaimRefundRatio);
        registerLong(map, "forceLoadUpkeepPrice", c.forceLoadUpkeepPrice);
        registerInt(map, "upkeepPeriodMinutes", c.upkeepPeriodMinutes);
        registerBoolean(map, "lockClaimVisibilityPublic", c.lockClaimVisibilityPublic);
        registerEnum(map, "forceLoadUpkeepMode", c.forceLoadUpkeepMode, ForceLoadUpkeepMode.class);
        registerEnum(map, "protectionUpkeepMode", c.protectionUpkeepMode, ProtectionUpkeepMode.class);
        // Protection prices of every installed claim mod, e.g. "price.ftb.allow_pvp".
        dev.malik.lcftbhook.config.ProtectionPriceConfig.all().forEach((setId, prices) ->
                prices.forEach((fieldId, value) -> registerLong(map, "price." + setId + "." + fieldId, value)));
        registerDouble(map, "warCostMultiplier", c.warCostMultiplier);
        registerDouble(map, "warOutgoingCostMultiplier", c.warOutgoingCostMultiplier);
        registerBoolean(map, "warEnabled", c.warEnabled,
                enabled -> dev.malik.lcftbhook.service.WarService.setWarSystemEnabled(
                        net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(), enabled));

        // TreeMap sorted the registration above alphabetically for iteration
        // order only - return as a LinkedHashMap so callers get a stable,
        // already-sorted view without re-sorting on every /config list call.
        return new LinkedHashMap<>(map);
    }

    private static void registerLong(Map<String, Entry> map, String key, ModConfigSpec.LongValue value) {
        map.put(key, new Entry(
                () -> String.valueOf(value.get()),
                input -> {
                    long parsed;
                    try {
                        parsed = Long.parseLong(input.trim());
                    } catch (NumberFormatException e) {
                        return "Not a whole number: " + input;
                    }
                    if (!value.getSpec().test(parsed)) {
                        return "Value rejected (out of allowed range): " + input;
                    }
                    value.set(parsed);
                    value.save();
                    return null;
                }
        ));
    }

    private static void registerInt(Map<String, Entry> map, String key, ModConfigSpec.IntValue value) {
        map.put(key, new Entry(
                () -> String.valueOf(value.get()),
                input -> {
                    int parsed;
                    try {
                        parsed = Integer.parseInt(input.trim());
                    } catch (NumberFormatException e) {
                        return "Not a whole number: " + input;
                    }
                    if (!value.getSpec().test(parsed)) {
                        return "Value rejected (out of allowed range): " + input;
                    }
                    value.set(parsed);
                    value.save();
                    return null;
                }
        ));
    }

    private static void registerDouble(Map<String, Entry> map, String key, ModConfigSpec.DoubleValue value) {
        map.put(key, new Entry(
                () -> String.valueOf(value.get()),
                input -> {
                    double parsed;
                    try {
                        parsed = Double.parseDouble(input.trim());
                    } catch (NumberFormatException e) {
                        return "Not a number: " + input;
                    }
                    if (!value.getSpec().test(parsed)) {
                        return "Value rejected (out of allowed range): " + input;
                    }
                    value.set(parsed);
                    value.save();
                    return null;
                }
        ));
    }

    private static void registerBoolean(Map<String, Entry> map, String key, ModConfigSpec.BooleanValue value) {
        registerBoolean(map, key, value, null);
    }

    private static void registerBoolean(
            Map<String, Entry> map, String key, ModConfigSpec.BooleanValue value, @javax.annotation.Nullable java.util.function.Consumer<Boolean> onChanged
    ) {
        map.put(key, new Entry(
                () -> String.valueOf(value.get()),
                input -> {
                    String trimmed = input.trim();
                    boolean parsed;
                    if (trimmed.equalsIgnoreCase("true")) {
                        parsed = true;
                    } else if (trimmed.equalsIgnoreCase("false")) {
                        parsed = false;
                    } else {
                        return "Must be true or false: " + input;
                    }
                    value.set(parsed);
                    value.save();
                    if (onChanged != null) {
                        onChanged.accept(parsed);
                    }
                    return null;
                }
        ));
    }

    private static <E extends Enum<E>> void registerEnum(Map<String, Entry> map, String key, ModConfigSpec.EnumValue<E> value, Class<E> enumClass) {
        map.put(key, new Entry(
                () -> String.valueOf(value.get()),
                input -> {
                    E parsed;
                    try {
                        parsed = Enum.valueOf(enumClass, input.trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return "Not one of " + java.util.Arrays.toString(enumClass.getEnumConstants()) + ": " + input;
                    }
                    if (!value.getSpec().test(parsed)) {
                        return "Value rejected: " + input;
                    }
                    value.set(parsed);
                    value.save();
                    return null;
                }
        ));
    }
}
