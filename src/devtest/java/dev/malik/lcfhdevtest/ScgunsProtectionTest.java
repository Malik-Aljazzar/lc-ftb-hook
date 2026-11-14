package dev.malik.lcfhdevtest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.malik.lcftbhook.adapter.ftb.FtbChunkKeys;
import dev.malik.lcftbhook.data.ChunkOwnership;
import dev.malik.lcftbhook.data.FtbHookSavedData;
import dev.malik.lcftbhook.data.ProtectionProperty;
import dev.malik.lcftbhook.data.Region;
import dev.malik.lcftbhook.port.protection.BlockChangeKind;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;
import top.ribs.scguns.block.PenetratorBlock;
import top.ribs.scguns.blockentity.PenetratorBlockEntity;
import top.ribs.scguns.common.BeamHandlerCommon;
import top.ribs.scguns.common.FireMode;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.entity.monster.ScampRocketEntity;
import top.ribs.scguns.entity.monster.ScampTankEntity;
import top.ribs.scguns.entity.projectile.BearPackShellProjectileEntity;
import top.ribs.scguns.entity.projectile.FireRoundEntity;
import top.ribs.scguns.entity.projectile.KrahgRoundProjectileEntity;
import top.ribs.scguns.entity.projectile.ProjectileEntity;
import top.ribs.scguns.init.ModBlocks;
import top.ribs.scguns.init.ModEntities;
import top.ribs.scguns.item.GunItem;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Drives every guarded ScorchedGuns block change against a real FTB claim of
 * the team named {@code <teamName>} on the dev server and checks the block
 * afterwards. "member" = a fake player with the team owner's UUID.
 * Three passes: the claim's Region with block edit/interact = TEAM and
 * explosions/mob griefing protected, the same Region fully unprotected, and an
 * unclaimed chunk. Expected outcome per case: for a player shooter whatever
 * FTB Chunks itself decides for that (fake) player at that block; for a mob or
 * no shooter "kept" only in the protected pass. The Region is restored
 * afterwards; test blocks are built at y=300 and removed again.
 */
