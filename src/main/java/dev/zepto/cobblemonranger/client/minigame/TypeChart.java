package dev.zepto.cobblemonranger.client.minigame;

import java.util.Locale;
import java.util.Map;

/**
 * The main-series type chart, used to decide how well a Poké Assist works on the wild Pokémon: an assist of a type
 * the Pokémon is weak to works better, one it resists works less, and one it is immune to does nothing.
 */
public final class TypeChart {
    private static final String[] TYPES = {"normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison",
            "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"};

    /**
     * One row per attacking type, one column per defending type, in the order of {@link #TYPES}:
     * 0 immune, 1 resisted, 2 neutral, 4 super effective.
     */
    private static final String CHART = ""
            // nor fir wat ele gra ice fig poi gro fly psy bug roc gho dra dar ste fai
            + "222222222222102212" // normal
            + "211244222224121242" // fire
            + "241212224222421222" // water
            + "224112220422221222" // electric
            + "214212214121421212" // grass
            + "211241224422224212" // ice
            + "422224212111402441" // fighting
            + "222242211222112204" // poison
            + "242412242021422242" // ground
            + "222142422224122212" // flying
            + "222222442212222012" // psychic
            + "212242112142212411" // bug
            + "242224121424222212" // rock
            + "022222222242242122" // ghost
            + "222222222222224210" // dragon
            + "222222122242242121" // dark
            + "211124222222422214" // steel
            + "212222412222224412"; // fairy

    private static final Map<String, Integer> INDEX;

    static {
        java.util.HashMap<String, Integer> index = new java.util.HashMap<>();
        for (int i = 0; i < TYPES.length; i++) {
            index.put(TYPES[i], i);
        }
        INDEX = Map.copyOf(index);
    }

    private TypeChart() {
    }

    /** Damage multiplier of {@code attack} against a Pokémon of the given types (0, 0.25, 0.5, 1, 2 or 4). */
    public static float multiplier(String attack, String... defenders) {
        Integer a = index(attack);
        if (a == null) {
            return 1f;
        }
        float result = 1f;
        for (String defender : defenders) {
            Integer d = index(defender);
            if (d != null) {
                result *= (CHART.charAt(a * TYPES.length + d) - '0') / 2f;
            }
        }
        return result;
    }

    private static Integer index(String type) {
        return type == null || type.isEmpty() ? null : INDEX.get(type.toLowerCase(Locale.ROOT));
    }
}
