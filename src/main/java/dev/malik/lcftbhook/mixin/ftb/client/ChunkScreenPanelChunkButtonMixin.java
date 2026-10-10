package dev.malik.lcftbhook.mixin.ftb.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.ImageIcon;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.malik.lcftbhook.client.ClientChunkOwnership;
import dev.malik.lcftbhook.client.ClientPendingState;
import dev.malik.lcftbhook.client.ClientRegions;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.client.ftb.ChunkScreenPanelAltToggleAccess;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.service.ProtectionPriceDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel$ChunkButton", remap = false)
public class ChunkScreenPanelChunkButtonMixin {
    private static final ImageIcon CHECKERED = new ImageIcon(
            ResourceLocation.fromNamespaceAndPath("ftbchunks", "textures/checkered.png")
    );

    @Shadow(remap = false)
    private dev.ftb.mods.ftblibrary.math.XZ chunkPos;

    @Shadow(remap = false)
    private dev.ftb.mods.ftbchunks.client.map.MapChunk chunk;

    @Final
    @Shadow(remap = false)
    private dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel this$0;

    /**
     * Right-click no longer opens a menu directly from here - a right-click
     * (with or without a preceding left-drag mark) is now handled uniformly
     * by {@link ChunkScreenPanelMixin}'s {@code mouseReleased} hook, which
     * opens {@link dev.malik.lcftbhook.client.ftb.ChunkContextMenuBuilder}'s menu
     * for whatever is currently selected (one chunk, or a whole drag/marked
     * selection). Only the Alt+Left "mark for old-style toggle" gesture
     * still needs a HEAD injection here.
     */
    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcFtbHook$altClickToggleChunkType(MouseButton mouseButton, CallbackInfo ci) {
        if (!Screen.hasAltDown() || !mouseButton.isLeft() || chunk == null || chunkPos == null) {
            return;
        }
        ci.cancel();
        if (chunk.getClaimedDate().isEmpty()) {
            return;
        }
        ((ChunkScreenPanelAltToggleAccess) this$0).lcFtbHook$selectForAltToggle(chunkPos);
    }

    @Inject(method = "addMouseOverText", at = @At("RETURN"), remap = false)
    private void lcFtbHook$addPendingForceLoadTooltip(
            dev.ftb.mods.ftblibrary.util.TooltipList list,
            CallbackInfo ci
    ) {
        if (chunk == null || chunkPos == null) {
            return;
        }

        ResourceKey<Level> dimension = ((ChunkScreenPanelAccessor) this$0).lcFtbHook$getChunkScreen().getDimension().dimension;
        int chunkX = chunkPos.x();
        int chunkZ = chunkPos.z();

        // Owner/region/for-sale lines used to live here too, but they're now
        // shown in the persistent info panel (see ChunkScreenPanelMixin's
        // drawAndResetHoverPanel) instead of duplicating them in this
        // native tooltip.
        if (ClientPendingState.isPendingForceLoad(dimension, chunkX, chunkZ)) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.chunk_pending_forceload",
                    ProtectionPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
            return;
        }

        if (ClientPendingState.isPendingForceUnload(dimension, chunkX, chunkZ)) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.chunk_pending_forceunload",
                    ProtectionPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
            return;
        }

        if (ClientPendingState.isPendingRegionAssignment(dimension, chunkX, chunkZ)) {
            UUID targetId = ClientPendingState.pendingRegionAssignmentTarget(dimension, chunkX, chunkZ);
            Region target = targetId != null ? ClientRegions.get(targetId) : null;
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_ftb_hook.chunk_pending_region",
                    target != null ? target.name() : "?",
                    ProtectionPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
        }
    }

    @Inject(method = "drawBackground", at = @At("RETURN"), remap = false)
    private void lcFtbHook$drawPendingChunkPattern(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int w,
            int h,
            CallbackInfo ci
    ) {
        ResourceKey<Level> dimension = ((ChunkScreenPanelAccessor) this$0).lcFtbHook$getChunkScreen().getDimension().dimension;
        int chunkX = chunkPos.x();
        int chunkZ = chunkPos.z();

        if (((dev.ftb.mods.ftblibrary.ui.Widget) (Object) this).isMouseOver()) {
            dev.malik.lcftbhook.client.ChunkHoverState.markHovered(dimension, chunkX, chunkZ);
        }

        if (Screen.hasControlDown() && chunk.getClaimedDate().isPresent()) {
            String chunkKey = ChunkPosKey.encode(dimension.location(), chunkX, chunkZ);
            ChunkOwnership ownership = ClientChunkOwnership.get(chunkKey);
            UUID localPlayer = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
            int blinkAlpha = dev.malik.lcftbhook.client.BlinkUtil.alpha();
            if (localPlayer != null && localPlayer.equals(ownership.privateOwner())) {
                drawPattern(graphics, x, y, w, h, Color4I.rgb(0x9C27B0).withAlpha(blinkAlpha));
            } else if (dev.malik.lcftbhook.client.BuyableChunkChecker.isBuyable(ownership, chunk.getTeam().map(t -> t.getTeamId()).orElse(null), chunkKey, localPlayer)) {
                drawPattern(graphics, x, y, w, h, Color4I.rgb(0xFF9800).withAlpha(blinkAlpha));
            } else if (!ownership.isStateOwned()) {
                // Privately owned by someone else - shown regardless of whether
                // it's ALSO listed (see the identical fix in BuyableChunkHighlighter).
                drawPattern(graphics, x, y, w, h, Color4I.rgb(0x2196F3).withAlpha(blinkAlpha));
            }
        }

        if (ClientPendingState.isPendingForceLoad(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(0xFFB74D).withAlpha(170));
            return;
        }

        if (ClientPendingState.isPendingForceUnload(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(0xEF5350).withAlpha(170));
            return;
        }

        if (ClientPendingState.isPendingRegionAssignment(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(0x81C784).withAlpha(170));
        }
    }

    private static void drawPattern(GuiGraphics graphics, int x, int y, int w, int h, Color4I color) {
        CHECKERED.withColor(color).draw(graphics, x, y, w, h);
    }
}
