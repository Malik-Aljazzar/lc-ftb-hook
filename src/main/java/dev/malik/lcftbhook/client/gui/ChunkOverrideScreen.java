package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.client.ClientClaims;
import dev.malik.lcftbhook.client.ClientPublicRegions;
import dev.malik.lcftbhook.client.ClientRegionMembership;
import dev.malik.lcftbhook.client.ProtectionDescriptionTooltip;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ClaimedChunkInfo;
import dev.malik.lcftbhook.data.PlayerAccessList;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.SetChunkLabelPayload;
import dev.malik.lcftbhook.network.SetChunkOverridePayload;
import dev.malik.lcftbhook.service.ProtectionResolution;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A private owner's settings for one chunk: a label and every protection of
 * the region, which the owner may loosen for this chunk. Where it makes
 * sense, a per-player whitelist/blacklist opens from the row's right edge.
 * The stored preference stays visible (struck through) while the region
 * currently can't deliver it, e.g. after being dismantled for non-payment.
 */
public class ChunkOverrideScreen extends LcScreen {
    private static final int LIST_BUTTON_WIDTH = 18;

    private final String chunkKey;

    public ChunkOverrideScreen(@Nullable Screen parent, String chunkKey) {
        super(Component.translatable("gui.lc_ftb_hook.marketplace.settings_title"), parent);
        this.chunkKey = chunkKey;
    }

    @Override
    protected int preferredWidth(int screenWidth) {
        return 300;
    }

    @Override
    protected int preferredHeight(int screenHeight) {
        return screenHeight * 4 / 5;
    }

    @Override
    protected void initContent() {
        int y = top + HEADER_HEIGHT + 6;
        EditBox labelBox = addRenderableWidget(new EditBox(font, left + PAD, y, boxWidth - PAD * 2 - 56, 18, Component.empty()));
        labelBox.setMaxLength(24);
        String label = ClientChunkOwnership.get(chunkKey).label();
        labelBox.setValue(label != null ? label : "");
        labelBox.setHint(Component.translatable("gui.lc_ftb_hook.marketplace.label_ghost"));
        button(Component.translatable("gui.lc_ftb_hook.marketplace.label_set"), left + boxWidth - PAD - 50, y, 50,
                b -> PacketDistributor.sendToServer(new SetChunkLabelPayload(chunkKey, labelBox.getValue().trim())));
        int listTop = y + 24;
        addList(new RowListWidget<Row>(left + PAD, listTop, boxWidth - PAD * 2,
                top + boxHeight - PAD - listTop, this::rows, row -> ProtectionRows.ROW_HEIGHT, this::renderRow)
                .onClick((row, localX, localY) -> click(row, localX))
                .tooltip((row, localX, localY) -> tooltip(row, localX)));
    }

    /** A protection row, the "advanced" expander (property null, count set) or the "coming soon" row (both null/0). */
    private record Row(@Nullable ProtectionProperty property, int advancedCount) {
    }

