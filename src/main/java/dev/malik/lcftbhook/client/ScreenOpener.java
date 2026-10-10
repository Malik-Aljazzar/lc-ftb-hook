package dev.malik.lcftbhook.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Opens this mod's screens via reflection instead of a direct constructor
 * reference. Needed for screens reached from a right-click-menu action stored
 * on a long-lived object (a Xaero {@code RightClickOption}, an FTB
 * {@code ContextMenuItem}): the lambda runs much later, and a plain
 * reference from there intermittently threw {@code ClassNotFoundException}.
 * The class is loaded explicitly through this class's own loader, with the
 * thread context loader as fallback.
 * <p>
 * {@code parent} is the screen to return to (null = close to the game).
 */
public final class ScreenOpener {
    private static final String GUI = "dev.malik.lcftbhook.client.gui.";

    private ScreenOpener() {
    }

    public static void openMainMenu() {
        open("MainMenuScreen", new Class<?>[]{Screen.class}, (Screen) null);
    }

    public static void openRegionList(@Nullable Screen parent) {
        open("RegionListScreen", new Class<?>[]{Screen.class}, parent);
    }

    public static void openWar(@Nullable Screen parent) {
        open("WarScreen", new Class<?>[]{Screen.class}, parent);
    }

    public static void openRegionPicker(@Nullable Screen parent, Consumer<UUID> onPicked) {
        open("RegionPickerScreen", new Class<?>[]{Screen.class, Consumer.class}, parent, onPicked);
    }

    public static void openRegionSettings(@Nullable Screen parent, UUID regionId) {
        open("RegionSettingsScreen", new Class<?>[]{Screen.class, UUID.class}, parent, regionId);
    }

    public static void openOverride(@Nullable Screen parent, String chunkKey) {
        open("ChunkOverrideScreen", new Class<?>[]{Screen.class, String.class}, parent, chunkKey);
    }

    public static void openBuyConfirm(@Nullable Screen parent, List<String> chunkKeys, boolean asCountry) {
        open("ChunkBuyConfirmScreen", new Class<?>[]{Screen.class, List.class, boolean.class}, parent, chunkKeys, asCountry);
    }

    public static void openTextPrompt(@Nullable Screen parent, Component title, String ghostText, Consumer<String> onConfirm) {
        open("TextPromptScreen", new Class<?>[]{Screen.class, Component.class, String.class, Consumer.class}, parent, title, ghostText, onConfirm);
    }

    public static void openSalePrice(@Nullable Runnable onBack, List<String> chunkKeys, boolean asCountry) {
        open("ChunkSalePriceScreen", new Class<?>[]{Runnable.class, List.class, boolean.class}, onBack, chunkKeys, asCountry);
    }

    public static void openExpropriatePrice(@Nullable Runnable onBack, List<String> chunkKeys) {
        open("ExpropriatePriceScreen", new Class<?>[]{Runnable.class, List.class}, onBack, chunkKeys);
    }

    /** A "back" action returning to {@code parent} (or closing), for the price screens. */
    public static Runnable backTo(@Nullable Screen parent) {
        return () -> Minecraft.getInstance().setScreen(parent);
    }

    private static void open(String simpleName, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> cls = loadClass(GUI + simpleName);
            Screen screen = (Screen) cls.getConstructor(parameterTypes).newInstance(args);
            Minecraft.getInstance().setScreen(screen);
        } catch (ReflectiveOperationException | ClassCastException e) {
            org.slf4j.LoggerFactory.getLogger("lc_ftb_hook").error("Failed to open {}", simpleName, e);
        }
    }

    private static Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name, true, ScreenOpener.class.getClassLoader());
        } catch (ClassNotFoundException ownLoaderFailure) {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            if (contextLoader == null) {
                throw ownLoaderFailure;
            }
            try {
                return Class.forName(name, true, contextLoader);
            } catch (ClassNotFoundException contextLoaderFailure) {
                ownLoaderFailure.addSuppressed(contextLoaderFailure);
                throw ownLoaderFailure;
            }
        }
    }
}
