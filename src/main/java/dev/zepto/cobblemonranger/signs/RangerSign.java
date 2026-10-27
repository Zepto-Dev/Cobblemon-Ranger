package dev.zepto.cobblemonranger.signs;

import net.minecraft.network.chat.Component;

/**
 * Ranger Signs: glyphs drawn on the styler to call a legendary Pokémon for help. Each one unlocks at a Ranger rank.
 * The template is the glyph as a polyline in a unit square (y pointing down); closed glyphs end where they start.
 */
public enum RangerSign {
    CELEBI("celebi", "celebi", 1, 20 * 60 * 3, true, circle()),
    SUICUNE("suicune", "suicune", 2, 20 * 60 * 5, false, wave()),
    ENTEI("entei", "entei", 3, 20 * 60 * 4, true,
            new float[][]{{0.5f, 0f}, {1f, 0.9f}, {0f, 0.9f}, {0.5f, 0f}}),
    RAIKOU("raikou", "raikou", 4, 20 * 60 * 4, false,
            new float[][]{{0.65f, 0f}, {0.2f, 0.5f}, {0.8f, 0.5f}, {0.35f, 1f}}),
    LATIOS("latios", "latios", 5, 20 * 60 * 10, false,
            new float[][]{{0f, 0.1f}, {0.5f, 0.9f}, {1f, 0.1f}}),
    GROUDON("groudon", "groudon", 6, 20 * 60 * 10, false,
            new float[][]{{0f, 1f}, {0.25f, 0f}, {0.5f, 0.6f}, {0.75f, 0f}, {1f, 1f}});

    /** Styler energy every sign costs. */
    public static final int ENERGY_COST = 10;

    private final String id;
    private final String species;
    private final int rank;
    private final int cooldownTicks;
    private final boolean closed;
    private final float[][] template;

    RangerSign(String id, String species, int rank, int cooldownTicks, boolean closed, float[][] template) {
        this.id = id;
        this.species = species;
        this.rank = rank;
        this.cooldownTicks = cooldownTicks;
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

    public int cooldownTicks() {
        return cooldownTicks;
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
}
