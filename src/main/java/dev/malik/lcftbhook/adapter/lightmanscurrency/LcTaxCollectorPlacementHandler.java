package dev.malik.lcftbhook.adapter.lightmanscurrency;

import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import io.github.lightman314.lightmanscurrency.common.blocks.TaxCollectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Lightman's Currency's Tax Collector may only be placed in land of the
 * placer's own team, and only by someone allowed to spend from that team's
 * account. Chunk ownership comes from the active claim backend, so this works
 * with FTB Chunks and OPAC alike. Only registered when Lightman's Currency is
 * installed (the block only exists then).
 */
public class LcTaxCollectorPlacementHandler {
    /**
     * Denies the placement before the block is ever set in the world -
     * cancelling {@link BlockEvent.EntityPlaceEvent} below (after the block
     * already exists) turned out to be too late: Lightman's Currency had
     * already registered the tax collector's own data by that point, so
     * reverting the block left an orphaned entry that LC's own cleanup later
     * "destroyed" with a confusing "was destroyed by unpreventable means,
     * but its contents were safely ejected" notification instead of our own
     * denial message ever being the only thing the player saw.
     */
    @SubscribeEvent
    public void onTaxCollectorRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof TaxCollectorBlock)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        // Matches vanilla's usual "place against the clicked face" position -
        // doesn't replicate the "clicked block is replaceable" special case
        // (e.g. placing into grass/water directly), but that only matters
        // right at a chunk border, and the EntityPlaceEvent check below still
        // catches it as a backstop either way.
        BlockPos targetPos = event.getPos().relative(event.getFace());
        String denialKey = lcFtbHook$denialReason(player, level, targetPos);
        if (denialKey != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            player.displayClientMessage(Component.translatable(denialKey), true);
        }
    }

    /** Backstop for placements that don't go through a right-click (e.g. a dispenser) - still correct, just can't avoid LC's own "destroyed" cleanup notification in that rarer case. */
    @SubscribeEvent
    public void onTaxCollectorPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!isTaxCollector(event.getPlacedBlock())) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            event.setCanceled(true);
            return;
        }

        String denialKey = lcFtbHook$denialReason(player, level, event.getPos());
        if (denialKey != null) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable(denialKey), true);
        }
    }

    /** {@code null} = placement allowed. Guarded: if the check fails, placement is refused. */
    @Nullable
    private static String lcFtbHook$denialReason(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return dev.malik.lcftbhook.util.Guard.call("LC tax collector placement check",
                () -> checkPlacement(player, level, pos), "message.lc_ftb_hook.internal_error_denied");
    }

    @Nullable
    private static String checkPlacement(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ClaimPort claims = Backends.claim();
        ChunkPos chunk = new ChunkPos(pos);
        UUID chunkTeam = claims.ownerTeamOf(player.server, level.dimension(), chunk.x, chunk.z);
        if (chunkTeam == null) {
            return null;
        }
        if (!chunkTeam.equals(claims.teamIdForPlayer(player.server, player.getUUID()))) {
            return "message.lc_ftb_hook.tax_collector_wrong_team";
        }
        if (!Backends.currency().canPlayerSpend(player.server, chunkTeam, player.getUUID())) {
            return "message.lc_ftb_hook.tax_collector_denied";
        }
        return null;
    }

    private static boolean isTaxCollector(BlockState state) {
        return state.getBlock() instanceof TaxCollectorBlock;
    }
}
