package dev.malik.lcftbhook.adapter.opac;

import dev.malik.lcftbhook.port.protection.ProtectionFieldSet;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every protection option OPAC's player config offers, as region fields,
 * each mapped to one OPAC (v2 API) option: access fields to exception groups,
 * toggles to booleans. Left out: the master switch and the full-access group
 * (set by us), the "redirect" and "reclaimable" options (behaviour, not protection).
 * Deliberately free of OPAC imports: the config reads this list at mod construction.
 */
public final class OpacProtectionFields {
    /** How an OPAC (v2 API) option stores its value. */
    public enum OptionKind {
        /** Exception group - who is exempt: "E"veryone, "A"llies, "P"arty, "N"obody. */
        GROUP,
        /** Boolean exception - true = allowed (not protected). */
        EXCEPTION,
        /** Boolean protection - true = protected. */
        PROTECTION
    }

    /** @param optionId OPAC's (v2) option id without the "playerConfig." root, as in OPAC's server config lists */
    public record OpacField(ProtectionFieldSpec spec, String optionId, OptionKind optionKind) {
    }

    private static final String EX = "claims.protection.exceptions.";
    private static final String PROT = "claims.protection.";
    private static final List<OpacField> FIELDS = new ArrayList<>();

    static {
        primaryAccess("opac_blocks_from_players", EX + "blocksByPlayers", true, 1000L,
                "Players breaking/placing/using blocks.", Role.BLOCK_EDIT, Role.BLOCK_INTERACT);
        primaryAccess("opac_item_use", EX + "itemUse", true, 1000L, "Players using items (buckets, spawn eggs, ...) in the claim.");
        primaryAccess("opac_entities_from_players", EX + "entitiesByPlayers", true, 1000L,
                "Players interacting with or hurting entities.", Role.ENTITY_INTERACT);
        primaryToggle("opac_players_from_players", EX + "playersByPlayers", OptionKind.EXCEPTION, 500L, "PvP.", Role.PVP);
        primaryToggle("opac_blocks_from_explosions", EX + "blocksByExplosions", OptionKind.EXCEPTION, 700L,
                "Blocks destroyed by explosions.", Role.EXPLOSIONS);
        toggle("opac_entities_from_explosions", EX + "entitiesByExplosions", OptionKind.EXCEPTION, 300L, "Entities hurt by explosions.");
        primaryAccess("opac_blocks_from_mobs", EX + "blocksByMobs", false, 800L,
                "Mobs breaking/changing blocks (mob griefing).", Role.MOB_GRIEFING);
        toggle("opac_override_mob_griefing_rule", PROT + "overrideMobGriefingRule", OptionKind.PROTECTION, 300L,
                "Overrides the mobGriefing game rule for mobs OPAC doesn't list.");
        access("opac_entities_from_mobs", EX + "entitiesByMobs", false, 300L, "Mobs hurting entities.");
        toggle("opac_players_from_mobs", EX + "playersByMobs", OptionKind.EXCEPTION, 300L, "Mobs hurting players.");
        access("opac_blocks_from_other", EX + "blocksByOther", false, 300L, "Other sources (e.g. fake players, machines) breaking blocks.");
        access("opac_entities_from_other", EX + "entitiesByOther", false, 300L, "Other sources hurting entities.");
        toggle("opac_players_from_other", EX + "playersByOther", OptionKind.EXCEPTION, 200L, "Other sources hurting players.");
        access("opac_blocks_from_enchantments", EX + "blocksByEnchantments", false, 200L, "Enchantment effects on blocks (e.g. frost walker).");
        primaryToggle("opac_from_fire_spread", EX + "fireSpread", OptionKind.EXCEPTION, 300L, "Fire spreading into the claim.");
        toggle("opac_entities_from_fire", EX + "entitiesByFire", OptionKind.EXCEPTION, 200L, "Entities hurt by fire.");
        access("opac_buttons_from_projectiles", EX + "buttonsByProjectiles", false, 100L, "Projectiles pressing buttons.");
        access("opac_targets_from_projectiles", EX + "targetsByProjectiles", false, 100L, "Projectiles hitting target blocks.");
        access("opac_plates_from_players", EX + "platesByPlayers", true, 100L, "Players activating pressure plates.");
        access("opac_plates_from_mobs", EX + "platesByMobs", false, 100L, "Mobs activating pressure plates.");
        access("opac_plates_from_other", EX + "platesByOther", false, 100L, "Other entities activating pressure plates.");
        access("opac_tripwire_from_players", EX + "tripwireByPlayers", true, 100L, "Players triggering tripwires.");
        access("opac_tripwire_from_mobs", EX + "tripwireByMobs", false, 100L, "Mobs triggering tripwires.");
        access("opac_tripwire_from_other", EX + "tripwireByOther", false, 100L, "Other entities triggering tripwires.");
        primaryToggle("opac_crop_trample", EX + "cropTrample", OptionKind.EXCEPTION, 100L, "Crop trampling.");
        access("opac_chorus_fruit_teleport", EX + "chorusFruitTeleport", true, 200L, "Teleporting into the claim with chorus fruit.");
        access("opac_nether_portals_players", EX + "netherPortalsPlayers", true, 200L, "Players using nether portals in the claim.");
        access("opac_nether_portals_mobs", EX + "netherPortalsMobs", false, 100L, "Mobs using nether portals in the claim.");
        access("opac_nether_portals_other", EX + "netherPortalsOther", false, 100L, "Other entities using nether portals in the claim.");
        access("opac_player_lightning", EX + "playerLightning", true, 200L, "Lightning caused by players (e.g. channeling tridents).");
        access("opac_item_toss_players", EX + "itemTossPlayers", true, 100L, "Players throwing items into the claim.");
        access("opac_item_toss_mobs", EX + "itemTossMobs", false, 100L, "Mobs throwing items into the claim.");
        access("opac_item_toss_other", EX + "itemTossOther", false, 100L, "Other sources throwing items into the claim.");
        access("opac_mob_loot", EX + "mobLoot", true, 200L, "Picking up loot dropped by mobs killed in the claim.");
        access("opac_player_death_loot", PROT + "playerDeathLoot", true, 200L, "Picking up items dropped by players who died in the claim.");
        access("opac_item_pickup_players", EX + "itemPickupPlayers", true, 200L, "Players picking up items.");
        access("opac_item_pickup_mobs", EX + "itemPickupMobs", false, 100L, "Mobs picking up items.");
        access("opac_xp_pickup", EX + "xpPickup", true, 100L, "Picking up experience orbs.");
        toggle("opac_neighbor_chunks_item_use", PROT + "neighborChunksItemUse", OptionKind.PROTECTION, 100L,
                "Protects against item use aimed from neighbouring chunks.");
        toggle("opac_fluid_barrier", PROT + "fluidBarrier", OptionKind.PROTECTION, 200L, "Stops fluids flowing in from outside.");
        toggle("opac_dispenser_barrier", PROT + "dispenserBarrier", OptionKind.PROTECTION, 200L, "Stops dispensers outside from affecting the claim.");
        toggle("opac_piston_barrier", PROT + "pistonBarrier", OptionKind.PROTECTION, 200L, "Stops pistons outside from pushing into the claim.");
        toggle("opac_raids", EX + "raids", OptionKind.EXCEPTION, 300L, "Raids in the claim.");
        toggle("opac_natural_spawn_hostile", EX + "naturalSpawnHostile", OptionKind.EXCEPTION, 300L, "Natural hostile mob spawning.");
        toggle("opac_natural_spawn_friendly", EX + "naturalSpawnFriendly", OptionKind.EXCEPTION, 100L, "Natural friendly mob spawning.");
        toggle("opac_spawners_hostile", EX + "spawnersHostile", OptionKind.EXCEPTION, 200L, "Hostile mobs from spawners.");
        toggle("opac_spawners_friendly", EX + "spawnersFriendly", OptionKind.EXCEPTION, 100L, "Friendly mobs from spawners.");
        access("opac_projectile_hit_hostile_spawn", EX + "projectileHitHostileSpawn", true, 100L, "Projectiles hitting hostile spawn-related entities.");
        access("opac_projectile_hit_friendly_spawn", EX + "projectileHitFriendlySpawn", true, 100L, "Projectiles hitting friendly spawn-related entities.");
    }

