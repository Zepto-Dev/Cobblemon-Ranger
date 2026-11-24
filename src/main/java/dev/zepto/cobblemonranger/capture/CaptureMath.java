package dev.zepto.cobblemonranger.capture;

/**
 * Capture numbers shared by the client's minigame and the server's check of its result, so both agree on how many
 * loops a capture takes.
 */
public final class CaptureMath {
    /** Most of the friendship gauge that Poké Assists alone can fill; the last loops are always the player's. */
    public static final float ASSIST_GAUGE_CAP = 0.95f;
    /** Loops a Fairy assist doubles at most (3, times the Partner's 1.5 strength, rounded). */
    public static final int MAX_CHARM_LOOPS = 5;

    private CaptureMath() {
    }

    /** Share of the friendship gauge one loop around the Pokémon fills. */
    public static float gainPerLoop(int catchRate, int level, float loopPower) {
        float catchFactor = clamp(catchRate, 1, 255) / 255f;
        float levelFactor = clamp(level, 1, 100) / 100f;
        return clamp(0.16f * (0.45f + catchFactor) * (1.25f - levelFactor * 0.6f) * loopPower, 0.035f, 0.34f);
    }

    /**
     * Fewest loops that can fill the gauge. {@code gaugeAssists} is whether a Dragon or Poison assist (which fill the
     * gauge directly, up to {@link #ASSIST_GAUGE_CAP}) was used, and {@code charmAssists} how many Fairy assists were.
     */
    public static int minLoops(float gainPerLoop, boolean gaugeAssists, int charmAssists) {
        float need = gaugeAssists ? 1f - ASSIST_GAUGE_CAP : 1f;
        // A little slack for float rounding differences between client and server.
        int loops = (int) Math.ceil(need / gainPerLoop - 0.01f);
        return Math.max(1, loops - MAX_CHARM_LOOPS * charmAssists);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
