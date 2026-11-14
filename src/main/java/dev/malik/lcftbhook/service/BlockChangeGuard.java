package dev.malik.lcftbhook.service;

import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import dev.malik.lcftbhook.port.protection.BlockProtectionPort;
import dev.malik.lcftbhook.util.Guard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Claim protection for block changes that bypass the claim mod's own events
 * (another mod calling {@code destroyBlock}/{@code setBlock} directly). Finds
 * who is responsible for the change and asks the active claim mod with the
 * matching rule: a player -> its block rules for that player, a mob -> its
 * mob-griefing rules, nobody -> its explosion rules.
 * <p>
 * Fails closed: if the check itself throws, the block is treated as
 * protected - a skipped glass break is harmless, a wrongly allowed one is
 * permanent damage on someone's claim.
 */
public final class BlockChangeGuard {
    /** Bounds the owner chain (projectile -> shooter -> owner); real chains are 1-2 long. */
    private static final int MAX_OWNER_DEPTH = 8;

    private BlockChangeGuard() {
    }

    /**
     * Whether {@code cause} (the entity doing it, or whatever it was fired by;
     * {@code null} when nobody is) may change the block at {@code pos}. Always
     * true on the client - only the server decides.
     */
    public static boolean allows(Level level, BlockPos pos, @Nullable Entity cause, BlockChangeKind kind) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return Guard.call("Block change protection check", () -> decide(serverLevel, pos, cause, kind), false);
    }

    private static boolean decide(ServerLevel level, BlockPos pos, @Nullable Entity cause, BlockChangeKind kind) {
        BlockProtectionPort protection = Backends.claim().blockProtection();
        Entity responsible = responsibleFor(cause);
        if (responsible instanceof ServerPlayer player) {
            return protection.playerMayChange(player, level, pos, kind);
        }
        if (responsible instanceof LivingEntity) {
            return protection.mobMayChange(responsible, level, pos);
        }
        return protection.unattributedMayChange(level, pos);
    }

    /**
     * Follows owners up from the direct cause: a projectile to whoever fired
     * it, a tamed mob to its owner. Stops at the first player or at an entity
     * without an owner; null if there is no cause at all.
     */
    @Nullable
    static Entity responsibleFor(@Nullable Entity cause) {
        Entity current = cause;
        for (int depth = 0; depth < MAX_OWNER_DEPTH && current != null && !(current instanceof ServerPlayer); depth++) {
            Entity owner = ownerOf(current);
            if (owner == null || owner == current) {
                break;
            }
            current = owner;
        }
        return current;
    }

    @Nullable
    private static Entity ownerOf(Entity entity) {
        if (entity instanceof TraceableEntity traceable) {
            return traceable.getOwner();
        }
        if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwner();
        }
        return null;
    }
}
