package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import dev.malik.lcftbhook.port.protection.BlockProtectionPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

/**
 * {@link BlockProtectionPort} with OPAC's own rules, all through its public
 * API: a player or a mob goes through OPAC's block check
 * ({@code IChunkProtectionAPI.onBlockInteraction} - the same check OPAC runs
 * for a block being broken/used, with its exception groups, mob griefing
 * lists and full passes; no chat message), an unattributed change follows the
 * claim's "blocks by explosions" exception like an explosion would.
 */
public final class OpacBlockProtection implements BlockProtectionPort {
    @Override
    public boolean playerMayChange(ServerPlayer player, ServerLevel level, BlockPos pos, BlockChangeKind kind) {
        return !protection(level).onBlockInteraction(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, level, pos,
                Direction.UP, kind == BlockChangeKind.BREAK, false, true);
    }

    @Override
    public boolean mobMayChange(Entity mob, ServerLevel level, BlockPos pos) {
        // targetExceptions: OPAC's per-block exceptions (e.g. forced-break blocks) apply, as for a real break.
        return !protection(level).onBlockInteraction(mob, null, null, level, pos, Direction.UP, true, false, true);
    }

    @Override
    public boolean unattributedMayChange(ServerLevel level, BlockPos pos) {
        OpenPACServerAPI api = OpenPACServerAPI.get(level.getServer());
        IPlayerChunkClaimAPI claim = api.getServerClaimsManager().get(level.dimension().location(), pos);
        if (claim == null) {
            return true;
        }
        IPlayerConfigAPI config = api.getChunkProtection().getConfig(claim);
        return config == null
                || !config.getEffective(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS)
                || config.getEffective(PlayerConfigOptions.CLAIM_EXCEPTION_BLOCKS_BY_EXPLOSIONS);
    }

    private static IChunkProtectionAPI protection(ServerLevel level) {
        return OpenPACServerAPI.get(level.getServer()).getChunkProtection();
    }
}
