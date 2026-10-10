package dev.malik.lcftbhook.client.gui;

import dev.malik.lcftbhook.client.ClientWarState;
import dev.malik.lcftbhook.client.gui.WarTooltips.Section;
import dev.malik.lcftbhook.network.RequestWarStatePayload;
import dev.malik.lcftbhook.network.ToggleWarPayload;
import dev.malik.lcftbhook.network.WarTeamEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Wars: incoming, outgoing and possible targets, each searchable. Clicking a
 * row's action (right side) declares/ends a war; clicking a pending row
 * cancels the queued change. Hover a row for its costs and vulnerabilities.
 */
public class WarScreen extends LcScreen {
    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_HEADER = 18;
    private static final int ACTION_WIDTH = 70;

    private final Map<Section, String> filters = new EnumMap<>(Section.class);
    private final Map<Section, Integer> sectionTops = new EnumMap<>(Section.class);

    public WarScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.lc_ftb_hook.war.title"), parent);
        PacketDistributor.sendToServer(new RequestWarStatePayload());
    }

    @Override
    protected int preferredWidth(int screenWidth) {
        return Math.max(300, screenWidth * 3 / 5);
    }

    @Override
    protected int preferredHeight(int screenHeight) {
        return screenHeight * 4 / 5;
    }

    @Override
    protected void initContent() {
        hoverArea(left + boxWidth - 22, top + 3, 18, 16, WarTooltips::costSummary);
        int contentTop = top + HEADER_HEIGHT + 4;
        int sectionHeight = (top + boxHeight - PAD - contentTop) / 3;
        int y = contentTop;
        for (Section section : Section.values()) {
            initSection(section, y, sectionHeight - 4);
            y += sectionHeight;
        }
    }

    private void initSection(Section section, int y, int height) {
        sectionTops.put(section, y);
        int filterWidth = Math.min(160, Math.max(96, boxWidth / 3));
        EditBox filter = addRenderableWidget(new EditBox(font, left + boxWidth - PAD - filterWidth, y + 1, filterWidth, 14, Component.empty()));
        filter.setHint(Component.translatable("gui.lc_ftb_hook.war.filter_ghost"));
        filter.setValue(filters.getOrDefault(section, ""));
        filter.setResponder(text -> filters.put(section, text));
        int listTop = y + SECTION_HEADER;
        addList(new RowListWidget<>(left + PAD, listTop, boxWidth - PAD * 2, height - SECTION_HEADER,
                () -> entries(section), entry -> ROW_HEIGHT,
                (graphics, entry, x, rowY, w, h, hovered) -> renderRow(graphics, entry, section, x, rowY, w, h, hovered))
                .onClick((entry, localX, localY) -> click(entry, section, localX))
                .tooltip((entry, localX, localY) -> WarTooltips.entry(entry, section)));
    }

    private List<WarTeamEntry> entries(Section section) {
        if (section == Section.DECLARE && !ClientWarState.canManageWar()) {
            return List.of();
        }
        Supplier<List<WarTeamEntry>> source = switch (section) {
            case INCOMING -> ClientWarState::incoming;
            case OUTGOING -> ClientWarState::outgoing;
            case DECLARE -> ClientWarState::availableTargets;
        };
        String query = filters.getOrDefault(section, "").trim().toLowerCase(Locale.ROOT);
        return source.get().stream()
                .filter(entry -> query.isEmpty() || entry.displayName().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparingInt((WarTeamEntry entry) -> searchRank(entry.displayName(), query))
                        .thenComparing(WarTeamEntry::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static int searchRank(String name, String query) {
        String lower = name.toLowerCase(Locale.ROOT);
        return query.isEmpty() || lower.equals(query) ? 0 : lower.startsWith(query) ? 1 : 2;
    }

    private void renderRow(GuiGraphics graphics, WarTeamEntry entry, Section section, int x, int y, int w, int h, boolean hovered) {
        boolean vulnerable = section == Section.OUTGOING && entry.hasWarVulnerability();
        graphics.fill(x, y, x + w, y + h, hovered ? COLOR_ROW_HOVER : COLOR_ROW);
        if (vulnerable) {
            graphics.fill(x, y, x + 2, y + h, 0xC8EBCB8B);
        }
        int right = x + w - 4;
        Component action = actionLabel(entry, section);
        if (action != null) {
            graphics.fill(right - ACTION_WIDTH, y + 2, right, y + h - 2, 0xFF4C566A);
            graphics.drawCenteredString(font, action, right - ACTION_WIDTH / 2, y + 6, COLOR_TEXT);
            right -= ACTION_WIDTH + 4;
        }
        Component cost = WarTooltips.costLabel(entry);
        graphics.drawString(font, cost, right - font.width(cost), y + 6, COLOR_TEXT);
        String name = font.plainSubstrByWidth(entry.displayName(), Math.max(0, right - font.width(cost) - x - 12));
        graphics.drawString(font, name, x + 6, y + 6, vulnerable ? 0xEBCB8B : COLOR_TEXT);
    }

    /** "Declare" / "End war" while that action is possible, otherwise null. */
    @Nullable
    private static Component actionLabel(WarTeamEntry entry, Section section) {
        if (section == Section.INCOMING || entry.isPending() || !ClientWarState.canManageWar()) {
            return null;
        }
        return Component.translatable(section == Section.DECLARE ? "gui.lc_ftb_hook.war.declare" : "gui.lc_ftb_hook.war.end_war");
    }

    private void click(WarTeamEntry entry, Section section, int localX) {
        if (!ClientWarState.canManageWar() || section == Section.INCOMING) {
            return;
        }
        int rowWidth = boxWidth - PAD * 2 - 6;
        boolean onAction = actionLabel(entry, section) != null && localX >= rowWidth - 4 - ACTION_WIDTH;
        if (entry.isPending() || onAction) {
            PacketDistributor.sendToServer(new ToggleWarPayload(entry.teamId()));
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "?", left + boxWidth - 15, top + 7, COLOR_MUTED);
        for (Section section : Section.values()) {
            int y = sectionTops.getOrDefault(section, top);
            graphics.drawString(font, heading(section).copy().withStyle(ChatFormatting.BOLD), left + PAD, y + 4, 0x88C0D0);
            if (entries(section).isEmpty()) {
                graphics.drawString(font, emptyMessage(section), left + PAD + 6, y + SECTION_HEADER + 4, COLOR_MUTED);
            }
        }
    }

    private static Component heading(Section section) {
        return Component.translatable(switch (section) {
            case INCOMING -> "gui.lc_ftb_hook.war.incoming_heading";
            case OUTGOING -> "gui.lc_ftb_hook.war.outgoing_heading";
            case DECLARE -> "gui.lc_ftb_hook.war.declare_heading";
        });
    }

    private Component emptyMessage(Section section) {
        if (section == Section.DECLARE && !ClientWarState.canManageWar()) {
            return Component.translatable("gui.lc_ftb_hook.war.view_only").withStyle(ChatFormatting.ITALIC);
        }
        if (section == Section.DECLARE && !ClientWarState.availableTargets().isEmpty()) {
            return Component.translatable("gui.lc_ftb_hook.war.search_empty").withStyle(ChatFormatting.ITALIC);
        }
        return Component.translatable(switch (section) {
            case INCOMING -> "gui.lc_ftb_hook.war.empty_incoming";
            case OUTGOING -> "gui.lc_ftb_hook.war.empty_outgoing";
            case DECLARE -> "gui.lc_ftb_hook.war.empty_targets";
        }).withStyle(ChatFormatting.ITALIC);
    }
}
