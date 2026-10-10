package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.data.Region;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Small "pick a region" list, opened from "Change region..." in the chunk menus. */
public class RegionPickerScreen extends LcScreen {
    private static final int ROW_HEIGHT = 18;

    private final Consumer<UUID> onPicked;

    public RegionPickerScreen(@Nullable Screen parent, Consumer<UUID> onPicked) {
        super(Component.translatable("gui.lc_ftb_hook.regions.pick_title"), parent);
        this.onPicked = onPicked;
    }

    @Override
    protected int preferredWidth(int screenWidth) {
        return 200;
    }

    @Override
    protected int preferredHeight(int screenHeight) {
        return 180;
    }

    @Override
    protected void initContent() {
        addList(new RowListWidget<>(left + PAD, top + HEADER_HEIGHT + PAD, boxWidth - PAD * 2,
                boxHeight - HEADER_HEIGHT - PAD * 2, RegionPickerScreen::regions, region -> ROW_HEIGHT,
                (graphics, region, x, y, w, h, hovered) -> {
                    graphics.fill(x, y, x + w, y + h, hovered ? COLOR_ROW_HOVER : COLOR_ROW);
                    graphics.drawString(font, region.name(), x + 6, y + 5, COLOR_TEXT);
                })
                .onClick((region, localX, localY) -> {
                    onPicked.accept(region.id());
                    onClose();
                }));
    }

    private static List<Region> regions() {
        return ClientRegions.regionOrder().stream().map(ClientRegions::get).filter(Objects::nonNull).toList();
    }
}
