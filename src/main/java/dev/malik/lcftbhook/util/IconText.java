package dev.malik.lcftbhook.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Text that may contain icon glyphs (private-use characters a currency adds
 * to the font, e.g. coin icons). Bitmap glyphs are tinted by the text color,
 * so icons are kept white - their own colors - while the digits around them
 * take whatever style the caller applies. Needed for amounts that arrive as
 * plain strings (synced balances), which lose the formatter's own styling.
 */
public final class IconText {
    private static final int WHITE = 0xFFFFFF;

    private IconText() {
    }

    public static MutableComponent of(String text) {
        MutableComponent result = Component.empty();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isIcon(c)) {
                flush(result, run);
                result.append(Component.literal(String.valueOf(c)).withStyle(style -> style.withColor(WHITE)));
            } else {
                run.append(c);
            }
        }
        flush(result, run);
        return result;
    }

    private static boolean isIcon(char c) {
        return c >= '\uE000' && c <= '\uF8FF';
    }

    private static void flush(MutableComponent result, StringBuilder run) {
        if (!run.isEmpty()) {
            result.append(Component.literal(run.toString()));
            run.setLength(0);
        }
    }
}
