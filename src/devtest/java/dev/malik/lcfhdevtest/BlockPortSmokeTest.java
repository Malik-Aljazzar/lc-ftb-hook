package dev.malik.lcfhdevtest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.malik.lcftbhook.data.ChunkPosKey;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.port.Backends;
import dev.malik.lcftbhook.port.claim.ClaimPort;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import dev.malik.lcftbhook.port.protection.BlockProtectionPort;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec.Role;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Backend-neutral smoke test of the active claim mod's {@link BlockProtectionPort}
 * (FTB or OPAC), on the first settled overworld claim with a stored region:
 * {@code blockport} prints the port's answers there for an outsider (fake
 * player), a zombie and no cause, and checks that an unclaimed chunk allows
 * all of them. {@code blockport protect on} sets that claim's block edit,
 * explosion and mob griefing fields to protected (OPAC picks it up within 100
 * ticks), {@code blockport protect off} restores the original region.
 */
final class BlockPortSmokeTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final List<Role> ROLES = List.of(Role.BLOCK_EDIT, Role.EXPLOSIONS, Role.MOB_GRIEFING);

    private record TestClaim(UUID teamId, ClaimPort.ChunkKey chunk, Region region) {
    }

    @Nullable
    private static TestClaim saved;

    private BlockPortSmokeTest() {
    }

    static int run(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = server.overworld();
        ClaimPort claims = Backends.claim();
        BlockProtectionPort port = claims.blockProtection();
        ServerPlayer outsider = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("lcfh-devtest-outsider".getBytes()), "lcfh_outsider"));
        LivingEntity zombie = EntityType.ZOMBIE.create(level);
        List<String> report = new ArrayList<>();
        int failures = 0;
        TestClaim claim = testClaim(server, claims);
        if (claim == null) {
            report.add("FAIL no settled overworld claim with a stored region");
            failures++;
        } else {
            report.add("claimed " + claim.chunk().pos() + " (region '" + claim.region().name() + "'): "
                    + answers(port, outsider, zombie, level, claim.chunk().pos()));
            ChunkPos free = new ChunkPos(claim.chunk().pos().x + 40, claim.chunk().pos().z + 40);
            boolean unclaimed = claims.ownerTeamOf(server, Level.OVERWORLD, free.x, free.z) == null;
            String freeAnswers = answers(port, outsider, zombie, level, free);
            boolean ok = unclaimed && !freeAnswers.contains("false");
            report.add((ok ? "PASS" : "FAIL") + " unclaimed " + free + ": " + freeAnswers);
            failures += ok ? 0 : 1;
        }
        report.add(failures == 0 ? "DONE" : failures + " FAILED");
        print(source, report);
        return failures == 0 ? 1 : 0;
    }

    static int protect(CommandSourceStack source, boolean on) {
        MinecraftServer server = source.getServer();
        FtbHookSavedData data = FtbHookSavedData.get(server);
        if (!on) {
            if (saved == null) {
                print(source, List.of("nothing to restore"));
                return 0;
            }
            data.updateRegion(saved.teamId(), saved.region());
            print(source, List.of("restored region '" + saved.region().name() + "' of " + saved.chunk().pos()));
            saved = null;
            return 1;
        }
        TestClaim claim = saved != null ? saved : testClaim(server, Backends.claim());
        if (claim == null) {
            print(source, List.of("FAIL no settled overworld claim with a stored region"));
            return 0;
        }
        saved = claim;
        Region region = claim.region();
        List<String> fields = new ArrayList<>();
        for (Role role : ROLES) {
            for (ProtectionProperty property : ProtectionProperty.withRole(role)) {
                region = region.withProperty(property.id(), property.isPrivacyMode() ? "TEAM" : "false");
                fields.add(property.id());
            }
        }
        data.updateRegion(claim.teamId(), region);
        print(source, List.of("protected " + fields + " on " + claim.chunk().pos() + " - wait 100 ticks before blockport"));
        return 1;
    }

    private static String answers(BlockProtectionPort port, ServerPlayer player, LivingEntity mob, ServerLevel level, ChunkPos chunk) {
        level.getChunk(chunk.x, chunk.z);
        BlockPos pos = new BlockPos(chunk.getMiddleBlockX(), 300, chunk.getMiddleBlockZ());
        return "playerBreak=" + port.playerMayChange(player, level, pos, BlockChangeKind.BREAK)
                + " playerInteract=" + port.playerMayChange(player, level, pos, BlockChangeKind.INTERACT)
                + " mob=" + port.mobMayChange(mob, level, pos)
                + " unattributed=" + port.unattributedMayChange(level, pos);
    }

    @Nullable
    private static TestClaim testClaim(MinecraftServer server, ClaimPort claims) {
        FtbHookSavedData data = FtbHookSavedData.get(server);
        for (UUID team : claims.allTeams(server)) {
            for (ClaimPort.ChunkKey chunk : claims.claimedChunks(server, team)) {
                String key = ChunkPosKey.encode(chunk);
                if (!Level.OVERWORLD.equals(chunk.dimension()) || data.isChunkUnsettled(team, key)) {
                    continue;
                }
                UUID regionId = data.getChunkRegion(team, key);
                Region region = regionId == null ? null : data.getRegion(team, regionId);
                if (region != null && data.getChunkOwnership(team, key).protectionOverride().isEmpty()) {
                    return new TestClaim(team, chunk, region);
                }
            }
        }
        return null;
    }

    private static void print(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            LOG.info("[lcfhtest] {}", line);
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }
}
