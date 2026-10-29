package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ClientClaimPrices;
import dev.malik.lcftbhook.client.ClientPendingState;
import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.client.ProtectionDescriptionTooltip;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.network.DeleteRegionPayload;
import dev.malik.lcftbhook.network.RenameRegionPayload;
import dev.malik.lcftbhook.network.RequestClaimPricesPayload;
import dev.malik.lcftbhook.network.RequestRegionsPayload;
import dev.malik.lcftbhook.network.SetRegionAllowPrivateSellingPayload;
import dev.malik.lcftbhook.network.SetRegionBuyerAccessPayload;
import dev.malik.lcftbhook.network.SetRegionForceLoadModePayload;
import dev.malik.lcftbhook.network.SetRegionPropertyPayload;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import dev.malik.lcftbhook.teams.MarketplaceTeamProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One region's settings: chunk count and upkeep first, then private selling,
 * buyer access, the force-load mode and every protection the server's claim mod offers - click a
 * row to cycle its value; queued changes show the target plus what's live now.
 */
public class RegionSettingsScreen extends LcScreen {
    private static final int INFO_LINE = 12;

    private sealed interface Row permits SellingRow, BuyerRow, ForceLoadRow, PropertyRow, AdvancedRow, ComingSoonRow {
    }

    private record AdvancedRow(int count) implements Row {
    }

    private record SellingRow() implements Row {
    }

    private record BuyerRow() implements Row {
    }

    private record ForceLoadRow() implements Row {
    }

    private record PropertyRow(ProtectionProperty property) implements Row {
    }

    private record ComingSoonRow() implements Row {
    }

    private final UUID regionId;
    private int infoTop;

    public RegionSettingsScreen(@Nullable Screen parent, UUID regionId) {
        super(Component.empty(), parent);
        this.regionId = regionId;
        PacketDistributor.sendToServer(new RequestRegionsPayload());
        PacketDistributor.sendToServer(new RequestClaimPricesPayload());
    }

    @Override
    protected int preferredWidth(int screenWidth) {
        return Math.max(300, screenWidth * 2 / 5);
    }

    @Override
    protected int preferredHeight(int screenHeight) {
        return screenHeight * 4 / 5;
    }

