package dev.malik.lcftbhook.adapter.ftb;

import dev.malik.lcftbhook.port.protection.ProtectionFieldSet;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec;
import dev.malik.lcftbhook.port.protection.ProtectionFieldSpec.Role;

import java.util.List;

/**
 * Every protection setting FTB Chunks enforces, as region fields. The ids are
 * FTB Chunks' own team property ids (without namespace) - the FTB mixins
 * match FTB's property reads against them by id. Deliberately free of FTB
 * imports: the config reads this list at mod construction.
 * <p>
 * Not included: claim_visibility / location_mode (not protections) and
 * allow_named_fake_players (a name list FTB keeps editing itself).
 */
public final class FtbProtectionFields {
    public static final String ALLOW_MOB_GRIEFING = "allow_mob_griefing";
    public static final String ALLOW_EXPLOSIONS = "allow_explosions";
    public static final String ALLOW_PVP = "allow_pvp";
    public static final String ALLOW_ALL_FAKE_PLAYERS = "allow_fake_players";
    public static final String ALLOW_FAKE_PLAYERS_BY_ID = "allow_fake_players_by_id";

    public static final ProtectionFieldSet SET = new ProtectionFieldSet("ftb", "FTB Chunks", List.of(
            ProtectionFieldSpec.access("entity_interact_mode", true, 1000L,
                    "Who may interact with living entities (villagers, animals, ...).", Role.ENTITY_INTERACT).asPrimary(),
            ProtectionFieldSpec.access("nonliving_entity_attack_mode", true, 1000L,
                    "Who may attack/break non-living entities (item frames, armor stands, paintings, ...)."),
            ProtectionFieldSpec.access("block_edit_mode", true, 1000L,
                    "Who may place or break blocks.", Role.BLOCK_EDIT).asPrimary(),
            ProtectionFieldSpec.access("block_interact_mode", true, 1000L,
                    "Who may right-click blocks (doors, chests, levers, ...).", Role.BLOCK_INTERACT).asPrimary(),
            ProtectionFieldSpec.toggle(ALLOW_MOB_GRIEFING, 800L, "Mob griefing protection.", Role.MOB_GRIEFING).asPrimary(),
            ProtectionFieldSpec.toggle(ALLOW_EXPLOSIONS, 700L, "Explosion protection.", Role.EXPLOSIONS).asPrimary(),
            ProtectionFieldSpec.toggle(ALLOW_PVP, 500L, "PvP protection.", Role.PVP).asPrimary(),
            ProtectionFieldSpec.toggle(ALLOW_ALL_FAKE_PLAYERS, 500L,
                    "Blocks fake players (machines like deployers) that aren't otherwise allowed."),
            ProtectionFieldSpec.toggle(ALLOW_FAKE_PLAYERS_BY_ID, 300L,
                    "Also blocks fake players acting under the UUID of a player who would have access.")
    ), List.of("entity_interact_mode", "block_edit_mode", "block_interact_mode", ALLOW_MOB_GRIEFING, ALLOW_EXPLOSIONS, ALLOW_PVP));

    private FtbProtectionFields() {
    }
}
