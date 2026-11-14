package dev.malik.lcftbhook.port.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Asks the active claim mod whether a block may be changed, for code paths
 * that change blocks directly ({@code Level.destroyBlock}/{@code setBlock})
 * and therefore never reach the events the claim mod itself listens to -
 * e.g. ScorchedGuns bullets shooting out glass. Each answer follows the claim
 * mod's own rules for that kind of cause, so a block is protected exactly as
 * well as it would be against the same cause through the normal path.
 * <p>
 * Adapters: {@code adapter.ftb.FtbBlockProtection}, {@code adapter.opac.OpacBlockProtection}.
 */
public interface BlockProtectionPort {
    /** The claim mod's block rules for this player (or fake player) - "may this player break/use the block". */
    boolean playerMayChange(ServerPlayer player, ServerLevel level, BlockPos pos, BlockChangeKind kind);

    /** The claim mod's mob-griefing rules for the chunk of {@code pos} - {@code mob} is the mob doing it. */
    boolean mobMayChange(Entity mob, ServerLevel level, BlockPos pos);

    /**
     * The claim mod's explosion rules for the chunk of {@code pos} - for changes
     * no player or mob is responsible for (a machine, an ownerless projectile).
     */
    boolean unattributedMayChange(ServerLevel level, BlockPos pos);
}
