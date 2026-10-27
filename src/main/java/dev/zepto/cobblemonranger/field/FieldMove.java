package dev.zepto.cobblemonranger.field;

import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.moves.Move;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Obstacles that friend Pokémon (and the Partner Pokémon) can clear in the field. A Pokémon can help if it has one of
 * the move's types or knows the move itself.
 */
public enum FieldMove {
    CUT("cut", Set.of("grass", "bug", "steel", "dark"), Set.of("cut", "furycutter", "leafblade", "xscissor", "slash")),
    ROCK_SMASH("rock_smash", Set.of("fighting", "rock", "ground", "steel"),
            Set.of("rocksmash", "brickbreak", "strength", "rockslide")),
    SOAK("soak", Set.of("water", "ice"), Set.of("soak", "watergun", "surf", "hydropump", "bubblebeam")),
    SURF("surf", Set.of("water"), Set.of("surf", "waterfall", "dive"));

    private final String id;
    private final Set<String> types;
    private final Set<String> moves;

    FieldMove(String id, Set<String> types, Set<String> moves) {
        this.id = id;
        this.types = types;
        this.moves = moves;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("field_move.cobblemonranger." + id);
    }

    public Component typeHint() {
        return Component.translatable("field_move.cobblemonranger." + id + ".types");
    }

    public boolean canUse(Pokemon pokemon) {
        for (ElementalType type : pokemon.getTypes()) {
            if (types.contains(type.getName().toLowerCase(Locale.ROOT))) {
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
        for (FieldMove move : values()) {
            if (move.id.equals(id)) {
                return move;
            }
        }
        return null;
    }
}