final class ScgunsProtectionTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int Y = 300;
    private static final List<String> REGION_FIELDS = List.of("block_edit_mode", "block_interact_mode", "allow_explosions", "allow_mob_griefing");

    private enum Pass { PROTECTED, UNPROTECTED, WILDERNESS }

    /** A shooter; {@code entity} null = a bullet nobody fired (the shooter field is cleared). */
    private record Shooter(String name, @Nullable LivingEntity entity) {
    }

    private final MinecraftServer server;
    private final ServerLevel level;
    private final UUID teamId;
    private final Region originalRegion;
    private final ChunkPos claimed;
    private final ChunkPos wilderness;
    private final ServerPlayer outsider;
    private final ServerPlayer member;
    private final LivingEntity zombie;
    private final List<String> report = new ArrayList<>();
    private int failures;
    private Pass pass;
    private ChunkPos chunk;
    private int slot;

    private ScgunsProtectionTest(MinecraftServer server, String ownerName) {
        this.server = server;
        this.level = server.overworld();
        Team team = teamNamed(ownerName).orElseThrow(() -> new IllegalArgumentException("no team with claims named " + ownerName));
        GameProfile owner = new GameProfile(team.getOwner(), "lcfh_member");
        UUID id = team.getTeamId();
        this.teamId = id;
        FtbHookSavedData data = FtbHookSavedData.get(server);
        ClaimedChunk claim = FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().stream()
                .filter(c -> Level.OVERWORLD.equals(c.getPos().dimension()))
                .filter(c -> testable(data, id, FtbChunkKeys.encode(c.getPos())))
                .findFirst().orElseThrow(() -> new IllegalStateException("team has no settled overworld chunk with a stored region and no private owner"));
        String key = FtbChunkKeys.encode(claim.getPos());
        this.originalRegion = data.getRegion(teamId, data.getChunkRegion(teamId, key));
        this.claimed = new ChunkPos(claim.getPos().x(), claim.getPos().z());
        this.wilderness = findWilderness(claimed);
        this.outsider = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("lcfh-devtest-outsider".getBytes()), "lcfh_outsider"));
        this.member = FakePlayerFactory.get(level, new GameProfile(owner.getId(), owner.getName()));
        this.zombie = EntityType.ZOMBIE.create(level);
    }

    static int run(CommandSourceStack source, String ownerName) {
        ScgunsProtectionTest test;
        try {
            test = new ScgunsProtectionTest(source.getServer(), ownerName);
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
        try {
            setRegionProtected(true);
            suite(Pass.PROTECTED, claimed);
            setRegionProtected(false);
            suite(Pass.UNPROTECTED, claimed);
            suite(Pass.WILDERNESS, wilderness);
        } catch (RuntimeException | LinkageError e) {
            fail("ABORTED: " + e);
            LOG.error("[lcfhtest] aborted", e);
        } finally {
            FtbHookSavedData.get(server).updateRegion(teamId, originalRegion);
            report.add("region restored");
        }
        report.add(failures == 0 ? "ALL PASSED" : failures + " FAILED");
    }

    private void suite(Pass pass, ChunkPos chunk) {
        this.pass = pass;
        this.chunk = chunk;
        this.slot = 0;
        level.getChunk(chunk.x, chunk.z);
        Shooter outsiderShooter = new Shooter("outsider", outsider);
        Shooter memberShooter = new Shooter("member(fake)", member);
        Shooter mob = new Shooter("zombie", zombie);
        Shooter nobody = new Shooter("no shooter", null);
        for (Shooter shooter : List.of(outsiderShooter, memberShooter, mob, nobody)) {
            fragileGlass(shooter);
        }
        for (Shooter shooter : List.of(outsiderShooter, mob, nobody)) {
            heavyRound(shooter);
        }
        for (Shooter shooter : List.of(outsiderShooter, mob)) {
            primeTnt(shooter);
            doorShot(shooter);
        }
        fireRound(outsiderShooter);
        beamMining(outsider);
        beamMining(member);
        scampTank();
        scampRocket();
        penetrator();
    }

    // ---- cases ----------------------------------------------------------------------------

    private void fragileGlass(Shooter shooter) {
        BlockPos pos = nextSlot();
        place(pos, Blocks.GLASS.defaultBlockState());
        boolean expected = expected(shooter.entity(), pos, BlockChangeKind.BREAK);
        for (int shot = 0; shot < 30 && level.getBlockState(pos).is(Blocks.GLASS); shot++) {
            hit(bullet(() -> new ProjectileEntity(ModEntities.PROJECTILE.get(), level, standIn(shooter), Guns.bulletStack, Guns.bullet, Guns.bulletGun), shooter), pos);
        }
        record("fragile glass", shooter.name(), expected, !level.getBlockState(pos).is(Blocks.GLASS));
        clear(pos);
    }

    private void heavyRound(Shooter shooter) {
        BlockPos pos = nextSlot();
        place(pos, Blocks.STONE.defaultBlockState());
        boolean expected = expected(shooter.entity(), pos, BlockChangeKind.BREAK);
        // Heavy rounds only break a block with a 65% chance per hit (ScorchedGuns' own roll).
        for (int shot = 0; shot < 20 && level.getBlockState(pos).is(Blocks.STONE); shot++) {
            hit(bullet(() -> new KrahgRoundProjectileEntity(ModEntities.KRAHG_ROUND_PROJECTILE.get(), level, standIn(shooter), Guns.bulletStack, Guns.bullet, Guns.bulletGun), shooter), pos);
        }
        record("heavy round on stone", shooter.name(), expected, !level.getBlockState(pos).is(Blocks.STONE));
        clear(pos);
    }

    private void primeTnt(Shooter shooter) {
        BlockPos pos = nextSlot();
        place(pos, Blocks.TNT.defaultBlockState());
        boolean expected = expected(shooter.entity(), pos, BlockChangeKind.BREAK);
        hit(bullet(() -> new ProjectileEntity(ModEntities.PROJECTILE.get(), level, standIn(shooter), Guns.bulletStack, Guns.bullet, Guns.bulletGun), shooter), pos);
        record("bullet primes TNT", shooter.name(), expected, !level.getBlockState(pos).is(Blocks.TNT));
        clear(pos);
    }

    private void doorShot(Shooter shooter) {
        BlockPos pos = nextSlot();
        place(pos, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        place(pos.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        boolean expected = expected(shooter.entity(), pos, BlockChangeKind.INTERACT);
        hit(bullet(() -> new BearPackShellProjectileEntity(ModEntities.BEARPACK_SHELL_PROJECTILE.get(), level, standIn(shooter), Guns.bulletStack, Guns.bullet, Guns.bulletGun), shooter), pos);
        BlockState after = level.getBlockState(pos);
        record("bear pack shell opens door", shooter.name(), expected, after.is(Blocks.OAK_DOOR) && after.getValue(DoorBlock.OPEN));
        clear(pos.above());
        clear(pos);
    }

    private void fireRound(Shooter shooter) {
        BlockPos pos = nextSlot();
        place(pos, Blocks.NETHERRACK.defaultBlockState());
        boolean expected = expected(shooter.entity(), pos.above(), BlockChangeKind.BREAK);
        hit(bullet(() -> new FireRoundEntity(ModEntities.FIRE_ROUND_PROJECTILE.get(), level, standIn(shooter), Guns.bulletStack, Guns.bullet, Guns.bulletGun), shooter), pos);
        record("fire round sets fire", shooter.name(), expected, level.getBlockState(pos.above()).is(Blocks.FIRE));
        clear(pos.above());
        clear(pos);
    }

    private void beamMining(ServerPlayer player) {
        BlockPos pos = nextSlot();
        place(pos, Blocks.STONE.defaultBlockState());
        boolean expected = expected(player, pos, BlockChangeKind.BREAK);
        if (Guns.laserGun == null || !Guns.laserGun.getGeneral().canMine()) {
            fail("SETUP beam mining: no mining laser gun data");
            clear(pos);
            return;
        }
        for (int tick = 0; tick < 400 && level.getBlockState(pos).is(Blocks.STONE); tick++) {
            BeamHandlerCommon.BeamMiningManager.updateBlockMining(level, pos, player, Guns.laserGun);
        }
        record("beam mining stone", player == outsider ? "outsider" : "member(fake)", expected, !level.getBlockState(pos).is(Blocks.STONE));
        clear(pos);
    }

    private void scampTank() {
        BlockPos pos = nextSlot();
        place(pos, Blocks.BARREL.defaultBlockState());
        ScampTankEntity tank = ModEntities.SCAMP_TANK.get().create(level);
        tank.setPos(Vec3.atCenterOf(pos).add(0, 2, 0));
        boolean expected = expected(tank, pos, BlockChangeKind.BREAK);
        invoke(tank, ScampTankEntity.class, "destroyBlock", new Class<?>[]{BlockPos.class, BlockState.class, boolean.class},
                pos, level.getBlockState(pos), false);
        record("scamp tank crushes barrel", "scamp tank", expected, !level.getBlockState(pos).is(Blocks.BARREL));
        clear(pos);
    }

    private void scampRocket() {
        BlockPos pos = nextSlot();
        place(pos, Blocks.GRANITE.defaultBlockState());
        ScampRocketEntity rocket = new ScampRocketEntity(ModEntities.SCAMP_ROCKET.get(), level, zombie);
        rocket.setPos(Vec3.atCenterOf(pos));
        boolean expected = expected(zombie, pos, BlockChangeKind.BREAK);
        for (int attempt = 0; attempt < 40 && level.getBlockState(pos).is(Blocks.GRANITE); attempt++) {
            invoke(rocket, ScampRocketEntity.class, "destroyBreakableBlocks", new Class<?>[0]);
        }
        record("scamp rocket breaks granite", "rocket of zombie", expected, !level.getBlockState(pos).is(Blocks.GRANITE));
        clear(pos);
    }

    private void penetrator() {
        BlockPos origin = new BlockPos(chunk.getMinBlockX() + 1, Y, chunk.getMinBlockZ() + 14);
        BlockState state = ModBlocks.PENETRATOR.get().defaultBlockState().setValue(PenetratorBlock.FACING, Direction.EAST);
        place(origin, state);
        for (int i = 1; i <= 3; i++) {
            place(origin.east(i), Blocks.STONE.defaultBlockState());
        }
        boolean expected = expected(null, origin.east(2), BlockChangeKind.BREAK);
        if (!(level.getBlockEntity(origin) instanceof PenetratorBlockEntity)) {
            fail("SETUP penetrator: no block entity");
        }
        // Powering it fires it right away (PenetratorBlock.neighborChanged -> PenetratorBlockEntity.tick).
        place(origin.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        if (level.getBlockEntity(origin) instanceof PenetratorBlockEntity stillThere) {
            PenetratorBlockEntity.tick(level, origin, state, stillThere);
        }
        record("penetrator tunnel", "no one (machine)", expected, !level.getBlockState(origin.east(2)).is(Blocks.STONE));
        for (int i = 0; i <= 3; i++) {
            clear(origin.east(i));
        }
        clear(origin.below());
    }

    // ---- helpers ----------------------------------------------------------------------------

    /** Claim mod's own verdict for a player; otherwise kept only in the protected pass. */
    private boolean expected(@Nullable Entity cause, BlockPos pos, BlockChangeKind kind) {
        if (cause instanceof ServerPlayer player) {
            Protection protection = kind == BlockChangeKind.INTERACT ? Protection.INTERACT_BLOCK : Protection.EDIT_BLOCK;
            boolean allowed = !FTBChunksAPI.api().getManager().shouldPreventInteraction(player, InteractionHand.MAIN_HAND, pos, protection, null);
            if (player == outsider && allowed == (pass == Pass.PROTECTED)) {
                fail("SETUP " + pass + ": FTB says outsider allowed=" + allowed + " at " + pos.toShortString());
            }
            return allowed;
        }
        return pass != Pass.PROTECTED;
    }

    private void record(String what, String who, boolean expectedChanged, boolean changed) {
        boolean ok = expectedChanged == changed;
        if (!ok) {
            failures++;
        }
        report.add((ok ? "PASS " : "FAIL ") + pass + " | " + what + " | " + who + " | expected "
                + (expectedChanged ? "changed" : "kept") + ", got " + (changed ? "changed" : "kept"));
    }

    private void fail(String line) {
        failures++;
        report.add("FAIL " + line);
    }

    private ProjectileEntity bullet(Supplier<ProjectileEntity> factory, Shooter shooter) {
        ProjectileEntity bullet = factory.get();
        if (shooter.entity() == null) {
            setField(bullet, ProjectileEntity.class, "shooter", null);
        }
        return bullet;
    }

    private LivingEntity standIn(Shooter shooter) {
        return shooter.entity() != null ? shooter.entity() : outsider;
    }

    private void hit(ProjectileEntity bullet, BlockPos pos) {
        Vec3 top = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        bullet.onHit(new BlockHitResult(top, Direction.UP, pos, false), top.add(0, 2, 0), top);
    }

    private BlockPos nextSlot() {
        int index = slot++;
        return new BlockPos(chunk.getMinBlockX() + 1 + (index % 7) * 2, Y, chunk.getMinBlockZ() + 1 + (index / 7) * 4);
    }

    private void place(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
    }

    private void clear(BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        AABB area = new AABB(pos).inflate(12);
        for (Entity leftover : level.getEntitiesOfClass(Entity.class, area, e -> e instanceof ItemEntity || e instanceof PrimedTnt)) {
            leftover.discard();
        }
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

    /** The team with claims whose display name or short name is {@code name} (case-insensitive). */
    private static java.util.Optional<Team> teamNamed(String name) {
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

    private ChunkPos findWilderness(ChunkPos from) {
        for (int radius = 1; radius < 32; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkDimPos candidate = new ChunkDimPos(Level.OVERWORLD, from.x + dx, from.z + dz);
                    if (FTBChunksAPI.api().getManager().getChunk(candidate) == null) {
                        return new ChunkPos(candidate.x(), candidate.z());
                    }
                }
            }
        }
        throw new IllegalStateException("no unclaimed chunk near " + from);
    }

    private static void invoke(Object target, Class<?> owner, String name, Class<?>[] types, Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("reflection " + name + ": " + e, e);
        }
    }

    private static void setField(Object target, Class<?> owner, String name, @Nullable Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("reflection " + name + ": " + e, e);
        }
    }

    /** The guns the test fires: any projectile gun for bullets, the CR4K mining laser for beams. */
    private static final class Guns {
        static final GunItem bullet = firstProjectileGun();
        static final ItemStack bulletStack = new ItemStack(bullet);
        static final Gun bulletGun = bullet.getModifiedGun(bulletStack);
        @Nullable
        static final Gun laserGun = laserGun();

        private static GunItem firstProjectileGun() {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof GunItem gun) {
                    FireMode mode = gun.getModifiedGun(new ItemStack(gun)).getGeneral().getFireMode();
                    if (mode != FireMode.BEAM && mode != FireMode.SEMI_BEAM) {
                        return gun;
                    }
                }
            }
            throw new IllegalStateException("no ScorchedGuns projectile gun registered");
        }

        @Nullable
        private static Gun laserGun() {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("scguns", "cr4k_mining_laser"));
            return item instanceof GunItem gun ? gun.getModifiedGun(new ItemStack(gun)) : null;
        }
    }
}
