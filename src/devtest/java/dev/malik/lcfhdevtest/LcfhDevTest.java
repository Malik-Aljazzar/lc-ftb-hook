package dev.malik.lcfhdevtest;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Dev-only test commands, run via RCON on a dev server:
 * {@code /lcfhtest scguns <teamName>} - see {@link ScgunsProtectionTest} (FTB Chunks + ScorchedGuns);
 * {@code /lcfhtest blockport} - see {@link BlockPortSmokeTest} (any claim backend).
 * Prints PASS/FAIL per case; the command result is 1 when everything passed.
 */
@Mod("lc_ftb_hook_devtest")
public final class LcfhDevTest {
    public LcfhDevTest(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(LcfhDevTest::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lcfhtest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("blockport")
                        .executes(ctx -> BlockPortSmokeTest.run(ctx.getSource()))
                        .then(Commands.literal("protect")
                                .then(Commands.literal("on").executes(ctx -> BlockPortSmokeTest.protect(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> BlockPortSmokeTest.protect(ctx.getSource(), false)))))
                .then(Commands.literal("scguns")
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .executes(ctx -> runScguns(ctx.getSource(), StringArgumentType.getString(ctx, "owner"))))));
    }

    private static int runScguns(CommandSourceStack source, String owner) {
        if (!ModList.get().isLoaded("scguns") || !ModList.get().isLoaded("ftbchunks")) {
            source.sendFailure(Component.literal("needs ScorchedGuns + FTB Chunks on this server"));
            return 0;
        }
        return ScgunsProtectionTest.run(source, owner);
    }
}