    @Override
    protected void initContent() {
        Region region = ClientRegions.get(regionId);
        int y = top + HEADER_HEIGHT + 6;
        if (region != null && !region.isDefault()) {
            EditBox renameBox = addRenderableWidget(new EditBox(font, left + PAD, y, boxWidth - PAD * 2 - 96, 18, Component.empty()));
            renameBox.setMaxLength(32);
            renameBox.setValue(region.name());
            button(Component.translatable("gui.lc_ftb_hook.regions.rename"), left + boxWidth - PAD - 90, y, 90, b -> {
                String name = renameBox.getValue().trim();
                if (!name.isEmpty()) {
                    PacketDistributor.sendToServer(new RenameRegionPayload(regionId, name));
                }
            });
            button(Component.literal("X"), left + boxWidth - 22, top + 3, 18, b -> {
                PacketDistributor.sendToServer(new DeleteRegionPayload(regionId));
                onClose();
            }).setTooltip(Tooltip.create(Component.translatable("gui.lc_ftb_hook.regions.delete")));
            y += 24;
        }
        infoTop = y;
        hoverArea(left + PAD, y + INFO_LINE, boxWidth - PAD * 2, INFO_LINE, () -> RegionUpkeepTooltips.forRegion(regionId));
        int listTop = y + INFO_LINE * 3 + 4;
        addList(new RowListWidget<>(left + PAD, listTop, boxWidth - PAD * 2, top + boxHeight - PAD - listTop,
                this::rows, this::rowHeight, this::renderRow)
                .onClick((row, localX, localY) -> click(row))
                .tooltip((row, localX, localY) -> tooltip(row)));
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new SellingRow());
        rows.add(new BuyerRow());
        rows.add(new ForceLoadRow());
        ProtectionRows.Layout layout = ProtectionRows.layout(this::isSet);
        layout.shown().forEach(property -> rows.add(new PropertyRow(property)));
        if (!layout.collapsed().isEmpty()) {
            rows.add(new AdvancedRow(layout.collapsed().size()));
            if (ProtectionRows.showAdvanced) {
                layout.collapsed().forEach(property -> rows.add(new PropertyRow(property)));
            }
        }
        rows.add(new ComingSoonRow());
        return rows;
    }

    /** Protected now or with a change queued - such advanced protections always stay visible. */
    private boolean isSet(ProtectionProperty property) {
        Region region = ClientRegions.get(regionId);
        return pendingValue(property) != null || (region != null && !region.isAtMinimum(property));
    }

    private int rowHeight(Row row) {
        return row instanceof PropertyRow p && pendingValue(p.property()) != null
                ? ProtectionRows.PENDING_ROW_HEIGHT : ProtectionRows.ROW_HEIGHT;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        Region region = ClientRegions.get(regionId);
        graphics.drawCenteredString(font, region != null ? region.name() : "?", left + boxWidth / 2, top + 7, COLOR_TEXT);
        graphics.drawString(font, Component.translatable("gui.lc_ftb_hook.regions.chunk_count", ClientRegions.chunkCount(regionId)),
                left + PAD, infoTop, COLOR_TEXT_DIM);
        long current = ClientRegions.currentUpkeepCopper(regionId) + ClientRegions.forceLoadCurrentUpkeepCopper(regionId);
        long pending = ClientRegions.pendingUpkeepCopper(regionId) + ClientRegions.forceLoadPendingUpkeepCopper(regionId);
        graphics.drawString(font, RegionUpkeepTooltips.upkeepLine(current, pending), left + PAD, infoTop + INFO_LINE, COLOR_TEXT);
        if (ClientRegions.billableChunkCount(regionId) == 0) {
            graphics.drawString(font, Component.translatable("gui.lc_ftb_hook.regions.empty_hint")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), left + PAD, infoTop + INFO_LINE * 2, COLOR_TEXT);
        }
    }

    private void renderRow(GuiGraphics graphics, Row row, int x, int y, int w, int h, boolean hovered) {
        Region region = ClientRegions.get(regionId);
        switch (row) {
            case ComingSoonRow r -> ProtectionRows.drawComingSoon(graphics, font, x, y, w, h);
            case AdvancedRow r -> ProtectionRows.drawAdvancedToggle(graphics, font, x, y, w, h, hovered, r.count());
            case SellingRow r -> {
                ProtectionRows.background(graphics, x, y, w, h, hovered);
                boolean allowed = region == null || region.allowPrivateSelling();
                ProtectionRows.labelAndValue(graphics, font, Component.translatable("gui.lc_ftb_hook.regions.allow_private_selling"),
                        Component.translatable(allowed ? "gui.lc_ftb_hook.regions.selling_enabled" : "gui.lc_ftb_hook.regions.selling_disabled")
                                .withStyle(allowed ? ChatFormatting.GREEN : ChatFormatting.RED), x, y, w, 6);
            }
            case BuyerRow r -> {
                ProtectionRows.background(graphics, x, y, w, h, hovered);
                ProtectionRows.labelAndValue(graphics, font, Component.translatable("gui.lc_ftb_hook.regions.buyer_access"),
                        Component.translatable(accessKey(region)), x, y, w, 6);
            }
            case ForceLoadRow r -> {
                ProtectionRows.background(graphics, x, y, w, h, hovered);
                ProtectionRows.labelAndValue(graphics, font, ForceLoadModeRow.label(), ForceLoadModeRow.value(region), x, y, w, 6);
            }
            case PropertyRow r -> renderProperty(graphics, region, r.property(), x, y, w, h, hovered);
        }
    }

    private void renderProperty(GuiGraphics graphics, @Nullable Region region, ProtectionProperty property,
                                int x, int y, int w, int h, boolean hovered) {
        ProtectionRows.background(graphics, x, y, w, h, hovered);
        String live = region != null ? region.propertyValue(property) : property.minimumSerialized();
        String pending = pendingValue(property);
        String shown = pending != null ? pending : live;
        Component value = ProtectionRows.value(property, shown);
        Long price = ClientClaimPrices.protectionPrice(property.id());
        if (price != null && price > 0 && !property.isAtMinimum(shown)) {
            value = value.copy().append(Component.literal(" (+").append(ProtectionPriceDisplay.formatPricePerChunk(price))
                    .append(Component.translatable("gui.lc_ftb_hook.protection_price_badge_suffix")).append(")")
                    .withStyle(ChatFormatting.GOLD));
        }
        ProtectionRows.labelAndValue(graphics, font, ProtectionRows.label(property), value, x, y, w, 6);
        if (pending != null) {
            graphics.drawString(font, Component.translatable("gui.lc_ftb_hook.protection_currently_active",
                    ProtectionRows.value(property, live)).withStyle(ChatFormatting.GRAY), x + 6, y + 16, COLOR_TEXT);
            Component pendingLabel = Component.translatable("gui.lc_ftb_hook.pending").withStyle(ChatFormatting.GOLD);
            graphics.drawString(font, pendingLabel, x + w - 6 - font.width(pendingLabel), y + 16, COLOR_TEXT);
        }
    }

    /** The queued value if it differs from the live one, else null. */
    @Nullable
    private String pendingValue(ProtectionProperty property) {
        Region region = ClientRegions.get(regionId);
        String pending = ClientPendingState.getPendingRegionProperty(regionId, property);
        String live = region != null ? region.propertyValue(property) : property.minimumSerialized();
        return pending != null && !pending.equals(live) ? pending : null;
    }

    private void click(Row row) {
        if (row instanceof AdvancedRow) {
            ProtectionRows.showAdvanced = !ProtectionRows.showAdvanced;
            return;
        }
        Region region = ClientRegions.get(regionId);
        if (region == null) {
            return;
        }
        switch (row) {
            case SellingRow r -> PacketDistributor.sendToServer(new SetRegionAllowPrivateSellingPayload(regionId, !region.allowPrivateSelling()));
            case BuyerRow r -> PacketDistributor.sendToServer(new SetRegionBuyerAccessPayload(regionId, nextAccess(region.buyerAccess())));
            case ForceLoadRow r -> ForceLoadModeRow.next(region).ifPresent(mode ->
                    PacketDistributor.sendToServer(new SetRegionForceLoadModePayload(regionId, mode.id())));
            case PropertyRow r -> {
                // Cycle from the queued target if there is one - otherwise every
                // click re-sent the same target and pending changes couldn't be undone.
                String pending = pendingValue(r.property());
                String current = pending != null ? pending : region.propertyValue(r.property());
                PacketDistributor.sendToServer(new SetRegionPropertyPayload(regionId, r.property().id(),
                        ProtectionRows.nextValue(r.property(), current)));
            }
            case AdvancedRow r -> {
            }
            case ComingSoonRow r -> {
            }
        }
    }

    private List<Component> tooltip(Row row) {
        return switch (row) {
            case PropertyRow r -> ProtectionDescriptionTooltip.lines(r.property(), true);
            case ComingSoonRow r -> ProtectionRows.comingSoonTooltip();
            case ForceLoadRow r -> ForceLoadModeRow.tooltip();
            default -> List.of();
        };
    }

    private static String nextAccess(String current) {
        return switch (current) {
            case MarketplaceTeamProperties.ACCESS_ALL -> MarketplaceTeamProperties.ACCESS_ALLIES;
            case MarketplaceTeamProperties.ACCESS_ALLIES -> MarketplaceTeamProperties.ACCESS_TEAM;
            default -> MarketplaceTeamProperties.ACCESS_ALL;
        };
    }

    private static String accessKey(@Nullable Region region) {
        String access = region != null ? region.buyerAccess() : MarketplaceTeamProperties.ACCESS_ALL;
        return switch (access) {
            case MarketplaceTeamProperties.ACCESS_ALLIES -> "gui.lc_ftb_hook.marketplace.access_allies";
            case MarketplaceTeamProperties.ACCESS_TEAM -> "gui.lc_ftb_hook.marketplace.access_team";
            default -> "gui.lc_ftb_hook.marketplace.access_all";
        };
    }
}
