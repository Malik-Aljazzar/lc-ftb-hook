package dev.malik.lcftbhook.mixin.ftb.client;

import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.malik.lcftbhook.client.ftb.ChunkContextMenuBuilder;
import dev.malik.lcftbhook.client.ChunkHoverState;
import dev.malik.lcftbhook.client.ClientClaimPrices;
import dev.malik.lcftbhook.client.ftb.ChunkScreenPanelAltToggleAccess;
import dev.malik.lcftbhook.client.ProtectionInfoLines;
import dev.malik.lcftbhook.client.ProtectionInfoPanelRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel", remap = false)
public class ChunkScreenPanelMixin implements ChunkScreenPanelAltToggleAccess {
    @Shadow(remap = false)
    private ChunkScreen chunkScreen;

    @Shadow(remap = false)
    private XZ firstSelectedChunk;

    @Shadow(remap = false)
    private Set<XZ> selectedChunks;

    @Shadow(remap = false)
    private dev.ftb.mods.ftblibrary.ui.Button lastButtonDragged;

    @Unique
    private static final ThreadLocal<Boolean> lcFtbHook$skipProblemCount = ThreadLocal.withInitial(() -> false);

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"
            )
    )
    private MutableComponent lcFtbHook$formatClaimProblem(String key) {
        if (ClientClaimPrices.isLcClaimResult(key)) {
            lcFtbHook$skipProblemCount.set(true);
            return ClientClaimPrices.claimProblemLine(key);
        }
        lcFtbHook$skipProblemCount.set(false);
        return Component.translatable(key);
    }

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/MutableComponent;append(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"
            )
    )
    private MutableComponent lcFtbHook$hideProblemChunkCount(MutableComponent component, String suffix) {
        if (Boolean.TRUE.equals(lcFtbHook$skipProblemCount.get())) {
            lcFtbHook$skipProblemCount.set(false);
            return component;
        }
        return component.append(suffix);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$altToggleOnRelease(dev.ftb.mods.ftblibrary.ui.input.MouseButton button, CallbackInfo ci) {
        if (!Screen.hasAltDown() || !button.isLeft() || selectedChunks.isEmpty()) {
            return;
        }
        lcFtbHook$releaseAltToggleSelection();
        ci.cancel();
    }

    /**
     * FTB's native {@code mouseReleased} unconditionally fires a claim
     * (plain left) or unclaim (plain right) request for whatever drag/click
     * built up in {@code selectedChunks}, then clears the selection. We want
     * releasing a plain left- or right-drag/click to immediately open our
     * context menu for the marked selection instead. Shift+drag (FTB's
     * force-load/-unload shortcut) is left completely alone by checking
     * {@code hasShiftDown()} here too.
     */
    @Redirect(method = "mouseReleased", at = @At(value = "INVOKE", target = "Ljava/util/Set;isEmpty()Z"), remap = false)
    private boolean lcFtbHook$suppressAutoClaimUnclaim(Set<XZ> selection, MouseButton button) {
        if (Screen.hasShiftDown()) {
            return selection.isEmpty();
        }
        return true;
    }

    @Inject(method = "mouseReleased", at = @At("TAIL"), remap = false)
    private void lcFtbHook$openMenuOnRelease(MouseButton button, CallbackInfo ci) {
        if (Screen.hasShiftDown() || selectedChunks.isEmpty()) {
            return;
        }
        List<XZ> positions = new ArrayList<>(selectedChunks);
        selectedChunks.clear();
        firstSelectedChunk = null;
        lastButtonDragged = null;

        List<ContextMenuItem> items = ChunkContextMenuBuilder.build(chunkScreen, positions);
        if (!items.isEmpty()) {
            chunkScreen.openContextMenu(items);
        }
    }

    @Override
    public void lcFtbHook$selectForAltToggle(XZ chunkPos) {
        if (selectedChunks.isEmpty()) {
            firstSelectedChunk = chunkPos;
        }
        selectedChunks.add(chunkPos);
    }

    /**
     * The old alt-click land/build toggle is superseded by the region
     * assignment context menu (see plan A6/B2, not yet built here) - the
     * selection accumulation stays harmless (just cleared) until that menu
     * lands and calls {@link dev.malik.lcftbhook.network.RequestRegionAssignmentPayload} instead.
     */
    @Override
    public void lcFtbHook$releaseAltToggleSelection() {
        selectedChunks.clear();
        firstSelectedChunk = null;
        lastButtonDragged = null;
    }

    @Unique
    private MapChunk lcFtbHook$mapChunk(XZ pos) {
        return chunkScreen.getDimension()
                .getRegion(XZ.regionFromChunk(pos.x(), pos.z()))
                .getDataBlocking()
                .getChunk(pos);
    }

    /**
     * Draws the same fixed info panel Xaero's World Map shows, using
     * whichever chunk button reported itself hovered LAST frame (marked in
     * {@code ChunkScreenPanelChunkButtonMixin}), then resets the flag so this
     * frame's children can mark the current one. Panel content is therefore
     * one frame stale, which is imperceptible for a static info readout.
     */
    private static final int PANEL_Y = 8;
    private static final int PANEL_WIDTH = 170;
    private static final int PANEL_GAP = 14;

    @Inject(method = "drawBackground", at = @At("HEAD"), remap = false)
    private void lcFtbHook$drawAndResetHoverPanel(GuiGraphics graphics, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        // Positioned relative to the claim screen's own left edge (with a
        // gap) rather than a fixed screen-absolute X, so it sits just
        // outside the map window instead of overlapping it or crowding its
        // edge, regardless of resolution/GUI scale.
        var moneyContent = new ProtectionInfoLines.Content(dev.malik.lcftbhook.client.ClientClaimPrices.moneyLines(true), null);
        ProtectionInfoLines.Content content = ChunkHoverState.isPresent()
                ? ProtectionInfoLines.build(ChunkHoverState.dimension(), ChunkHoverState.chunkX(), ChunkHoverState.chunkZ())
                : ProtectionInfoLines.Content.EMPTY;
        // Grows with its content, but only into the free space left of the map window.
        int width = ProtectionInfoPanelRenderer.width(PANEL_WIDTH, x - PANEL_GAP - 8, moneyContent, content);
        int panelX = Math.max(8, x - width - PANEL_GAP);
        int cursorY = PANEL_Y;

        int moneyHeight = ProtectionInfoPanelRenderer.height(moneyContent);
        if (moneyHeight > 0) {
            ProtectionInfoPanelRenderer.draw(graphics, moneyContent, panelX, cursorY, width);
            cursorY += moneyHeight + 4;
        }
        ProtectionInfoPanelRenderer.draw(graphics, content, panelX, cursorY, width);
        ChunkHoverState.reset();
    }
}
