package dev.malik.lcfhdevtest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbchunks.FTBCUtils;
import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftbchunks.util.FireSpreadHelper;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyProperty;
import dev.malik.lcftbhook.adapter.ftb.FtbChunkKeys;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /lcfhtest ftbregion <teamName>}: FTB Chunks' protection checks that
 * don't go through {@code shouldPreventInteraction} - nether portal creation,
 * a player's or an explosion's damage to item frames (armor stands, boats,
 * minecarts), and pistons / fire / fluids crossing into a claim - against a
 * real claim of the team, run twice: with the claim's Region protected (block
 * edit and non-living attack = TEAM, explosions and fake players protected)
 * and with every one of those fields at its minimum. Each check must block in
 * the first pass and allow in the second, i.e. follow the Region - except a
 * bare {@code canPlayerUse} without any chunk, which falls back to Public and
 * logs the missing-context warning. The "outsider" is a fake player with no
 * team. The Region is restored afterwards.
 */
final class FtbRegionHooksTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int Y = 120;
    private static final List<String> REGION_FIELDS = List.of(
            "block_edit_mode", "nonliving_entity_attack_mode", "allow_explosions", "allow_fake_players", "allow_fake_players_by_id");

    private final MinecraftServer server;
    private final ServerLevel level;
    private final ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
    private final UUID teamId;
    private final Region originalRegion;
    private final ClaimedChunk claim;
    private final ChunkPos claimed;
    private final ChunkPos wilderness;
    private final ServerPlayer outsider;
    private final List<String> report = new ArrayList<>();
    private int failures;
    private String pass;

    private FtbRegionHooksTest(MinecraftServer server, String teamName) {
        this.server = server;
        this.level = server.overworld();
        Team team = teamNamed(teamName).orElseThrow(() -> new IllegalArgumentException("no team with claims named " + teamName));
        this.teamId = team.getTeamId();
        FtbHookSavedData data = FtbHookSavedData.get(server);
        this.claim = manager.getOrCreateData(team).getClaimedChunks().stream()
                .filter(c -> Level.OVERWORLD.equals(c.getPos().dimension()))
                .filter(c -> testable(data, teamId, FtbChunkKeys.encode(c.getPos())))
                // a border chunk first, so the fluid case has a neighbour that isn't the team's
                .min(Comparator.comparing((ClaimedChunk c) -> !hasForeignNeighbour(c)).thenComparing(c -> c.getPos().toString())).orElseThrow(() -> new IllegalStateException("team has no settled overworld chunk with a stored region and no private owner"));
        String key = FtbChunkKeys.encode(claim.getPos());
        this.originalRegion = data.getRegion(teamId, data.getChunkRegion(teamId, key));
        this.claimed = new ChunkPos(claim.getPos().x(), claim.getPos().z());
        this.wilderness = findWilderness(claimed);
        this.outsider = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("lcfh-devtest-outsider".getBytes()), "lcfh_outsider"));
    }

    static int run(CommandSourceStack source, String teamName) {
        FtbRegionHooksTest test;
        try {
            test = new FtbRegionHooksTest(source.getServer(), teamName);
        } catch (RuntimeException e) {
            source.sendFailure(Component.literal("setup failed: " + e));
            return 0;
        }
        test.runAll();
        for (String line : test.report) {
            LOG.info("[lcfhtest] {}", line);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return test.failures == 0 ? 1 : 0;
    }

    private void runAll() {
        report.add("claim " + claimed + " (team " + teamId + ", region '" + originalRegion.name() + "'), wilderness " + wilderness);
        level.getChunk(claimed.x, claimed.z);
        try {
            setRegionProtected(true);
            cases("PROTECTED", true);
            setRegionProtected(false);
            cases("UNPROTECTED", false);
        } catch (RuntimeException | LinkageError e) {
            fail("ABORTED: " + e);
            LOG.error("[lcfhtest] aborted", e);
        } finally {
            FtbHookSavedData.get(server).updateRegion(teamId, originalRegion);
            report.add("region restored");
        }
        report.add(failures == 0 ? "ALL PASSED" : failures + " FAILED");
    }

    private void cases(String pass, boolean protect) {
        this.pass = pass;
        BlockPos inside = new BlockPos(claimed.getMinBlockX() + 8, Y, claimed.getMinBlockZ() + 8);

        // The reference: FTB's own block edit verdict for the outsider through shouldPreventInteraction.
        boolean editBlocked = manager.shouldPreventInteraction(outsider, InteractionHand.MAIN_HAND, inside, Protection.EDIT_BLOCK, null);
        if (editBlocked != protect) {
            fail("SETUP " + pass + ": FTB's own block edit check says blocked=" + editBlocked);
        }

        netherPortal(protect, inside);

        ItemFrame frame = new ItemFrame(level, inside, Direction.UP);
        record("player attacks item frame", protect, FTBCUtils.shouldProtectNonLivingEntity(frame, level.damageSources().playerAttack(outsider)));
        record("explosion hits item frame", protect, FTBCUtils.shouldProtectNonLivingEntity(frame, level.damageSources().explosion((Entity) null, null)));

        record("piston moves blocks into claim", protect,
                (Boolean) invokeStatic("dev.ftb.mods.ftbchunks.util.PistonHelper", "prevent",
                        new Class<?>[]{PrivacyProperty.class, ClaimedChunk.class, ClaimedChunk.class},
                        FTBChunksProperties.BLOCK_EDIT_MODE, null, claim));

        boolean fireProtection = FTBChunksWorldConfig.FIRE_SPREAD_PROTECTION.get();
        try {
            FTBChunksWorldConfig.FIRE_SPREAD_PROTECTION.set(true);
            BlockPos from = new BlockPos(wilderness.getMinBlockX() + 8, Y, wilderness.getMinBlockZ() + 8);
            record("fire spreads into claim", protect, FireSpreadHelper.shouldPreventFireSpread(level, from, inside));
        } finally {
            FTBChunksWorldConfig.FIRE_SPREAD_PROTECTION.set(fireProtection);
        }

        fluidFlow(protect);

        // A caller that asks canPlayerUse without a chunk can't get region settings applied: it reads
        // Public (allowed) in both passes, and the log gets one "checked FTB Chunks' ... without saying
        // which chunk" warning naming this method.
        record("canPlayerUse without a chunk (Public fallback, warns once)", false,
                !claim.getTeamData().canPlayerUse(outsider, FTBChunksProperties.BLOCK_EDIT_MODE));
    }

    private void netherPortal(boolean protect, BlockPos inside) {
        Class<?> portals;
        try {
            portals = Class.forName("dev.ftb.mods.ftbchunks.util.ClaimRespectingNetherPortals");
        } catch (ClassNotFoundException e) {
            report.add("SKIP " + pass + " | nether portal into claim | FTB Chunks without portal protection");
            return;
        }
        try {
            if ("ALLOW".equals(String.valueOf(portalCreationSetting()))) {
                report.add("SKIP " + pass + " | nether portal into claim | nether_portal_creation = allow");
                return;
            }
            Object result = portals.getMethod("tryCreatePortal", Entity.class, ServerLevel.class, BlockPos.class).invoke(null, outsider, level, inside);
            String type = ((Enum<?>) result.getClass().getMethod("type").invoke(result)).name();
            record("nether portal into claim (" + type + ")", protect, !"ALLOW".equals(type));
        } catch (ReflectiveOperationException e) {
            fail(pass + " | nether portal into claim | " + unwrap(e));
        }
    }

    /** Water flowing from a neighbour chunk that isn't the team's into the claim. */
    private void fluidFlow(boolean protect) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos into = switch (dir) {
                case EAST -> new BlockPos(claimed.getMinBlockX(), Y, claimed.getMinBlockZ() + 8);
                case WEST -> new BlockPos(claimed.getMaxBlockX(), Y, claimed.getMinBlockZ() + 8);
                case SOUTH -> new BlockPos(claimed.getMinBlockX() + 8, Y, claimed.getMinBlockZ());
                default -> new BlockPos(claimed.getMinBlockX() + 8, Y, claimed.getMaxBlockZ());
            };
            BlockPos from = into.relative(dir.getOpposite());
            ClaimedChunk source = manager.getChunk(new ChunkDimPos(level, from));
            if (source != null && source.getTeamData().getTeam().getTeamId().equals(teamId)) {
                continue;
            }
            Map<Direction, FluidState> result = new EnumMap<>(Direction.class);
            result.put(dir, Fluids.WATER.defaultFluidState());
            invokeStatic("dev.ftb.mods.ftbchunks.util.FlowingFluidHelper", "checkDir",
                    new Class<?>[]{Level.class, BlockPos.class, Map.class, ClaimedChunk.class, Direction.class, PrivacyProperty.class},
                    level, from, result, source, dir, FTBChunksProperties.BLOCK_EDIT_MODE);
            record("water flows into claim", protect, !result.containsKey(dir));
            return;
        }
        report.add("SKIP " + pass + " | water flows into claim | every neighbour chunk is the team's");
    }

    // ---- helpers ----------------------------------------------------------------------------

    private void record(String what, boolean expectedBlocked, boolean blocked) {
        boolean ok = expectedBlocked == blocked;
        if (!ok) {
            failures++;
        }
        report.add((ok ? "PASS " : "FAIL ") + pass + " | " + what + " | expected "
                + (expectedBlocked ? "blocked" : "allowed") + ", got " + (blocked ? "blocked" : "allowed"));
    }

    private void fail(String line) {
        failures++;
        report.add("FAIL " + line);
    }

    private void setRegionProtected(boolean protect) {
        Region region = originalRegion;
        for (String id : REGION_FIELDS) {
            ProtectionProperty property = ProtectionProperty.byId(id);
            String value = protect ? (property.isPrivacyMode() ? "TEAM" : "false") : property.minimumSerialized();
            region = region.withProperty(id, value);
        }
        FtbHookSavedData.get(server).updateRegion(teamId, region);
    }

    /** {@code FTBChunksWorldConfig.PORTAL_CREATION} - by reflection, older FTB Chunks (also compiled against) has none. */
    private static Object portalCreationSetting() throws ReflectiveOperationException {
        Object value = FTBChunksWorldConfig.class.getField("PORTAL_CREATION").get(null);
        Method get = value.getClass().getMethod("get");
        get.setAccessible(true);
        return get.invoke(value);
    }

    /** A private static FTB helper, by reflection. */
    @Nullable
    private static Object invokeStatic(String className, String name, Class<?>[] types, Object... args) {
        try {
            Method method = Class.forName(className).getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("reflection " + className + "." + name + ": " + unwrap(e), e);
        }
    }

    private static String unwrap(ReflectiveOperationException e) {
        return String.valueOf(e instanceof InvocationTargetException ite ? ite.getCause() : e);
    }

    /** The team with claims whose display name or short name is {@code name} (case-insensitive). */
    private static Optional<Team> teamNamed(String name) {
        return FTBTeamsAPI.api().getManager().getTeams().stream()
                .filter(t -> t.getName().getString().equalsIgnoreCase(name) || t.getShortName().equalsIgnoreCase(name))
                .filter(t -> !FTBChunksAPI.api().getManager().getOrCreateData(t).getClaimedChunks().isEmpty())
                .findFirst();
    }

    /** Settled, assigned to a stored region, and no private-owner override that would loosen it. */
    private static boolean testable(FtbHookSavedData data, UUID teamId, String key) {
        if (data.isChunkUnsettled(teamId, key)) {
            return false;
        }
        UUID regionId = data.getChunkRegion(teamId, key);
        ChunkOwnership ownership = data.getChunkOwnership(teamId, key);
        return regionId != null && data.getRegion(teamId, regionId) != null && ownership.protectionOverride().isEmpty();
    }

    private boolean hasForeignNeighbour(ClaimedChunk chunk) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            ClaimedChunk neighbour = manager.getChunk(new ChunkDimPos(chunk.getPos().dimension(),
                    chunk.getPos().x() + dir.getStepX(), chunk.getPos().z() + dir.getStepZ()));
            if (neighbour == null || !neighbour.getTeamData().getTeam().getTeamId().equals(teamId)) {
                return true;
            }
        }
        return false;
    }

    private ChunkPos findWilderness(ChunkPos from) {
        for (int radius = 1; radius < 32; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (manager.getChunk(new ChunkDimPos(Level.OVERWORLD, from.x + dx, from.z + dz)) == null) {
                        return new ChunkPos(from.x + dx, from.z + dz);
                    }
                }
            }
        }
        throw new IllegalStateException("no unclaimed chunk near " + from);
    }
}
