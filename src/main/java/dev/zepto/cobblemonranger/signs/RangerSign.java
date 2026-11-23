package dev.zepto.cobblemonranger.signs;

import dev.zepto.cobblemonranger.config.RangerConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Ranger Signs: glyphs drawn on the styler to call a legendary Pokémon for help. Each one unlocks at a Ranger rank,
 * from rank 1 to the top rank 10.
 * The template is the glyph as a polyline in a unit square (y pointing down); closed glyphs end where they start.
 */
public enum RangerSign {
    CELEBI("celebi", "celebi", 1, 20 * 60 * 3, false, true, circle()),
    SUICUNE("suicune", "suicune", 2, 20 * 60 * 5, false, false, wave()),
    ENTEI("entei", "entei", 3, 20 * 60 * 4, false, true,
            new float[][]{{0.5f, 0f}, {1f, 0.9f}, {0f, 0.9f}, {0.5f, 0f}}),
    RAIKOU("raikou", "raikou", 4, 20 * 60 * 4, false, false,
            new float[][]{{0.65f, 0f}, {0.2f, 0.5f}, {0.8f, 0.5f}, {0.35f, 1f}}),
    LATIOS("latios", "latios", 5, 20 * 60 * 10, false, false,
            new float[][]{{0f, 0.1f}, {0.5f, 0.9f}, {1f, 0.1f}}),
    GROUDON("groudon", "groudon", 6, 0, true, false,
            new float[][]{{0f, 1f}, {0.25f, 0f}, {0.5f, 0.6f}, {0.75f, 0f}, {1f, 1f}}),
    KYOGRE("kyogre", "kyogre", 7, 0, true, false, spiral()),
    CRESSELIA("cresselia", "cresselia", 8, 0, true, true, crescent()),
    DARKRAI("darkrai", "darkrai", 9, 0, true, true, star()),
    RAYQUAZA("rayquaza", "rayquaza", 10, 20 * 60 * 5, false, false, rising());

    /** Styler energy every sign costs. */
    public static final int ENERGY_COST = 10;

    private final String id;
    private final String species;
    private final int rank;
    private final int cooldownTicks;
    private final boolean worldSign;
    private final boolean closed;
    private final float[][] template;

    RangerSign(String id, String species, int rank, int cooldownTicks, boolean worldSign, boolean closed,
               float[][] template) {
        this.id = id;
        this.species = species;
        this.rank = rank;
        this.cooldownTicks = cooldownTicks;
        this.worldSign = worldSign;
        this.closed = closed;
        this.template = template;
    }

    public String id() {
        return id;
    }

    public String species() {
        return species;
    }

    public int rank() {
        return rank;
    }

    /** The player's own wait before they can use this sign again. World signs take theirs from the server config. */
    public int cooldownTicks() {
        return worldSign ? (int) Math.min(Integer.MAX_VALUE, RangerConfig.worldSignPersonalCooldownTicks())
                : cooldownTicks;
    }

    /**
     * Signs that change the weather or the time of day for everyone. On top of their own cooldown they share one
     * world-wide cooldown, so players on a server can't keep flipping the weather between them.
     */
    public boolean worldSign() {
        return worldSign;
    }

    public boolean closed() {
        return closed;
    }

    public float[][] template() {
        return template;
    }

    public Component displayName() {
        return Component.translatable("sign.cobblemonranger." + id);
    }

    public Component description() {
        return Component.translatable("sign.cobblemonranger." + id + ".desc");
    }

    /** A wait in seconds as "4:05", or "1:02:30" for an hour or more. */
    public static String formatTime(long seconds) {
        if (seconds >= 3600) {
            return String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static RangerSign byId(String id) {
        for (RangerSign sign : values()) {
            if (sign.id.equals(id)) {
                return sign;
            }
        }
        return null;
    }

    private static float[][] circle() {
        int n = 24;
        float[][] points = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            double a = -Math.PI / 2 + Math.PI * 2 * i / n;
            points[i] = new float[]{0.5f + 0.5f * (float) Math.cos(a), 0.5f + 0.5f * (float) Math.sin(a)};
        }
        return points;
    }

    private static float[][] wave() {
        int n = 24;
        float[][] points = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            points[i] = new float[]{t, 0.5f - 0.3f * (float) Math.sin(t * Math.PI * 3)};
        }
        return points;
    }

    /** Kyogre: a whirlpool, drawn from the middle outwards. */
    private static float[][] spiral() {
        int n = 40;
        float turns = 1.75f;
        float[][] points = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            double a = -Math.PI / 2 + Math.PI * 2 * turns * t;
            float r = 0.08f + 0.42f * t;
            points[i] = new float[]{0.5f + r * (float) Math.cos(a), 0.5f + r * (float) Math.sin(a)};
        }
        return points;
    }

    /** Cresselia: a crescent moon, the outer curve then the inner one. */
    private static float[][] crescent() {
        int n = 16;
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(-70 - 220.0 * i / n);
            points.add(new float[]{0.5f + 0.5f * (float) Math.cos(a), 0.5f - 0.5f * (float) Math.sin(a)});
        }
        float[] start = points.get(0);
        float[] end = points.get(n);
        // The inner curve bulges less, back to where the outer one began.
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            float x = end[0] + (start[0] - end[0]) * t;
            float y = end[1] + (start[1] - end[1]) * t;
            float bulge = 0.28f * (float) Math.sin(Math.PI * t);
            points.add(new float[]{x - bulge, y});
        }
        return points.toArray(new float[0][]);
    }

    /** Darkrai: a five-pointed star in one stroke. */
    private static float[][] star() {
        float[][] points = new float[6][];
        for (int i = 0; i <= 5; i++) {
            double a = -Math.PI / 2 + Math.PI * 4 * i / 5;
            points[i] = new float[]{0.5f + 0.5f * (float) Math.cos(a), 0.5f + 0.5f * (float) Math.sin(a)};
        }
        return points;
    }

    /** Rayquaza: a serpent rising up the pad, drawn from the bottom. */
    private static float[][] rising() {
        int n = 24;
        float[][] points = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            points[i] = new float[]{0.5f + 0.3f * (float) Math.sin(t * Math.PI * 2), 1f - t};
        }
        return points;
    }
}