    public static final ProtectionFieldSet SET = new ProtectionFieldSet("opac", "Open Parties and Claims",
            FIELDS.stream().map(OpacField::spec).toList(),
            // Same idea as FTB's default: nuisances first, building/interaction next, PvP kept longest.
            List.of("opac_crop_trample", "opac_from_fire_spread", "opac_entities_from_players", "opac_item_use",
                    "opac_blocks_from_players", "opac_blocks_from_mobs", "opac_blocks_from_explosions", "opac_players_from_players"));

    private static final Map<String, OpacField> BY_ID = FIELDS.stream()
            .collect(Collectors.toMap(field -> field.spec().id(), Function.identity()));
    private static final Map<String, OpacField> BY_OPTION_ID = FIELDS.stream()
            .collect(Collectors.toMap(OpacField::optionId, Function.identity()));

    private OpacProtectionFields() {
    }

    public static List<OpacField> all() {
        return List.copyOf(FIELDS);
    }

    public static OpacField byFieldId(String fieldId) {
        return BY_ID.get(fieldId);
    }

    /** @param optionId with or without the "playerConfig." root; null if not one of ours */
    public static OpacField byOptionId(String optionId) {
        String shortId = optionId.startsWith("playerConfig.") ? optionId.substring("playerConfig.".length()) : optionId;
        return BY_OPTION_ID.get(shortId);
    }

    private static void access(String fieldId, String optionId, boolean accessList, long price, String description, Role... roles) {
        FIELDS.add(new OpacField(ProtectionFieldSpec.access(fieldId, accessList, price, description, roles), optionId, OptionKind.GROUP));
    }

    private static void toggle(String fieldId, String optionId, OptionKind kind, long price, String description, Role... roles) {
        FIELDS.add(new OpacField(ProtectionFieldSpec.toggle(fieldId, price, description, roles), optionId, kind));
    }

    private static void primaryAccess(String fieldId, String optionId, boolean accessList, long price, String description, Role... roles) {
        FIELDS.add(new OpacField(ProtectionFieldSpec.access(fieldId, accessList, price, description, roles).asPrimary(), optionId, OptionKind.GROUP));
    }

    private static void primaryToggle(String fieldId, String optionId, OptionKind kind, long price, String description, Role... roles) {
        FIELDS.add(new OpacField(ProtectionFieldSpec.toggle(fieldId, price, description, roles).asPrimary(), optionId, kind));
    }
}
