package dev.zepto.cobblemonranger.field;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;

/**
 * One field move per Pokémon type. A Pokémon can help if it has the move's type or knows one of the listed moves by
 * name. {@link #targeted} moves are aimed at a block (right-click it with the styler); the others are picked from the
 * move picker (sneak + right-click the air) and act around the player.
 */
public enum FieldMove {
    HEADBUTT("headbutt", "normal", true, Set.of("headbutt", "bodyslam", "takedown", "doubleedge", "gigaimpact")),
    BURN("burn", "fire", true, Set.of("ember", "flamethrower", "fireblast", "flamewheel", "firespin", "heatwave")),
    SURF("surf", "water", true, Set.of("surf", "waterfall", "dive", "watergun", "hydropump", "bubblebeam")),
    CUT("cut", "grass", true, Set.of("cut", "furycutter", "leafblade", "xscissor", "slash", "razorleaf")),
    FLASH("flash", "electric", false, Set.of("flash", "thunderbolt", "thundershock", "charge", "spark")),
    FREEZE("freeze", "ice", true, Set.of("icebeam", "blizzard", "powdersnow", "icywind", "freezedry")),
    ROCK_SMASH("rock_smash", "fighting", true, Set.of("rocksmash", "brickbreak", "strength", "rockslide")),
    DECOMPOSE("decompose", "poison", false, Set.of("acid", "sludge", "sludgebomb", "toxic", "acidspray")),
    DIG("dig", "ground", true, Set.of("dig", "mudslap", "bulldoze", "earthquake", "drillrun")),
    TAILWIND("tailwind", "flying", false, Set.of("tailwind", "gust", "whirlwind", "airslash", "hurricane")),
    TELEKINESIS("telekinesis", "psychic", true, Set.of("telekinesis", "confusion", "psychic", "psybeam", "kinesis")),
    STRING_SHOT("string_shot", "bug", false, Set.of("stringshot", "electroweb", "stickyweb", "spiderweb")),
    STONE_EDGE("stone_edge", "rock", true, Set.of("stoneedge", "rockthrow", "rocktomb", "powergem", "rockpolish")),
    SPOOK("spook", "ghost", false, Set.of("astonish", "scaryface", "shadowball", "lick", "nightshade")),
    OUTRAGE("outrage", "dragon", true, Set.of("outrage", "dragonclaw", "dragonrush", "dragonpulse", "dracometeor")),
    SNATCH("snatch", "dark", false, Set.of("snatch", "thief", "covet", "knockoff", "pluck")),
    MAGNET("magnet", "steel", false, Set.of("magnetrise", "irondefense", "flashcannon", "magnetbomb", "ironhead")),
    CHARM("charm", "fairy", false, Set.of("charm", "attract", "sweetkiss", "babydolleyes", "drainingkiss"));

    private final String id;
    private final String type;
    private final boolean targeted;
    private final Set<String> moves;

    FieldMove(String id, String type, boolean targeted, Set<String> moves) {
        this.id = id;
        this.type = type;
        this.targeted = targeted;
        this.moves = moves;
    }

    public String id() {
        return id;
    }

    /** The Pokémon type this move belongs to, lower case (e.g. {@code "fire"}). */
    public String type() {
        return type;
    }

    /** Aimed at a block with a right-click, rather than picked from the move picker. */
    public boolean targeted() {
        return targeted;
    }

    public Component displayName() {
        return Component.translatable("field_move.cobblemonranger." + id);
    }

    public Component typeHint() {
        return Component.translatable("type.cobblemonranger." + type);
    }

    public Component description() {
        return Component.translatable("field_move.cobblemonranger." + id + ".desc");
    }

    public boolean canUse(Pokemon pokemon) {
        for (ElementalType t : pokemon.getTypes()) {
            if (type.equals(t.getName().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        MoveSet moveSet = pokemon.getMoveSet();
        for (Move move : moveSet.getMoves()) {
            if (moves.contains(move.getName().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static FieldMove byId(String id) {
        if ("soak".equals(id)) {
            // Soak was split into Surf (fire) and Freeze (lava); old missions keep working as Surf.
            return SURF;
        }
        for (FieldMove move : values()) {
            if (move.id.equals(id)) {
                return move;
            }
        }
        return null;
    }
}
