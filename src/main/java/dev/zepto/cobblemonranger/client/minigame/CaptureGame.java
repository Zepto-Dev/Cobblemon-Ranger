package dev.zepto.cobblemonranger.client.minigame;

import dev.zepto.cobblemonranger.capture.CaptureMath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Rules of the Guardian Signs style capture: the player drags a capture line around the Pokémon, each closed loop
 * that encloses it fills the friendship gauge, and anything the Pokémon does that touches the line breaks it and
 * drains styler energy. Pure logic in arena units (see {@link #WIDTH} x {@link #HEIGHT}); rendering lives in the
 * screen.
 */
public final class CaptureGame {
    public static final float WIDTH = 320f;
    public static final float HEIGHT = 200f;

    private static final float INTRO_TIME = 1.2f;
    private static final float MIN_POINT_SPACING = 2.5f;
    private static final float MIN_LOOP_AREA = 60f;
    private static final float GAUGE_DECAY_DELAY = 2.0f;
    private static final float GAUGE_DECAY_RATE = 0.04f;
    private static final float HIT_INVULNERABILITY = 0.8f;
    private static final float WINDUP_TIME = 0.7f;
    /** The tail of the capture line fades after this many seconds, as in the DS games. */
    private static final float LINE_LIFETIME = 1.1f;

    // Poké Assists
    /** Time to make use of an assist that turns the next strokes into something else. */
    private static final float DRAW_MODE_TIME = 10f;
    private static final float ICE_DRAW_TIME = 6f;
    private static final float BUBBLE_MIN_RADIUS = 5f;
    private static final float BUBBLE_MAX_RADIUS = 38f;
    private static final float BUBBLE_GROWTH = 14f;
    private static final float BUBBLE_FLIGHT_TIME = 4f;
    private static final float BUBBLE_SPEED = 150f;
    /** How far the cursor moves from where the bubble started to launch it. */
    private static final float BUBBLE_FLICK = 10f;
    private static final float ASSIST_GAUGE_CAP = CaptureMath.ASSIST_GAUGE_CAP;
    private static final float WALL_THICKNESS = 4f;
    /** Ice enclosure test grid, in arena units per cell. */
    private static final int ICE_CELL = 4;

    public enum Phase { INTRO, PLAYING, CAPTURED, FAILED }

    public enum AttackStyle { DASH, BURST, SHOCKWAVE, ERUPTION }

    /**
     * What a Poké Assist does, decided by the helping Pokémon's primary type: every type has its own effect. Effects
     * that act on the wild Pokémon depend on its weaknesses and resistances; the ones that help the player
     * ({@link #selfBuff}) always work in full.
     */
    public enum AssistEffect {
        /** Normal: the capture line gets half again as long for a while. */
        LONG_LINE(true),
        /** Fire: a ring of flames traps the Pokémon where it stands, and its attacks can't get out. */
        FIRE_RING(false),
        /** Water: hold still to blow a bubble, then flick the line to launch it. A bubble that hits the Pokémon traps it. */
        BUBBLE(false),
        /** Grass: tall grass sprouts around the Pokémon; it crawls while inside. */
        THICKET(false),
        /** Electric: recharges the styler. */
        CHARGE(true),
        /** Ice: lines become ice walls for a few seconds. Box the Pokémon in with them and it freezes. */
        ICE_WALL(false),
        /** Ground: the next loop becomes quicksand. A Pokémon inside can't leave and moves at half speed. */
        QUICKSAND(false),
        /** Rock: the next stroke becomes a boulder wall the Pokémon can't cross and its shots break against. */
        BOULDER_WALL(false),
        /** Flying: blows every attack away and pins the Pokémon in a small whirlwind in the middle. */
        WHIRLWIND(false),
        /** Bug: a sticky web where the Pokémon is; touching it holds the Pokémon still. */
        WEB(false),
        /** Psychic: the Pokémon and its attacks move at half speed. The line doesn't. */
        SLOW_TIME(false),
        /** Ghost: the Pokémon runs to a corner and cowers there, too scared to attack. */
        SPOOK(false),
        /** Dark: the Pokémon can't see the line, so its attacks go in random directions. */
        BLIND(false),
        /** Poison: the friendship gauge stops draining and slowly fills by itself. */
        POISON(false),
        /** Fairy: the next few loops count double. */
        CHARM(true),
        /** Dragon: a big chunk of friendship at once. */
        DRAGON_RUSH(false),
        /** Steel: the line can't be broken for a while. */
        IRON_LINE(true),
        /** Fighting: the next hit on the line is blocked and stuns the Pokémon instead. */
        COUNTER(false);

        public final boolean selfBuff;

        AssistEffect(boolean selfBuff) {
            this.selfBuff = selfBuff;
        }
    }

    /** What the player's strokes are turned into after an assist that draws on the arena. */
    public enum DrawMode { NONE, BOULDER, ICE, QUICKSAND, BUBBLE }

    public interface Listener {
        default void onLoop(boolean enclosed, int loops) {}

        default void onLineBroken(int damage) {}

        default void onWindup(AttackStyle style) {}

        default void onCaptured() {}

        default void onFailed() {}

        /** {@code effectiveness} is the type matchup: 0 (no effect), 0.25 up to 4, or 1 for effects that help the player. */
        default void onAssist(AssistEffect effect, float effectiveness) {}

        /** A Counter assist blocked a hit on the line. */
        default void onCounter() {}

        /** Ice walls boxed the Pokémon in and froze it. */
        default void onFrozen() {}

        /** A water bubble caught the Pokémon. */
        default void onTrapped() {}
    }

    public static final class Projectile {
        public float x, y, vx, vy, life;
        public final float radius;

        Projectile(float x, float y, float vx, float vy, float radius) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
            this.life = 3f;
        }
    }

    public static final class Shockwave {
        public final float x, y, maxRadius;
        public float radius;
        boolean spent;

        Shockwave(float x, float y, float maxRadius) {
            this.x = x;
            this.y = y;
            this.maxRadius = maxRadius;
        }
    }

    public static final class Hazard {
        public final float x, y, radius;
        /** Counts down while the warning marker is shown, then goes negative while it explodes. */
        public float timer;

        Hazard(float x, float y, float radius, float warning) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.timer = warning;
        }

        public boolean exploding() {
            return timer <= 0;
        }
    }

    /** A finished loop, kept briefly so the screen can flash it. */
    public static final class LoopFlash {
        public final float[] xs, ys;
        public final boolean enclosed;
        public float age;

        LoopFlash(float[] xs, float[] ys, boolean enclosed) {
            this.xs = xs;
            this.ys = ys;
            this.enclosed = enclosed;
        }
    }

    /** A wall drawn with a Rock or Ice assist. The Pokémon can't walk through it and projectiles break on it. */
    public static final class Wall {
        public final boolean ice;
        public final List<float[]> points = new ArrayList<>();
        public final float maxLife;
        public float life;

        Wall(boolean ice, float life) {
            this.ice = ice;
            this.life = life;
            this.maxLife = life;
        }
    }

    public enum ZoneKind { FIRE_RING, WHIRLWIND, THICKET, WEB, QUICKSAND }

    /** An area an assist leaves on the arena: a circle, or for quicksand the loop's outline. */
    public static final class Zone {
        public final ZoneKind kind;
        public final float x, y, radius, maxLife;
        /** Outline for quicksand; null for circles. */
        public final float[] xs, ys;
        public float life;
        int grabsLeft = 3;
        float grabCooldown;

        Zone(ZoneKind kind, float x, float y, float radius, float life, float[] xs, float[] ys) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.life = life;
            this.maxLife = life;
            this.xs = xs;
            this.ys = ys;
        }

        boolean contains(float px, float py, float shrink) {
            if (xs != null) {
                return pointInPolygon(px, py, xs, ys);
            }
            return Math.hypot(px - x, py - y) <= radius - shrink;
        }

        /** The Pokémon can't move out of these. */
        boolean confines() {
            return kind == ZoneKind.FIRE_RING || kind == ZoneKind.WHIRLWIND || kind == ZoneKind.QUICKSAND;
        }
    }

    /** A Water assist bubble: it grows while the player holds the line still, then flies where it was flicked. */
    public static final class Bubble {
        public float x, y, vx, vy, radius;
        public boolean growing = true, launched;
        float life = BUBBLE_FLIGHT_TIME;
        final float strength;
        float trapTimer;

        Bubble(float x, float y, float strength) {
            this.x = x;
            this.y = y;
            this.radius = BUBBLE_MIN_RADIUS;
            this.strength = strength;
        }

        public boolean holdsPokemon() {
            return trapTimer > 0;
        }
    }

    // Configuration
    private final Random random;
    private final Listener listener;
    private final float difficulty;
    private final float speed;
    private final float gainPerLoop;
    private final float lineLength;
    private final int level;
    public final float radius;
    public final AttackStyle signatureAttack;
    public final int maxEnergy;

    // State
    private Phase phase = Phase.INTRO;
    private float phaseTime, clock;
    public float x = WIDTH / 2, y = HEIGHT / 2, vx, vy;
    private float wanderAngle, wanderTimer, startledTimer;
    private boolean paused;
    private float attackCooldown;
    private AttackStyle pendingAttack;
    private float windupTimer, dashTimer;
    private final List<float[]> line = new ArrayList<>();
    private boolean drawing, strokeBroken;
    private float gauge, sinceLastLoop;
    private int loops;
    private int energy, damageTaken;
    private float invulnerable;
    private float stunTimer, frozenTimer;
    private float longLineTimer, slowTimeTimer, spookTimer, blindTimer, poisonTimer, ironTimer, counterTimer;
    private float counterStrength, spookX, spookY;
    private int charmLoops;
    private DrawMode drawMode = DrawMode.NONE;
    private float drawModeTimer, drawStrength, inkLeft;
    private Wall currentWall;
    private final String[] defenderTypes;
    public final List<Wall> walls = new ArrayList<>();
    public final List<Zone> zones = new ArrayList<>();
    public final List<Bubble> bubbles = new ArrayList<>();
    private Bubble heldBubble;
    private float[] bubbleOrigin;
    public float hitFlash;
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Shockwave> shockwaves = new ArrayList<>();
    public final List<Hazard> hazards = new ArrayList<>();
    public final List<LoopFlash> flashes = new ArrayList<>();

    public CaptureGame(String type, String secondaryType, int catchRate, int level, int energy, int maxEnergy,
                       float lineLength, float loopPower, float sizeHint, long seed, Listener listener) {
        this.random = new Random(seed);
        this.defenderTypes = secondaryType == null || secondaryType.isEmpty()
                ? new String[]{type} : new String[]{type, secondaryType};
        this.listener = listener;
        this.level = level;
        this.energy = energy;
        this.maxEnergy = maxEnergy;
        this.lineLength = lineLength;
        float catchFactor = clamp(catchRate, 1, 255) / 255f;
        float levelFactor = clamp(level, 1, 100) / 100f;
        this.difficulty = clamp((1 - catchFactor) * 0.7f + levelFactor * 0.3f, 0, 1);
        this.speed = 22f + 38f * difficulty;
        this.gainPerLoop = CaptureMath.gainPerLoop(catchRate, level, loopPower);
        this.radius = clamp(9f + sizeHint * 5f, 10f, 24f);
        this.signatureAttack = attackFor(type);
        this.wanderAngle = random.nextFloat() * (float) (Math.PI * 2);
        this.attackCooldown = nextCooldown() * 0.6f;
    }

    public static AssistEffect assistFor(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "fire" -> AssistEffect.FIRE_RING;
            case "water" -> AssistEffect.BUBBLE;
            case "grass" -> AssistEffect.THICKET;
            case "electric" -> AssistEffect.CHARGE;
            case "ice" -> AssistEffect.ICE_WALL;
            case "ground" -> AssistEffect.QUICKSAND;
            case "rock" -> AssistEffect.BOULDER_WALL;
            case "flying" -> AssistEffect.WHIRLWIND;
            case "bug" -> AssistEffect.WEB;
            case "psychic" -> AssistEffect.SLOW_TIME;
            case "ghost" -> AssistEffect.SPOOK;
            case "dark" -> AssistEffect.BLIND;
            case "poison" -> AssistEffect.POISON;
            case "fairy" -> AssistEffect.CHARM;
            case "dragon" -> AssistEffect.DRAGON_RUSH;
            case "steel" -> AssistEffect.IRON_LINE;
            case "fighting" -> AssistEffect.COUNTER;
            default -> AssistEffect.LONG_LINE;
        };
    }

    /**
     * How strongly an assist of {@code assistType} works on this Pokémon: the type matchup, softened so a resisted
     * assist still does something and a super effective one isn't overwhelming. 0 if the Pokémon is immune.
     */
    public float matchup(String assistType) {
        if (assistFor(assistType).selfBuff) {
            return 1f;
        }
        return TypeChart.multiplier(assistType, defenderTypes);
    }

    static float strengthFor(float matchup) {
        if (matchup <= 0) {
            return 0;
        }
        if (matchup < 0.5f) {
            return 0.4f;
        }
        if (matchup < 1f) {
            return 0.65f;
        }
        if (matchup < 2f) {
            return 1f;
        }
        return matchup < 4f ? 1.5f : 1.8f;
    }

    static AttackStyle attackFor(String type) {
        return switch (type == null ? "" : type.toLowerCase()) {
            case "water", "grass", "poison", "bug", "ice", "flying" -> AttackStyle.BURST;
            case "electric", "psychic", "ghost", "fairy", "dark" -> AttackStyle.SHOCKWAVE;
            case "fire", "ground", "rock" -> AttackStyle.ERUPTION;
            default -> AttackStyle.DASH;
        };
    }

    // ------------------------------------------------------------------ accessors

    public Phase phase() {
        return phase;
    }

    public float phaseTime() {
        return phaseTime;
    }

    public float gauge() {
        return gauge;
    }

    public int energy() {
        return energy;
    }

    public int damageTaken() {
        return damageTaken;
    }

    public int loops() {
        return loops;
    }

    public List<float[]> line() {
        return line;
    }

    public boolean isWindingUp() {
        return windupTimer > 0;
    }

    public boolean isDashing() {
        return dashTimer > 0;
    }

    public boolean isInvulnerable() {
        return invulnerable > 0;
    }

    public boolean isStunned() {
        return stunTimer > 0;
    }

    public boolean isFrozen() {
        return frozenTimer > 0;
    }

    public boolean isTrapped() {
        for (Bubble bubble : bubbles) {
            if (bubble.holdsPokemon()) {
                return true;
            }
        }
        return false;
    }

    /** Held still by a stun, a freeze, a web or a bubble: it doesn't move, attack or break the line by touching it. */
    public boolean isHeld() {
        return stunTimer > 0 || isTrapped();
    }

    public boolean isLineLong() {
        return longLineTimer > 0;
    }

    public boolean isTimeSlowed() {
        return slowTimeTimer > 0;
    }

    public boolean isSpooked() {
        return spookTimer > 0;
    }

    public boolean isBlind() {
        return blindTimer > 0;
    }

    public boolean isPoisoned() {
        return poisonTimer > 0;
    }

    public boolean isLineIron() {
        return ironTimer > 0;
    }

    public boolean isCounterReady() {
        return counterTimer > 0;
    }

    public int charmLoops() {
        return charmLoops;
    }

    public DrawMode drawMode() {
        return drawMode;
    }

    /** Line left for drawing walls, 0 to 1. */
    public float inkFraction() {
        return clamp(inkLeft / (lineLength * (drawMode == DrawMode.ICE ? 1.8f : 1.2f)), 0, 1);
    }

    public Wall currentWall() {
        return currentWall;
    }

    /** True while a slower Pokémon is in grass or quicksand, for the screen's status labels. */
    public boolean isBogged() {
        for (Zone zone : zones) {
            if ((zone.kind == ZoneKind.THICKET || zone.kind == ZoneKind.QUICKSAND) && zone.contains(x, y, 0)) {
                return true;
            }
        }
        return false;
    }

    public int energyGained() {
        return Math.max(0, -damageTaken);
    }

    public boolean canUseAssist() {
        return phase == Phase.PLAYING;
    }

    /**
     * Applies a Poké Assist from a Pokémon of {@code assistType}. {@code strength} scales durations and amounts; it is
     * higher for the Partner Pokémon. Returns the type matchup, which the effect is scaled by as well.
     */
    public float useAssist(String assistType, float strength) {
        AssistEffect effect = assistFor(assistType);
        if (!canUseAssist()) {
            return 1f;
        }
        float matchup = matchup(assistType);
        float s = strength * (effect.selfBuff ? 1f : strengthFor(matchup));
        if (s <= 0) {
            listener.onAssist(effect, 0);
            return 0;
        }
        switch (effect) {
            case LONG_LINE -> longLineTimer = Math.max(longLineTimer, 10f * s);
            case FIRE_RING -> {
                windupTimer = 0;
                dashTimer = 0;
                vx *= 0.2f;
                vy *= 0.2f;
                zones.add(new Zone(ZoneKind.FIRE_RING, x, y, radius + 30f, 6f * s, null, null));
            }
            case BUBBLE -> startDrawMode(DrawMode.BUBBLE, DRAW_MODE_TIME, s);
            case THICKET -> zones.add(new Zone(ZoneKind.THICKET, x, y, radius + 36f, 8f * s, null, null));
            case CHARGE -> {
                int gained = Math.min(maxEnergy - energy, Math.max(1, Math.round(maxEnergy * 0.2f * s)));
                energy += gained;
                damageTaken -= gained;
            }
            case ICE_WALL -> startDrawMode(DrawMode.ICE, ICE_DRAW_TIME, s);
            case QUICKSAND -> startDrawMode(DrawMode.QUICKSAND, DRAW_MODE_TIME, s);
            case BOULDER_WALL -> startDrawMode(DrawMode.BOULDER, DRAW_MODE_TIME, s);
            case WHIRLWIND -> {
                clearAttacks();
                zones.removeIf(z -> z.kind == ZoneKind.WHIRLWIND);
                zones.add(new Zone(ZoneKind.WHIRLWIND, WIDTH / 2, HEIGHT / 2, radius + 14f, 4f * s, null, null));
            }
            case WEB -> zones.add(new Zone(ZoneKind.WEB, x, y, radius + 14f, 10f * s, null, null));
            case SLOW_TIME -> slowTimeTimer = Math.max(slowTimeTimer, 6f * s);
            case SPOOK -> {
                spookTimer = Math.max(spookTimer, 6f * s);
                windupTimer = 0;
                float margin = radius + 6;
                spookX = x < WIDTH / 2 ? WIDTH - margin : margin;
                spookY = y < HEIGHT / 2 ? HEIGHT - margin : margin;
            }
            case BLIND -> blindTimer = Math.max(blindTimer, 8f * s);
            case POISON -> poisonTimer = Math.max(poisonTimer, 8f * s);
            case CHARM -> charmLoops = Math.max(charmLoops, Math.round(3 * s));
            case DRAGON_RUSH -> {
                // An assist alone never finishes a capture; the last loop is always the player's.
                gauge = Math.max(gauge, Math.min(ASSIST_GAUGE_CAP, gauge + 0.25f * s));
                sinceLastLoop = 0;
            }
            case IRON_LINE -> ironTimer = Math.max(ironTimer, 6f * s);
            case COUNTER -> {
                counterTimer = 15f;
                counterStrength = s;
            }
        }
        listener.onAssist(effect, matchup);
        return matchup;
    }

    private void startDrawMode(DrawMode mode, float time, float strength) {
        endWall();
        heldBubble = null;
        drawMode = mode;
        drawModeTimer = time;
        drawStrength = strength;
        inkLeft = lineLength * (mode == DrawMode.ICE ? 1.8f : 1.2f);
        // The capture line in progress turns into the new kind of stroke from the next press.
        line.clear();
        drawing = false;
    }

    private void endDrawMode() {
        endWall();
        drawMode = DrawMode.NONE;
        heldBubble = null;
    }

    private void clearAttacks() {
        projectiles.clear();
        shockwaves.clear();
        hazards.clear();
        windupTimer = 0;
        dashTimer = 0;
    }

    /** 0 for a point drawn just now, 1 for one about to fade. */
    public float pointAge(float[] point) {
        return clamp((clock - point[2]) / LINE_LIFETIME, 0, 1);
    }

    public float lineUsage() {
        return Math.min(1f, totalLineLength() / effectiveLineLength());
    }

    private float effectiveLineLength() {
        return lineLength * (longLineTimer > 0 ? 1.5f : 1f);
    }

    // ------------------------------------------------------------------ input

    public void beginStroke(float px, float py) {
        if (phase != Phase.PLAYING) {
            return;
        }
        px = clamp(px, 0, WIDTH);
        py = clamp(py, 0, HEIGHT);
        switch (drawMode) {
            case BOULDER, ICE -> {
                if (inkLeft > 0) {
                    boolean ice = drawMode == DrawMode.ICE;
                    currentWall = new Wall(ice, (ice ? 8f : 10f) * drawStrength);
                    currentWall.points.add(new float[]{px, py});
                    walls.add(currentWall);
                    drawing = true;
                }
                return;
            }
            case BUBBLE -> {
                heldBubble = new Bubble(px, py, drawStrength);
                bubbles.add(heldBubble);
                bubbleOrigin = new float[]{px, py};
                drawing = true;
                strokeBroken = false;
                return;
            }
            default -> {
            }
        }
        drawing = true;
        strokeBroken = false;
        line.clear();
        line.add(new float[]{px, py, clock});
    }

    public void dragStroke(float px, float py) {
        if (!drawing || phase != Phase.PLAYING) {
            return;
        }
        px = clamp(px, 0, WIDTH);
        py = clamp(py, 0, HEIGHT);
        if (currentWall != null) {
            extendWall(px, py);
            return;
        }
        if (heldBubble != null) {
            float dx = px - bubbleOrigin[0];
            float dy = py - bubbleOrigin[1];
            float dist = (float) Math.hypot(dx, dy);
            if (dist > BUBBLE_FLICK) {
                heldBubble.vx = dx / dist * BUBBLE_SPEED;
                heldBubble.vy = dy / dist * BUBBLE_SPEED;
                releaseBubble();
            }
            return;
        }
        if (strokeBroken || drawMode == DrawMode.BOULDER || drawMode == DrawMode.ICE) {
            return;
        }
        if (line.isEmpty()) {
            line.add(new float[]{px, py, clock});
            return;
        }
        float[] last = line.get(line.size() - 1);
        float dist = (float) Math.hypot(px - last[0], py - last[1]);
        if (dist < MIN_POINT_SPACING) {
            return;
        }
        // Long mouse jumps are split so loop detection and hit tests stay accurate.
        int steps = (int) Math.ceil(dist / 8f);
        for (int s = 1; s <= steps; s++) {
            float t = s / (float) steps;
            line.add(new float[]{last[0] + (px - last[0]) * t, last[1] + (py - last[1]) * t, clock});
            detectLoop();
        }
        trimLine();
    }

    public void endStroke() {
        drawing = false;
        line.clear();
        if (currentWall != null) {
            endWall();
        }
        if (heldBubble != null) {
            // Let go without a flick: the bubble floats where it was blown.
            releaseBubble();
        }
    }

    private void releaseBubble() {
        heldBubble.growing = false;
        heldBubble.launched = true;
        heldBubble = null;
        strokeBroken = true;
        drawMode = DrawMode.NONE;
    }

    private void extendWall(float px, float py) {
        List<float[]> points = currentWall.points;
        float[] last = points.get(points.size() - 1);
        float dist = (float) Math.hypot(px - last[0], py - last[1]);
        if (dist < MIN_POINT_SPACING) {
            return;
        }
        if (dist > inkLeft) {
            float t = inkLeft / dist;
            px = last[0] + (px - last[0]) * t;
            py = last[1] + (py - last[1]) * t;
            dist = inkLeft;
        }
        int steps = Math.max(1, (int) Math.ceil(dist / 6f));
        for (int s = 1; s <= steps; s++) {
            float t = s / (float) steps;
            points.add(new float[]{last[0] + (px - last[0]) * t, last[1] + (py - last[1]) * t});
        }
        inkLeft -= dist;
        if (currentWall.ice) {
            checkIceBox();
        }
        if (inkLeft <= 0.5f) {
            endWall();
        }
    }

    /** Finishes the wall being drawn. A boulder wall is one stroke; ice walls can be drawn until the ink runs out. */
    private void endWall() {
        Wall wall = currentWall;
        if (wall == null) {
            return;
        }
        currentWall = null;
        if (wall.points.size() < 2) {
            walls.remove(wall);
            return;
        }
        if (drawMode == DrawMode.BOULDER || (drawMode == DrawMode.ICE && inkLeft <= 0.5f)) {
            drawMode = DrawMode.NONE;
        }
    }

    private void detectLoop() {
        int n = line.size();
        if (n < 4) {
            return;
        }
        float[] a = line.get(n - 2);
        float[] b = line.get(n - 1);
        for (int i = n - 4; i >= 0; i--) {
            float[] c = line.get(i);
            float[] d = line.get(i + 1);
            float[] hit = intersect(a, b, c, d);
            if (hit == null) {
                continue;
            }
            int count = (n - 2) - (i + 1) + 2;
            float[] xs = new float[count];
            float[] ys = new float[count];
            xs[0] = hit[0];
            ys[0] = hit[1];
            for (int k = i + 1, j = 1; k <= n - 2; k++, j++) {
                xs[j] = line.get(k)[0];
                ys[j] = line.get(k)[1];
            }
            closeLoop(xs, ys);
            line.clear();
            line.add(new float[]{hit[0], hit[1], clock});
            line.add(b);
            return;
        }
    }

    private void closeLoop(float[] xs, float[] ys) {
        if (Math.abs(polygonArea(xs, ys)) < MIN_LOOP_AREA) {
            return;
        }
        if (drawMode == DrawMode.QUICKSAND) {
            float cx = 0, cy = 0;
            for (int i = 0; i < xs.length; i++) {
                cx += xs[i] / xs.length;
                cy += ys[i] / xs.length;
            }
            zones.add(new Zone(ZoneKind.QUICKSAND, cx, cy, 0, 6f * drawStrength, xs, ys));
            drawMode = DrawMode.NONE;
            line.clear();
            strokeBroken = true;
            return;
        }
        boolean enclosed = pointInPolygon(x, y, xs, ys);
        flashes.add(new LoopFlash(xs, ys, enclosed));
        if (enclosed) {
            loops++;
            gauge = Math.min(1f, gauge + gainPerLoop * (charmLoops > 0 ? 2f : 1f));
            charmLoops = Math.max(0, charmLoops - 1);
            sinceLastLoop = 0;
            startledTimer = 0.5f;
            paused = false;
            wanderAngle = random.nextFloat() * (float) (Math.PI * 2);
        }
        listener.onLoop(enclosed, loops);
        if (gauge >= 1f) {
            setPhase(Phase.CAPTURED);
            line.clear();
            drawing = false;
            endDrawMode();
            listener.onCaptured();
        }
    }

    private void trimLine() {
        while (line.size() > 2 && totalLineLength() > effectiveLineLength()) {
            line.remove(0);
        }
    }

    private float totalLineLength() {
        float total = 0;
        for (int i = 1; i < line.size(); i++) {
            total += (float) Math.hypot(line.get(i)[0] - line.get(i - 1)[0], line.get(i)[1] - line.get(i - 1)[1]);
        }
        return total;
    }

    // ------------------------------------------------------------------ simulation

    public void tick(float dt) {
        phaseTime += dt;
        clock += dt;
        while (!line.isEmpty() && clock - line.get(0)[2] > LINE_LIFETIME) {
            line.remove(0);
        }
        hitFlash = Math.max(0, hitFlash - dt);
        Iterator<LoopFlash> flashIt = flashes.iterator();
        while (flashIt.hasNext()) {
            LoopFlash flash = flashIt.next();
            flash.age += dt;
            if (flash.age > 0.5f) {
                flashIt.remove();
            }
        }

        switch (phase) {
            case INTRO -> {
                if (phaseTime >= INTRO_TIME) {
                    setPhase(Phase.PLAYING);
                }
                return;
            }
            case CAPTURED, FAILED -> {
                vx *= 0.9f;
                vy *= 0.9f;
                return;
            }
            case PLAYING -> {
            }
        }

        invulnerable = Math.max(0, invulnerable - dt);
        stunTimer = Math.max(0, stunTimer - dt);
        frozenTimer = Math.max(0, frozenTimer - dt);
        longLineTimer = Math.max(0, longLineTimer - dt);
        slowTimeTimer = Math.max(0, slowTimeTimer - dt);
        spookTimer = Math.max(0, spookTimer - dt);
        blindTimer = Math.max(0, blindTimer - dt);
        poisonTimer = Math.max(0, poisonTimer - dt);
        ironTimer = Math.max(0, ironTimer - dt);
        counterTimer = Math.max(0, counterTimer - dt);
        tickDrawMode(dt);
        tickArenaObjects(dt);

        sinceLastLoop += dt;
        if (poisonTimer > 0) {
            if (gauge < ASSIST_GAUGE_CAP) {
                gauge = Math.min(ASSIST_GAUGE_CAP, gauge + 0.02f * dt);
            }
        } else if (sinceLastLoop > GAUGE_DECAY_DELAY) {
            gauge = Math.max(0, gauge - GAUGE_DECAY_RATE * dt);
        }

        // A Psychic assist slows the Pokémon and its attacks, but not the player.
        float worldDt = slowTimeTimer > 0 ? dt * 0.45f : dt;
        movePokemon(worldDt);
        updateAttacks(worldDt);
        updateBubbles(dt);
        checkLineHits();
    }

    private void tickDrawMode(float dt) {
        if (drawMode == DrawMode.NONE) {
            return;
        }
        // The clock for using a draw assist stops while the player is drawing with it, except for the ice box.
        boolean busy = currentWall != null || heldBubble != null;
        if (drawMode == DrawMode.ICE || !busy) {
            drawModeTimer -= dt;
        }
        if (drawModeTimer <= 0) {
            endDrawMode();
        }
    }

    private void tickArenaObjects(float dt) {
        Iterator<Wall> wallIt = walls.iterator();
        while (wallIt.hasNext()) {
            Wall wall = wallIt.next();
            wall.life -= dt;
            if (wall.life <= 0 && wall != currentWall) {
                wallIt.remove();
            }
        }
        Iterator<Zone> zoneIt = zones.iterator();
        while (zoneIt.hasNext()) {
            Zone zone = zoneIt.next();
            zone.life -= dt;
            zone.grabCooldown = Math.max(0, zone.grabCooldown - dt);
            if (zone.life <= 0 || (zone.kind == ZoneKind.WEB && zone.grabsLeft <= 0 && zone.grabCooldown <= 0)) {
                zoneIt.remove();
            }
        }
    }

    private Zone zone(ZoneKind kind) {
        for (Zone zone : zones) {
            if (zone.kind == kind) {
                return zone;
            }
        }
        return null;
    }

    private Bubble trappingBubble() {
        for (Bubble bubble : bubbles) {
            if (bubble.holdsPokemon()) {
                return bubble;
            }
        }
        return null;
    }

    /** Pinned in a Flying assist's whirlwind: it can't attack until the wind dies down. */
    private boolean isPinned() {
        Zone whirlwind = zone(ZoneKind.WHIRLWIND);
        return whirlwind != null && whirlwind.contains(x, y, 0);
    }

    private void movePokemon(float dt) {
        Bubble trap = trappingBubble();
        if (trap != null) {
            x = trap.x;
            y = trap.y;
            vx = 0;
            vy = 0;
            return;
        }
        Zone whirlwind = zone(ZoneKind.WHIRLWIND);
        if (whirlwind != null && !whirlwind.contains(x, y, radius * 0.6f)) {
            // Swept into the middle, ignoring walls on the way.
            float angle = (float) Math.atan2(whirlwind.y - y, whirlwind.x - x);
            vx = (float) Math.cos(angle) * 220f;
            vy = (float) Math.sin(angle) * 220f;
            x += vx * dt;
            y += vy * dt;
            return;
        }

        float bog = 1f;
        for (Zone zone : zones) {
            if (zone.kind == ZoneKind.THICKET && zone.contains(x, y, 0)) {
                bog = Math.min(bog, 0.3f);
            } else if (zone.kind == ZoneKind.QUICKSAND && zone.contains(x, y, 0)) {
                bog = Math.min(bog, 0.5f);
            }
        }
        if (windupTimer > 0 || isHeld()) {
            vx *= 0.8f;
            vy *= 0.8f;
        } else if (spookTimer > 0) {
            float dx = spookX - x, dy = spookY - y;
            float dist = (float) Math.hypot(dx, dy);
            if (dist > 4) {
                float s = speed * 1.8f * bog;
                vx += (dx / dist * s - vx) * Math.min(1, dt * 5);
                vy += (dy / dist * s - vy) * Math.min(1, dt * 5);
            } else {
                vx *= 0.7f;
                vy *= 0.7f;
            }
        } else if (dashTimer <= 0) {
            wanderTimer -= dt;
            if (wanderTimer <= 0) {
                // Occasionally pause, like the DS Pokémon do between movements.
                paused = !paused && random.nextFloat() < 0.15f;
                wanderAngle += (random.nextFloat() - 0.5f) * 2.4f;
                wanderTimer = paused ? 0.4f + random.nextFloat() * 0.5f : 0.8f + random.nextFloat() * 1.6f;
            }
            float targetSpeed = paused ? 0 : speed * (startledTimer > 0 ? 2.2f : 1f) * bog;
            float tx = (float) Math.cos(wanderAngle) * targetSpeed;
            float ty = (float) Math.sin(wanderAngle) * targetSpeed;
            float blend = Math.min(1, dt * 3);
            vx += (tx - vx) * blend;
            vy += (ty - vy) * blend;
            startledTimer = Math.max(0, startledTimer - dt);
        } else if (bog < 1f) {
            vx *= 1 - (1 - bog) * Math.min(1, dt * 6);
            vy *= 1 - (1 - bog) * Math.min(1, dt * 6);
        }

        float ox = x, oy = y;
        x += vx * dt;
        y += vy * dt;
        float margin = radius + 2;
        if (x < margin || x > WIDTH - margin) {
            x = clamp(x, margin, WIDTH - margin);
            vx = -vx;
            wanderAngle = (float) (Math.PI - wanderAngle);
        }
        if (y < margin || y > HEIGHT - margin) {
            y = clamp(y, margin, HEIGHT - margin);
            vy = -vy;
            wanderAngle = -wanderAngle;
        }
        if (blocked(ox, oy)) {
            x = ox;
            y = oy;
            vx = -vx * 0.5f;
            vy = -vy * 0.5f;
            wanderAngle += (float) Math.PI;
            dashTimer = 0;
        }

        for (Zone zone : zones) {
            if (zone.kind == ZoneKind.WEB && zone.grabsLeft > 0 && zone.grabCooldown <= 0 && !isHeld()
                    && zone.contains(x, y, 0)) {
                // Stuck fast for a moment, then it pulls free; the web holds three times.
                float hold = 1.6f * zone.maxLife / 10f;
                stunTimer = Math.max(stunTimer, hold);
                windupTimer = 0;
                dashTimer = 0;
                zone.grabsLeft--;
                zone.grabCooldown = hold + 1.2f;
            }
        }
    }

    /** Moving from (ox, oy) to the current position would go through a wall or out of a trap. */
    private boolean blocked(float ox, float oy) {
        float reach = radius * 0.8f + WALL_THICKNESS / 2;
        for (Wall wall : walls) {
            float now = distanceToWall(wall, x, y);
            if (now < reach && now < distanceToWall(wall, ox, oy) - 0.01f) {
                return true;
            }
        }
        for (Zone zone : zones) {
            float shrink = zone.xs == null ? radius * 0.6f : 0;
            if (zone.confines() && zone.contains(ox, oy, shrink) && !zone.contains(x, y, shrink)) {
                return true;
            }
        }
        return false;
    }

    private void updateBubbles(float dt) {
        Iterator<Bubble> it = bubbles.iterator();
        while (it.hasNext()) {
            Bubble bubble = it.next();
            if (bubble.growing) {
                bubble.radius = Math.min(BUBBLE_MAX_RADIUS, bubble.radius + BUBBLE_GROWTH * dt);
            } else {
                bubble.x += bubble.vx * dt;
                bubble.y += bubble.vy * dt;
                float margin = bubble.holdsPokemon() ? Math.max(bubble.radius, radius + 2) : bubble.radius;
                if (bubble.x < margin || bubble.x > WIDTH - margin) {
                    bubble.x = clamp(bubble.x, margin, WIDTH - margin);
                    bubble.vx = -bubble.vx;
                }
                if (bubble.y < margin || bubble.y > HEIGHT - margin) {
                    bubble.y = clamp(bubble.y, margin, HEIGHT - margin);
                    bubble.vy = -bubble.vy;
                }
                if (bubble.holdsPokemon()) {
                    bubble.trapTimer -= dt;
                    if (bubble.trapTimer <= 0) {
                        it.remove();
                    }
                    continue;
                }
                bubble.life -= dt;
                if (bubble.life <= 0) {
                    it.remove();
                    continue;
                }
            }
            if (!isTrapped() && Math.hypot(x - bubble.x, y - bubble.y) < bubble.radius + radius * 0.3f) {
                // Bigger bubbles hold it for longer.
                float size = (bubble.radius - BUBBLE_MIN_RADIUS) / (BUBBLE_MAX_RADIUS - BUBBLE_MIN_RADIUS);
                bubble.trapTimer = (0.8f + 4.2f * size) * bubble.strength;
                if (bubble == heldBubble) {
                    releaseBubble();
                }
                bubble.growing = false;
                bubble.launched = true;
                float angle = random.nextFloat() * (float) (Math.PI * 2);
                bubble.vx = (float) Math.cos(angle) * 12f;
                bubble.vy = (float) Math.sin(angle) * 12f;
                windupTimer = 0;
                dashTimer = 0;
                listener.onTrapped();
            }
        }
    }

    /** After drawing ice walls: if they box the Pokémon in (the arena's edge doesn't count), it freezes. */
    private void checkIceBox() {
        int cols = (int) Math.ceil(WIDTH / ICE_CELL), rows = (int) Math.ceil(HEIGHT / ICE_CELL);
        boolean[] solid = new boolean[cols * rows];
        for (Wall wall : walls) {
            if (!wall.ice) {
                continue;
            }
            for (int i = 0; i < wall.points.size(); i++) {
                float[] a = wall.points.get(i);
                float[] b = wall.points.get(Math.min(i + 1, wall.points.size() - 1));
                float length = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
                int samples = Math.max(1, (int) Math.ceil(length / 1.5f));
                for (int k = 0; k <= samples; k++) {
                    float t = k / (float) samples;
                    int c = (int) ((a[0] + (b[0] - a[0]) * t) / ICE_CELL);
                    int r = (int) ((a[1] + (b[1] - a[1]) * t) / ICE_CELL);
                    // Thickened by a cell so near-misses at the corners still close the box.
                    for (int dc = -1; dc <= 1; dc++) {
                        for (int dr = -1; dr <= 1; dr++) {
                            int cc = c + dc, rr = r + dr;
                            if (cc >= 0 && rr >= 0 && cc < cols && rr < rows) {
                                solid[rr * cols + cc] = true;
                            }
                        }
                    }
                }
            }
        }
        int start = (int) clamp(y / ICE_CELL, 0, rows - 1) * cols + (int) clamp(x / ICE_CELL, 0, cols - 1);
        if (solid[start]) {
            return;
        }
        boolean[] seen = new boolean[cols * rows];
        int[] queue = new int[cols * rows];
        int head = 0, tail = 0;
        queue[tail++] = start;
        seen[start] = true;
        while (head < tail) {
            int cell = queue[head++];
            int c = cell % cols, r = cell / cols;
            if (c == 0 || r == 0 || c == cols - 1 || r == rows - 1) {
                return;
            }
            int[] next = {cell - 1, cell + 1, cell - cols, cell + cols};
            for (int n : next) {
                if (!seen[n] && !solid[n]) {
                    seen[n] = true;
                    queue[tail++] = n;
                }
            }
        }
        float freeze = 3f * drawStrength;
        stunTimer = Math.max(stunTimer, freeze);
        frozenTimer = Math.max(frozenTimer, freeze);
        windupTimer = 0;
        dashTimer = 0;
        currentWall = null;
        walls.removeIf(w -> w.ice);
        drawMode = DrawMode.NONE;
        listener.onFrozen();
    }

    private void updateAttacks(float dt) {
        if (dashTimer > 0) {
            dashTimer -= dt;
            if (dashTimer <= 0) {
                vx *= 0.3f;
                vy *= 0.3f;
            }
        }
        if (windupTimer > 0) {
            windupTimer -= dt;
            if (windupTimer <= 0) {
                launch(pendingAttack);
            }
        } else if (dashTimer <= 0 && !isHeld() && spookTimer <= 0 && !isPinned()) {
            attackCooldown -= dt;
            if (attackCooldown <= 0) {
                pendingAttack = random.nextFloat() < 0.25f ? AttackStyle.DASH : signatureAttack;
                windupTimer = WINDUP_TIME;
                attackCooldown = nextCooldown();
                listener.onWindup(pendingAttack);
            }
        }

        Zone ring = zone(ZoneKind.FIRE_RING);
        Iterator<Projectile> projectileIt = projectiles.iterator();
        while (projectileIt.hasNext()) {
            Projectile p = projectileIt.next();
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt;
            if (p.life <= 0 || p.x < -10 || p.x > WIDTH + 10 || p.y < -10 || p.y > HEIGHT + 10
                    || hitsWall(p.x, p.y, p.radius) || (ring != null && !ring.contains(p.x, p.y, 0))) {
                projectileIt.remove();
            }
        }
        Iterator<Shockwave> waveIt = shockwaves.iterator();
        while (waveIt.hasNext()) {
            Shockwave wave = waveIt.next();
            wave.radius += wave.maxRadius / 0.8f * dt;
            if (wave.radius >= wave.maxRadius) {
                waveIt.remove();
            }
        }
        Iterator<Hazard> hazardIt = hazards.iterator();
        while (hazardIt.hasNext()) {
            Hazard hazard = hazardIt.next();
            hazard.timer -= dt;
            if (hazard.timer < -0.3f) {
                hazardIt.remove();
            }
        }
    }

    private boolean hitsWall(float px, float py, float r) {
        for (Wall wall : walls) {
            if (distanceToWall(wall, px, py) < r + WALL_THICKNESS / 2) {
                return true;
            }
        }
        return false;
    }

    private void launch(AttackStyle style) {
        boolean blind = blindTimer > 0;
        Zone ring = zone(ZoneKind.FIRE_RING);
        switch (style) {
            case DASH -> {
                float[] aim = blind ? randomPoint() : aimPoint();
                float angle = (float) Math.atan2(aim[1] - y, aim[0] - x);
                float dashSpeed = speed * 4f;
                vx = (float) Math.cos(angle) * dashSpeed;
                vy = (float) Math.sin(angle) * dashSpeed;
                dashTimer = 0.45f;
            }
            case BURST -> {
                float projectileSpeed = 80f + 50f * difficulty;
                if (blind) {
                    // Can't see where to aim: a few shots in random directions.
                    for (int i = 0; i < 4; i++) {
                        float angle = random.nextFloat() * (float) (Math.PI * 2);
                        projectiles.add(new Projectile(x, y, (float) Math.cos(angle) * projectileSpeed,
                                (float) Math.sin(angle) * projectileSpeed, 4f));
                    }
                    return;
                }
                int count = 8;
                int waves = difficulty > 0.6f ? 2 : 1;
                float offset = random.nextFloat() * (float) Math.PI;
                for (int w = 0; w < waves; w++) {
                    for (int i = 0; i < count; i++) {
                        float angle = offset + (float) (Math.PI * 2 * i / count) + w * (float) (Math.PI / count);
                        float s = projectileSpeed * (1f - w * 0.35f);
                        projectiles.add(new Projectile(x, y, (float) Math.cos(angle) * s, (float) Math.sin(angle) * s, 4f));
                    }
                }
            }
            case SHOCKWAVE -> {
                float max = (radius + 60f + 40f * difficulty) * (blind ? 0.6f : 1f);
                if (ring != null) {
                    max = Math.min(max, ring.radius - (float) Math.hypot(x - ring.x, y - ring.y));
                }
                shockwaves.add(new Shockwave(x, y, Math.max(radius + 4, max)));
            }
            case ERUPTION -> {
                int count = 3 + Math.round(2 * difficulty);
                for (int i = 0; i < count; i++) {
                    float hx, hy;
                    if (ring != null) {
                        // Trapped in the fire ring: the eruptions stay inside it too.
                        float angle = random.nextFloat() * (float) (Math.PI * 2);
                        float dist = random.nextFloat() * ring.radius * 0.8f;
                        hx = ring.x + (float) Math.cos(angle) * dist;
                        hy = ring.y + (float) Math.sin(angle) * dist;
                    } else if (!blind && !line.isEmpty() && random.nextFloat() < 0.7f) {
                        float[] p = line.get(random.nextInt(line.size()));
                        hx = p[0] + (random.nextFloat() - 0.5f) * 30f;
                        hy = p[1] + (random.nextFloat() - 0.5f) * 30f;
                    } else {
                        float[] p = randomPoint();
                        hx = p[0];
                        hy = p[1];
                    }
                    hazards.add(new Hazard(clamp(hx, 0, WIDTH), clamp(hy, 0, HEIGHT), 18f + 6f * difficulty,
                            0.9f + i * 0.12f));
                }
            }
        }
    }

    private float[] randomPoint() {
        return new float[]{20 + random.nextFloat() * (WIDTH - 40), 20 + random.nextFloat() * (HEIGHT - 40)};
    }

    private float[] aimPoint() {
        if (!line.isEmpty()) {
            return line.get(line.size() - 1);
        }
        return new float[]{WIDTH / 2 + (random.nextFloat() - 0.5f) * 120, HEIGHT / 2 + (random.nextFloat() - 0.5f) * 80};
    }

    private void checkLineHits() {
        if (line.size() < 2 || invulnerable > 0) {
            return;
        }
        boolean iron = ironTimer > 0;
        // The Pokémon's own body breaks the line; harder when it is charging.
        if (!isHeld() && !iron && distanceToLine(x, y) < radius * 0.7f) {
            breakLine(dashTimer > 0 ? 3 + level / 25 : 1 + level / 40);
            return;
        }
        for (Projectile p : projectiles) {
            if (distanceToLine(p.x, p.y) < p.radius + 1) {
                p.life = 0;
                if (!iron) {
                    breakLine(2 + level / 35);
                    return;
                }
            }
        }
        for (Shockwave wave : shockwaves) {
            if (wave.spent) {
                continue;
            }
            for (float[] point : line) {
                float d = (float) Math.hypot(point[0] - wave.x, point[1] - wave.y);
                if (Math.abs(d - wave.radius) < 3f) {
                    wave.spent = true;
                    if (!iron) {
                        breakLine(3 + level / 30);
                        return;
                    }
                    break;
                }
            }
        }
        if (iron) {
            return;
        }
        for (Hazard hazard : hazards) {
            if (hazard.exploding() && distanceToLine(hazard.x, hazard.y) < hazard.radius) {
                breakLine(4 + level / 25);
                return;
            }
        }
    }

    private void breakLine(int damage) {
        if (counterTimer > 0) {
            // A Fighting assist takes the hit for the line and knocks the Pokémon off its feet.
            counterTimer = 0;
            stunTimer = Math.max(stunTimer, 2.5f * counterStrength);
            windupTimer = 0;
            dashTimer = 0;
            invulnerable = HIT_INVULNERABILITY;
            listener.onCounter();
            return;
        }
        line.clear();
        strokeBroken = true;
        invulnerable = HIT_INVULNERABILITY;
        hitFlash = 0.35f;
        gauge *= 0.85f;
        int dealt = Math.min(damage, energy);
        energy -= dealt;
        damageTaken += dealt;
        listener.onLineBroken(dealt);
        if (energy <= 0) {
            setPhase(Phase.FAILED);
            drawing = false;
            endDrawMode();
            listener.onFailed();
        }
    }

    private float nextCooldown() {
        return 4.0f - 2.2f * difficulty + (random.nextFloat() - 0.5f) * 1.6f;
    }

    private void setPhase(Phase phase) {
        this.phase = phase;
        this.phaseTime = 0;
    }

    // ------------------------------------------------------------------ geometry

    static float distanceToWall(Wall wall, float px, float py) {
        List<float[]> points = wall.points;
        if (points.size() == 1) {
            return (float) Math.hypot(px - points.get(0)[0], py - points.get(0)[1]);
        }
        float best = Float.MAX_VALUE;
        for (int i = 1; i < points.size(); i++) {
            best = Math.min(best, distanceToSegment(px, py, points.get(i - 1), points.get(i)));
        }
        return best;
    }

    private float distanceToLine(float px, float py) {
        float best = Float.MAX_VALUE;
        for (int i = 1; i < line.size(); i++) {
            best = Math.min(best, distanceToSegment(px, py, line.get(i - 1), line.get(i)));
        }
        return best;
    }

    static float distanceToSegment(float px, float py, float[] a, float[] b) {
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float lenSq = dx * dx + dy * dy;
        float t = lenSq == 0 ? 0 : clamp(((px - a[0]) * dx + (py - a[1]) * dy) / lenSq, 0, 1);
        return (float) Math.hypot(px - (a[0] + t * dx), py - (a[1] + t * dy));
    }

    static float[] intersect(float[] p1, float[] p2, float[] p3, float[] p4) {
        float d = (p2[0] - p1[0]) * (p4[1] - p3[1]) - (p2[1] - p1[1]) * (p4[0] - p3[0]);
        if (Math.abs(d) < 1e-6f) {
            return null;
        }
        float t = ((p3[0] - p1[0]) * (p4[1] - p3[1]) - (p3[1] - p1[1]) * (p4[0] - p3[0])) / d;
        float u = ((p3[0] - p1[0]) * (p2[1] - p1[1]) - (p3[1] - p1[1]) * (p2[0] - p1[0])) / d;
        if (t < 0 || t > 1 || u < 0 || u > 1) {
            return null;
        }
        return new float[]{p1[0] + t * (p2[0] - p1[0]), p1[1] + t * (p2[1] - p1[1])};
    }

    static boolean pointInPolygon(float px, float py, float[] xs, float[] ys) {
        boolean inside = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if ((ys[i] > py) != (ys[j] > py) && px < (xs[j] - xs[i]) * (py - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    static float polygonArea(float[] xs, float[] ys) {
        float area = 0;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            area += (xs[j] + xs[i]) * (ys[j] - ys[i]);
        }
        return area / 2;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