    /** Main protections and set advanced ones, the expander, (expanded) the rest, "coming soon". */
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        ProtectionRows.Layout layout = ProtectionRows.layout(this::isSet);
        layout.shown().forEach(property -> rows.add(new Row(property, 0)));
        if (!layout.collapsed().isEmpty()) {
            rows.add(new Row(null, layout.collapsed().size()));
            if (ProtectionRows.showAdvanced) {
                layout.collapsed().forEach(property -> rows.add(new Row(property, 0)));
            }
        }
        rows.add(new Row(null, 0));
        return rows;
    }

    /** Loosened, stored preference or an access list - such advanced protections always stay visible. */
    private boolean isSet(ProtectionProperty property) {
        PlayerAccessList list = ClientChunkOwnership.get(chunkKey).accessLists().get(property.id());
        return storedValue(property) != null || !property.isAtMinimum(effectiveValue(property)) || (list != null && !list.isEmpty());
    }

    private void click(Row row, int localX) {
        if (row.property() != null) {
            click(row.property(), localX);
        } else if (row.advancedCount() > 0) {
            ProtectionRows.showAdvanced = !ProtectionRows.showAdvanced;
        }
    }

    private List<Component> tooltip(Row row, int localX) {
        if (row.property() != null) {
            return tooltip(row.property(), localX);
        }
        return row.advancedCount() > 0 ? List.of() : ProtectionRows.comingSoonTooltip();
    }

    private void renderRow(GuiGraphics graphics, Row row, int x, int y, int w, int h, boolean hovered) {
        if (row.property() == null) {
            if (row.advancedCount() > 0) {
                ProtectionRows.drawAdvancedToggle(graphics, font, x, y, w, h, hovered, row.advancedCount());
            } else {
                ProtectionRows.drawComingSoon(graphics, font, x, y, w, h);
            }
            return;
        }
        ProtectionProperty property = row.property();
        ProtectionRows.background(graphics, x, y, w, h, hovered);
        String stored = storedValue(property);
        String effective = effectiveValue(property);
        Component value = ProtectionRows.value(property, effective);
        if (stored != null && !stored.equals(effective)) {
            value = Component.empty().append(dev.malik.lcftbhook.client.ProtectionValueFormat.formatValue(stored, property).copy()
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH)).append(" ").append(value);
        }
        boolean hasList = property.isAccessListRelevant();
        ProtectionRows.labelAndValue(graphics, font, ProtectionRows.label(property), value, x, y, w, hasList ? LIST_BUTTON_WIDTH + 4 : 6);
        if (hasList) {
            int bx = x + w - LIST_BUTTON_WIDTH;
            graphics.fill(bx, y + 1, bx + LIST_BUTTON_WIDTH - 1, y + h - 1, 0xFF4C566A);
            graphics.drawCenteredString(font, "☰", bx + LIST_BUTTON_WIDTH / 2, y + 5, COLOR_TEXT);
        }
    }

    private void click(ProtectionProperty property, int localX) {
        if (property.isAccessListRelevant() && localX >= rowWidth() - LIST_BUTTON_WIDTH) {
            Minecraft.getInstance().setScreen(new PlayerAccessListScreen(this, chunkKey, property));
            return;
        }
        Region region = region();
        if (region == null) {
            return;
        }
        // Cycles the owner's STORED preference, never blocked - it just may not
        // be in effect while the region itself protects less.
        String stored = storedValue(property);
        String current = stored != null ? stored : region.propertyValue(property);
        PacketDistributor.sendToServer(new SetChunkOverridePayload(chunkKey, property.id(), ProtectionRows.nextValue(property, current)));
    }

    private List<Component> tooltip(ProtectionProperty property, int localX) {
        if (property.isAccessListRelevant() && localX >= rowWidth() - LIST_BUTTON_WIDTH) {
            List<Component> lines = new ArrayList<>(List.of(Component.translatable("gui.lc_ftb_hook.access_list.button")));
            if (property.isAtMinimum(effectiveValue(property))) {
                lines.add(Component.translatable("message.lc_ftb_hook.access_list.not_in_effect").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            return lines;
        }
        List<Component> lines = new ArrayList<>(ProtectionDescriptionTooltip.lines(property, false));
        String stored = storedValue(property);
        String effective = effectiveValue(property);
        if (stored != null && !stored.equals(effective)) {
            lines.add(Component.translatable("gui.lc_ftb_hook.marketplace_override_not_in_effect",
                    dev.malik.lcftbhook.client.ProtectionValueFormat.formatValue(effective, property))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return lines;
    }

    private int rowWidth() {
        return boxWidth - PAD * 2 - 6;
    }

    @Nullable
    private String storedValue(ProtectionProperty property) {
        return ProtectionResolution.storedOverrideValue(ClientChunkOwnership.get(chunkKey), property);
    }

    private String effectiveValue(ProtectionProperty property) {
        Region region = region();
        ChunkOwnership ownership = ClientChunkOwnership.get(chunkKey);
        return region != null ? ProtectionResolution.effectiveValue(region, ownership, property) : property.minimumSerialized();
    }

    /**
     * The chunk's region from the public (all teams) region cache - a private
     * chunk can sit in another team's land, whose regions aren't in the
     * viewer's own team-private cache.
     */
    @Nullable
    private Region region() {
        UUID regionId = ClientRegionMembership.rawRegionOf(chunkKey);
        Region region = regionId != null ? ClientPublicRegions.get(regionId) : null;
        if (region != null) {
            return region;
        }
        ClaimedChunkInfo claim = ClientClaims.chunk(chunkKey);
        return claim != null ? ClientPublicRegions.getDefaultFor(claim.teamId()) : null;
    }
}
