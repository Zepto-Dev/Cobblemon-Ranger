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

    // Boss captures
    /** After a boss enters a new stage, loops don't count for this long. */
    private static final float SHIELD_TIME = 2.2f;
    /** Each stage adds this much to how hard the Pokémon fights (more shots, bigger waves, shorter cooldowns). */
    private static final float STAGE_DIFFICULTY = 0.18f;
    private static final float DARK_TIME = 5.4f;
    private static final float BARRIER_TIME = 4.5f, BARRIER_WARMUP = 0.6f;
    private static final float WIND_TIME = 3.2f;
    public static final float REWIND_FX_TIME = 0.9f;

    public enum Phase { INTRO, PLAYING, CAPTURED, FAILED }

    /**
     * What the wild Pokémon attacks with. The first four are the type attacks every Pokémon uses; the rest are the
     * signature attacks of legendary bosses, named in the {@code ranger_bosses} data files by their lower-case names.
     */
    public enum AttackStyle {
        DASH, BURST, SHOCKWAVE, ERUPTION,
        /** Groudon: cracks open across the arena and erupt from one end to the other. */
        FISSURE,
        /** Kyogre: a wave sweeps the arena, with one gap to slip the line through. */
        TIDAL_WAVE,
        /** Rayquaza: streaks dive across the arena in straight lines. */
        DIVE,
        /** Rayquaza and Lugia: a beam from the Pokémon sweeps around. */
        BEAM,
        /** Latios and Latias: balls of mist that home in on the line. */
        MIST_BALLS,
        /** Latios and Latias: very fast laps around the arena. */
        ORBIT_RUSH,
        /** Celebi: time skips back. The Pokémon blinks to where it was and the newest part of the line is undone. */
        REWIND,
        /** Suicune: aurora walls split the arena for a few seconds. */
        AURORA,
        /** Entei: flame pillars burst up under the tip of the line, one after another. */
        PILLARS,
        /** Raikou: lightning chains between three points. */
        CHAIN,
        /** Cresselia: crescent blades orbit it and spiral outward. */
        CRESCENTS,
        /** Darkrai: the arena goes dark except a small circle around the cursor. */
        DARKNESS,
        /** Mewtwo: a psychic barrier that breaks any line drawn too close to it. */
        BARRIER,
        /** Lugia: wind pushes the line sideways. */
        GUST,
        /** Legendary birds: ice, thunder or fire falls in waves. */
        RAIN;

        public static AttackStyle byName(String name) {
            for (AttackStyle style : values()) {
                if (style.name().equalsIgnoreCase(name)) {
                    return style;
                }
            }
            return null;
        }
    }

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

        /** A boss moved into a new stage ({@code stage} from 0, of {@code stages}). */
        default void onStage(int stage, int stages) {}

        /** A loop bounced off a boss's shield at the start of a stage. */
        default void onShieldBlocked() {}

        /** A signature attack went off, for its sound. */
        default void onSignature(AttackStyle style) {}
    }

    public static final class Projectile {
        public float x, y, vx, vy, life;
        public final float radius;
        /** Turn rate towards the line, in radians a second; 0 flies straight. */
        float homing;

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

    public enum LaneKind { FISSURE, DIVE, AURORA, CHAIN }

    /**
     * A straight danger line across the arena from a legendary's signature attack. It shows a warning first, then
     * goes live from {@code a} towards {@code b} over {@code travel} seconds, and each part stays dangerous for
     * {@code active} seconds after the front passes it.
     */
    public static final class Lane {
        public final LaneKind kind;
        public final float ax, ay, bx, by, width, travel, active;
        /** Counts down while the warning shows, then goes negative while it is live. */
        public float timer;

        Lane(LaneKind kind, float ax, float ay, float bx, float by, float width, float warning, float travel,
             float active) {
            this.kind = kind;
            this.ax = ax;
            this.ay = ay;
            this.bx = bx;
            this.by = by;
            this.width = width;
            this.timer = warning;
            this.travel = travel;
            this.active = active;
        }

        public boolean live() {
            return timer <= 0;
        }

        /** How far along the front is, 0 to 1. */
        public float front() {
            return travel <= 0 ? (live() ? 1 : 0) : clamp(-timer / travel, 0, 1);
        }

        /** How far along the oldest still-dangerous part is, 0 to 1. */
        public float tail() {
            if (!live()) {
                return 0;
            }
            return travel <= 0 ? (-timer > active ? 1 : 0) : clamp((-timer - active) / travel, 0, 1);
        }

        boolean finished() {
            return -timer > travel + active;
        }
    }

    /** Kyogre's tidal wave: a band crossing the arena with one gap in it. */
    public static final class Wave {
        /** True when the band moves along x (it stands upright); false when it moves along y. */
        public final boolean alongX;
        public final float direction, gapCenter, gapHalf;
        public float pos, warning;
        public static final float BAND = 24f;

        Wave(boolean alongX, float direction, float gapCenter, float gapHalf, float warning) {
            this.alongX = alongX;
            this.direction = direction;
            this.gapCenter = gapCenter;
            this.gapHalf = gapHalf;
            this.warning = warning;
            float extent = alongX ? WIDTH : HEIGHT;
            this.pos = direction > 0 ? -BAND : extent + BAND;
        }

        boolean finished() {
            float extent = alongX ? WIDTH : HEIGHT;
            return direction > 0 ? pos > extent + BAND : pos < -BAND;
        }

        boolean hits(float px, float py) {
            float along = alongX ? px : py, across = alongX ? py : px;
            return warning <= 0 && Math.abs(along - pos) < BAND / 2 && Math.abs(across - gapCenter) > gapHalf;
        }
    }

    /** A beam from the Pokémon that sweeps through an arc. */
    public static final class Beam {
        public final float start, sweep, duration;
        public float timer;

        Beam(float start, float sweep, float warning, float duration) {
            this.start = start;
            this.sweep = sweep;
            this.timer = warning;
            this.duration = duration;
        }

        public boolean live() {
            return timer <= 0;
        }

        public float angle() {
            return start + sweep * clamp(-timer / duration, 0, 1);
        }
    }

    /** One of Cresselia's crescent blades, circling the Pokémon and drifting outward. */
    public static final class Orbiter {
        public float angle, distance, life;
        final float spin;

        Orbiter(float angle, float distance, float spin) {
            this.angle = angle;
            this.distance = distance;
            this.spin = spin;
            this.life = 3.6f;
        }
    }

    /** An attack part that starts a little later, like the second of Entei's flame pillars. */
    private static final class Pending {
        float delay;
        final Runnable action;

        Pending(float delay, Runnable action) {
            this.delay = delay;
            this.action = action;
        }
    }

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
    private final float baseDifficulty;
    private float difficulty;
    private float speed;
    private final float bossSpeed;
    private final float gainPerLoop;
    private final float lineLength;
    private final int level;
    public final float radius;
    public final AttackStyle signatureAttack;
    public final int maxEnergy;
    /** Stages of the capture: 1 for a normal one, 2 for an Alpha, 3 for a legendary. */
    public final int stages;
    /** 0 for a normal capture, 1 for an Alpha and 2 for a legendary (BossCapture.Kind ordinals). */
    public final int bossKind;
    private final List<List<AttackStyle>> stageAttacks = new ArrayList<>();

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

    // Boss captures
    private int stage;
    private float stageFloor, shieldTimer, shieldBlockFx;
    public final List<Lane> lanes = new ArrayList<>();
    public final List<Wave> waves = new ArrayList<>();
    public final List<Beam> beams = new ArrayList<>();
    public final List<Orbiter> orbiters = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private float orbitTimer, orbitAngle;
    private float darkTimer, barrierTimer, windTimer, windX, windY;
    /** Where the Pokémon was over the last two seconds, for Celebi's rewind: x, y pairs every 0.1 s. */
    private final float[] history = new float[40];
    private int historyHead, historySize;
    private float historyTimer;
    public float rewindFx = -1, rewindFromX, rewindFromY;

    public CaptureGame(String type, String secondaryType, int catchRate, int level, int energy, int maxEnergy,
                       float lineLength, float loopPower, float sizeHint, long seed, Listener listener) {
        this(type, secondaryType, catchRate, level, energy, maxEnergy, lineLength, loopPower, sizeHint, seed, listener,
                0, List.of(List.of()));
    }

    /**
     * {@code bossKind} is 1 for an Alpha and 2 for a legendary; {@code stageAttacks} names the attacks of each stage
     * (see {@link AttackStyle}), with an empty list meaning the Pokémon's type attacks.
     */
    public CaptureGame(String type, String secondaryType, int catchRate, int level, int energy, int maxEnergy,
                       float lineLength, float loopPower, float sizeHint, long seed, Listener listener, int bossKind,
                       List<List<String>> stageAttacks) {
        this.random = new Random(seed);
        this.bossKind = bossKind;
        this.stages = Math.max(1, stageAttacks.size());
        for (List<String> names : stageAttacks) {
            List<AttackStyle> styles = new ArrayList<>();
            for (String name : names) {
                AttackStyle style = AttackStyle.byName(name);
                if (style != null) {
                    styles.add(style);
                }
            }
            this.stageAttacks.add(styles);
        }
        if (this.stageAttacks.isEmpty()) {
            this.stageAttacks.add(List.of());
        }
        this.defenderTypes = secondaryType == null || secondaryType.isEmpty()
                ? new String[]{type} : new String[]{type, secondaryType};
        this.listener = listener;
        this.level = level;
        this.energy = energy;
        this.maxEnergy = maxEnergy;
        this.lineLength = lineLength;
        float catchFactor = clamp(catchRate, 1, 255) / 255f;
        float levelFactor = clamp(level, 1, 100) / 100f;
        this.baseDifficulty = clamp((1 - catchFactor) * 0.7f + levelFactor * 0.3f, 0, 1);
        this.difficulty = baseDifficulty;
        // Alphas are big and quick on their feet.
        this.bossSpeed = bossKind == 1 ? 1.1f : 1f;
        this.speed = (22f + 38f * difficulty) * bossSpeed;
        this.gainPerLoop = CaptureMath.gainPerLoop(catchRate, level, loopPower, stages);
        this.radius = bossKind == 1 ? clamp((9f + sizeHint * 5f) * 1.2f, 12f, 28f) : clamp(9f + sizeHint * 5f, 10f, 24f);
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
        return dashTimer > 0 || orbitTimer > 0;
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

    /** The stage the capture is in, from 0. */
    public int stage() {
        return stage;
    }

    /** Gauge share where stage {@code k} (from 0) begins. */
    public float stageMark(int k) {
        return k / (float) stages;
    }

    public boolean isBoss() {
        return bossKind > 0;
    }

    /** Right after a new stage starts, loops don't count. */
    public boolean isShielded() {
        return shieldTimer > 0;
    }

    public float shieldTime() {
        return shieldTimer;
    }

    /** 1 just after a loop bounced off the shield, fading to 0. */
    public float shieldBlockFx() {
        return shieldBlockFx;
    }

    /** How dark the arena is from Darkrai's attack, 0 to 1. */
    public float darkness() {
        return darkTimer <= 0 ? 0 : clamp(Math.min(darkTimer, DARK_TIME - darkTimer) / 0.4f, 0, 1);
    }

    /** Mewtwo's barrier radius around the Pokémon, or 0 when there is none. */
    public float barrierRadius() {
        return barrierTimer > 0 ? radius + 30f : 0;
    }

    /** The barrier only breaks lines after it has formed. */
    public boolean isBarrierUp() {
        return barrierTimer > 0 && barrierTimer < BARRIER_TIME - BARRIER_WARMUP;
    }

    public float barrierFade() {
        return barrierTimer <= 0 ? 0 : clamp(Math.min(BARRIER_TIME - barrierTimer, barrierTimer) / BARRIER_WARMUP, 0, 1);
    }

    public boolean isWindy() {
        return windTimer > 0;
    }

    public float windX() {
        return windX;
    }

    public float windY() {
        return windY;
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
                orbitTimer = 0;
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
                orbitTimer = 0;
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
                checkStage();
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
        lanes.clear();
        waves.clear();
        beams.clear();
        orbiters.clear();
        pending.clear();
        windupTimer = 0;
        dashTimer = 0;
        orbitTimer = 0;
        darkTimer = 0;
        barrierTimer = 0;
        windTimer = 0;
    }

    // ------------------------------------------------------------------ boss stages

    /** Moves a boss into its next stage once the gauge passes the stage's mark. */
    private void checkStage() {
        while (stage < stages - 1 && gauge >= stageMark(stage + 1) && gauge < 1f) {
            stage++;
            stageFloor = stageMark(stage);
            shieldTimer = SHIELD_TIME;
            clearAttacks();
            difficulty = clamp(baseDifficulty + STAGE_DIFFICULTY * stage, 0, 1);
            speed = (22f + 38f * difficulty) * bossSpeed * (1 + 0.12f * stage);
            attackCooldown = SHIELD_TIME + 0.3f;
            startledTimer = 0;
            listener.onStage(stage, stages);
        }
    }

    private AttackStyle pickAttack() {
        List<AttackStyle> styles = stageAttacks.get(Math.min(stage, stageAttacks.size() - 1));
        if (!styles.isEmpty()) {
            List<AttackStyle> usable = new ArrayList<>();
            for (AttackStyle style : styles) {
                // Ongoing effects aren't started twice.
                if ((style == AttackStyle.DARKNESS && darkTimer > 0) || (style == AttackStyle.BARRIER && barrierTimer > 0)
                        || (style == AttackStyle.GUST && windTimer > 0)) {
                    continue;
                }
                usable.add(style);
            }
            if (!usable.isEmpty()) {
                return usable.get(random.nextInt(usable.size()));
            }
            return signatureAttack;
        }
        if (isBoss() && stage > 0) {
            // A boss without a set of its own mixes in a second type attack in later stages.
            float roll = random.nextFloat();
            return roll < 0.2f ? AttackStyle.DASH : roll < 0.6f ? signatureAttack : secondaryAttack(signatureAttack);
        }
        return random.nextFloat() < 0.25f ? AttackStyle.DASH : signatureAttack;
    }

    private static AttackStyle secondaryAttack(AttackStyle style) {
        return switch (style) {
            case BURST -> AttackStyle.SHOCKWAVE;
            case DASH -> AttackStyle.ERUPTION;
            default -> AttackStyle.BURST;
        };
    }

    private void recordHistory(float dt) {
        historyTimer -= dt;
        if (historyTimer > 0) {
            return;
        }
        historyTimer = 0.1f;
        history[historyHead * 2] = x;
        history[historyHead * 2 + 1] = y;
        historyHead = (historyHead + 1) % (history.length / 2);
        historySize = Math.min(historySize + 1, history.length / 2);
    }

    /** Where the Pokémon was about {@code seconds} ago. */
    private float[] pastPosition(float seconds) {
        int back = Math.min(historySize, Math.max(1, Math.round(seconds / 0.1f)));
        if (back == 0) {
            return new float[]{x, y};
        }
        int slots = history.length / 2;
        int index = ((historyHead - back) % slots + slots) % slots;
        return new float[]{history[index * 2], history[index * 2 + 1]};
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
        if (enclosed && shieldTimer > 0) {
            // A boss shielding itself at the start of a stage: the loop bounces off.
            enclosed = false;
            shieldBlockFx = 1f;
            listener.onShieldBlocked();
        }
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
        checkStage();
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
                checkStage();
            }
        } else if (sinceLastLoop > GAUGE_DECAY_DELAY) {
            // A boss never loses a stage it has reached.
            gauge = Math.max(Math.min(gauge, stageFloor), gauge - GAUGE_DECAY_RATE * dt);
        }
        shieldTimer = Math.max(0, shieldTimer - dt);
        shieldBlockFx = Math.max(0, shieldBlockFx - dt * 2.5f);
        recordHistory(dt);

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
        if (isHeld()) {
            orbitTimer = 0;
        }
        if (orbitTimer > 0) {
            // Latios and Latias lap the arena at speed.
            orbitTimer -= dt;
            orbitAngle += 2.8f * dt;
            float tx = WIDTH / 2 + (float) Math.cos(orbitAngle) * (WIDTH / 2 - radius - 24);
            float ty = HEIGHT / 2 + (float) Math.sin(orbitAngle) * (HEIGHT / 2 - radius - 16);
            float blend = Math.min(1, dt * 8);
            vx += ((tx - x) * 6 - vx) * blend;
            vy += ((ty - y) * 6 - vy) * blend;
        } else if (windupTimer > 0 || isHeld() || !beams.isEmpty()) {
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
            orbitTimer = 0;
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
        } else if (dashTimer <= 0 && orbitTimer <= 0 && beams.isEmpty() && !isHeld() && spookTimer <= 0 && !isPinned()) {
            attackCooldown -= dt;
            if (attackCooldown <= 0) {
                pendingAttack = pickAttack();
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
        updateSignatures(dt);
    }

    private void updateSignatures(float dt) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            Pending part = pending.get(i);
            part.delay -= dt;
            if (part.delay <= 0) {
                pending.remove(i);
                part.action.run();
            }
        }
        lanes.removeIf(lane -> {
            lane.timer -= dt;
            return lane.finished();
        });
        waves.removeIf(wave -> {
            if (wave.warning > 0) {
                wave.warning -= dt;
            } else {
                wave.pos += wave.direction * (110f + 40f * difficulty) * dt;
            }
            return wave.finished();
        });
        beams.removeIf(beam -> {
            beam.timer -= dt;
            return -beam.timer > beam.duration;
        });
        Zone ring = zone(ZoneKind.FIRE_RING);
        orbiters.removeIf(orbiter -> {
            orbiter.life -= dt;
            orbiter.angle += orbiter.spin * dt;
            orbiter.distance = Math.min(130f, orbiter.distance + (24f + 14f * difficulty) * dt);
            float ox = orbiterX(orbiter), oy = orbiterY(orbiter);
            return orbiter.life <= 0 || hitsWall(ox, oy, 5f) || (ring != null && !ring.contains(ox, oy, 0));
        });
        for (Projectile p : projectiles) {
            if (p.homing > 0 && !line.isEmpty()) {
                float[] tip = line.get(line.size() - 1);
                float want = (float) Math.atan2(tip[1] - p.y, tip[0] - p.x);
                float have = (float) Math.atan2(p.vy, p.vx);
                float turn = (float) Math.atan2(Math.sin(want - have), Math.cos(want - have));
                float step = Math.max(-p.homing * dt, Math.min(p.homing * dt, turn));
                float s = (float) Math.hypot(p.vx, p.vy);
                p.vx = (float) Math.cos(have + step) * s;
                p.vy = (float) Math.sin(have + step) * s;
            }
        }
        darkTimer = Math.max(0, darkTimer - dt);
        barrierTimer = Math.max(0, barrierTimer - dt);
        if (windTimer > 0) {
            windTimer -= dt;
            // The wind pushes the line sideways, which can drag it into attacks.
            for (float[] point : line) {
                point[0] = clamp(point[0] + windX * dt, 0, WIDTH);
                point[1] = clamp(point[1] + windY * dt, 0, HEIGHT);
            }
        }
        if (rewindFx >= 0) {
            rewindFx = rewindFx + dt > REWIND_FX_TIME ? -1 : rewindFx + dt;
        }
    }

    public float orbiterX(Orbiter orbiter) {
        return x + (float) Math.cos(orbiter.angle) * orbiter.distance;
    }

    public float orbiterY(Orbiter orbiter) {
        return y + (float) Math.sin(orbiter.angle) * orbiter.distance;
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
            default -> launchSignature(style, blind);
        }
    }

    /** The legendary signature attacks. Aimed ones aim at random spots while a Dark assist blinds the Pokémon. */
    private void launchSignature(AttackStyle style, boolean blind) {
        listener.onSignature(style);
        switch (style) {
            case FISSURE -> {
                int count = 2 + Math.round(difficulty);
                for (int i = 0; i < count; i++) {
                    float[] at = i == 0 && !blind ? aimPoint() : randomPoint();
                    float angle = random.nextFloat() * (float) Math.PI;
                    addCrossingLane(LaneKind.FISSURE, at, angle, 12f, 0.9f + i * 0.4f, 0.7f, 0.45f);
                }
            }
            case TIDAL_WAVE -> {
                boolean alongX = random.nextFloat() < 0.7f;
                float extent = alongX ? HEIGHT : WIDTH;
                float gapHalf = 26f - 8f * difficulty;
                float gap = gapHalf + 6 + random.nextFloat() * (extent - 2 * gapHalf - 12);
                waves.add(new Wave(alongX, random.nextBoolean() ? 1 : -1, gap, gapHalf, 0.8f));
            }
            case DIVE -> {
                int count = 2 + (difficulty > 0.7f ? 1 : 0);
                for (int i = 0; i < count; i++) {
                    float[] at = blind ? randomPoint() : aimPoint();
                    float angle = random.nextFloat() * (float) Math.PI;
                    addCrossingLane(LaneKind.DIVE, at, angle, 16f, 0.7f + i * 0.5f, 0.35f, 0.25f);
                }
            }
            case BEAM -> {
                float[] aim = blind ? randomPoint() : aimPoint();
                float sweep = (1.3f + 0.5f * difficulty) * (random.nextBoolean() ? 1 : -1);
                float centre = (float) Math.atan2(aim[1] - y, aim[0] - x);
                beams.add(new Beam(centre - sweep / 2, sweep, 0.55f, 1.3f));
            }
            case MIST_BALLS -> {
                int count = 4 + Math.round(2 * difficulty);
                float[] aim = blind ? randomPoint() : aimPoint();
                float centre = (float) Math.atan2(aim[1] - y, aim[0] - x);
                for (int i = 0; i < count; i++) {
                    float angle = centre + (i - (count - 1) / 2f) * 0.45f;
                    Projectile p = new Projectile(x, y, (float) Math.cos(angle) * 75f, (float) Math.sin(angle) * 75f, 4.5f);
                    p.homing = blind ? 0 : 1.6f + difficulty;
                    p.life = 3.5f;
                    projectiles.add(p);
                }
            }
            case ORBIT_RUSH -> {
                orbitTimer = 2.2f + difficulty;
                orbitAngle = (float) Math.atan2((y - HEIGHT / 2) / HEIGHT, (x - WIDTH / 2) / WIDTH);
            }
            case REWIND -> {
                boolean confined = false;
                for (Zone zone : zones) {
                    confined |= zone.confines();
                }
                if (!confined) {
                    // Blinks back to where it was, unless an assist holds it in place.
                    float[] past = pastPosition(1.5f);
                    rewindFromX = x;
                    rewindFromY = y;
                    rewindFx = 0;
                    x = clamp(past[0], radius + 2, WIDTH - radius - 2);
                    y = clamp(past[1], radius + 2, HEIGHT - radius - 2);
                    vx = 0;
                    vy = 0;
                }
                // The newest part of the line is undone, which breaks the loop being drawn without hurting the styler.
                line.removeIf(point -> clock - point[2] < 0.6f);
                for (int i = 0; i < 6; i++) {
                    float angle = (float) (Math.PI * 2 * i / 6) + random.nextFloat() * 0.3f;
                    float s = 70f + 40f * difficulty;
                    projectiles.add(new Projectile(x, y, (float) Math.cos(angle) * s, (float) Math.sin(angle) * s, 4f));
                }
            }
            case AURORA -> {
                float[] at = blind ? randomPoint() : aimPoint();
                float angle = random.nextInt(4) * (float) (Math.PI / 4);
                addCrossingLane(LaneKind.AURORA, at, angle, 10f, 0.9f, 0f, 2.6f + difficulty);
            }
            case PILLARS -> {
                int count = 3 + Math.round(2 * difficulty);
                for (int i = 0; i < count; i++) {
                    pending.add(new Pending(i * 0.32f, () -> {
                        float[] at = blindTimer > 0 || line.isEmpty() ? randomPoint() : line.get(line.size() - 1);
                        hazards.add(new Hazard(clamp(at[0], 0, WIDTH), clamp(at[1], 0, HEIGHT), 14f, 0.55f));
                    }));
                }
            }
            case CHAIN -> {
                float[] centre = blind ? randomPoint() : aimPoint();
                float size = 50f + 25f * difficulty;
                float turn = random.nextFloat() * (float) (Math.PI * 2);
                float[][] corners = new float[3][];
                for (int i = 0; i < 3; i++) {
                    float angle = turn + (float) (Math.PI * 2 * i / 3);
                    corners[i] = new float[]{clamp(centre[0] + (float) Math.cos(angle) * size, 8, WIDTH - 8),
                            clamp(centre[1] + (float) Math.sin(angle) * size, 8, HEIGHT - 8)};
                }
                for (int i = 0; i < 3; i++) {
                    float[] a = corners[i], b = corners[(i + 1) % 3];
                    lanes.add(new Lane(LaneKind.CHAIN, a[0], a[1], b[0], b[1], 6f, 0.8f, 0f, 1.5f));
                }
            }
            case CRESCENTS -> {
                int count = 3 + Math.round(2 * difficulty);
                float spin = random.nextBoolean() ? 2.1f : -2.1f;
                for (int i = 0; i < count; i++) {
                    orbiters.add(new Orbiter((float) (Math.PI * 2 * i / count), radius + 8, spin));
                }
            }
            case DARKNESS -> darkTimer = DARK_TIME;
            case BARRIER -> barrierTimer = BARRIER_TIME;
            case GUST -> {
                windTimer = WIND_TIME;
                float angle = random.nextFloat() * (float) (Math.PI * 2);
                float strength = 34f + 20f * difficulty;
                windX = (float) Math.cos(angle) * strength;
                windY = (float) Math.sin(angle) * strength;
            }
            case RAIN -> {
                int waves = 3;
                for (int w = 0; w < waves; w++) {
                    pending.add(new Pending(w * 0.5f, () -> {
                        int drops = 4 + Math.round(2 * difficulty);
                        for (int i = 0; i < drops; i++) {
                            float[] at;
                            if (blindTimer <= 0 && !line.isEmpty() && random.nextFloat() < 0.5f) {
                                float[] p = line.get(random.nextInt(line.size()));
                                at = new float[]{p[0] + (random.nextFloat() - 0.5f) * 50f, p[1] + (random.nextFloat() - 0.5f) * 50f};
                            } else {
                                at = randomPoint();
                            }
                            hazards.add(new Hazard(clamp(at[0], 0, WIDTH), clamp(at[1], 0, HEIGHT), 11f,
                                    0.6f + random.nextFloat() * 0.2f));
                        }
                    }));
                }
            }
            default -> {
            }
        }
    }

    /** A lane through {@code at} at {@code angle}, running edge to edge across the arena. */
    private void addCrossingLane(LaneKind kind, float[] at, float angle, float width, float warning, float travel,
                                 float active) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        float reach = WIDTH + HEIGHT;
        // Clip the long line to the arena so the front travels across what the player can see.
        float t0 = -reach, t1 = reach;
        if (Math.abs(dx) > 1e-4f) {
            float a = (0 - at[0]) / dx, b = (WIDTH - at[0]) / dx;
            t0 = Math.max(t0, Math.min(a, b));
            t1 = Math.min(t1, Math.max(a, b));
        }
        if (Math.abs(dy) > 1e-4f) {
            float a = (0 - at[1]) / dy, b = (HEIGHT - at[1]) / dy;
            t0 = Math.max(t0, Math.min(a, b));
            t1 = Math.min(t1, Math.max(a, b));
        }
        if (random.nextBoolean()) {
            float swap = t0;
            t0 = t1;
            t1 = swap;
        }
        lanes.add(new Lane(kind, at[0] + dx * t0, at[1] + dy * t0, at[0] + dx * t1, at[1] + dy * t1, width, warning,
                travel, active));
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
            breakLine(isDashing() ? 3 + level / 25 : 1 + level / 40);
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
        for (Orbiter orbiter : orbiters) {
            if (distanceToLine(orbiterX(orbiter), orbiterY(orbiter)) < 6f) {
                orbiter.life = 0;
                if (!iron) {
                    breakLine(3 + level / 30);
                    return;
                }
            }
        }
        if (iron) {
            return;
        }
        if (signatureHit()) {
            breakLine(3 + level / 30);
            return;
        }
        for (Hazard hazard : hazards) {
            if (hazard.exploding() && distanceToLine(hazard.x, hazard.y) < hazard.radius) {
                breakLine(4 + level / 25);
                return;
            }
        }
    }

    /** Whether a live lane, wave, beam or barrier touches the line. */
    private boolean signatureHit() {
        float barrier = isBarrierUp() ? barrierRadius() : 0;
        for (float[] point : line) {
            float px = point[0], py = point[1];
            if (barrier > 0 && Math.hypot(px - x, py - y) < barrier) {
                return true;
            }
            for (Lane lane : lanes) {
                if (!lane.live()) {
                    continue;
                }
                float dx = lane.bx - lane.ax, dy = lane.by - lane.ay;
                float lenSq = dx * dx + dy * dy;
                if (lenSq < 1) {
                    continue;
                }
                float t = ((px - lane.ax) * dx + (py - lane.ay) * dy) / lenSq;
                if (t < lane.tail() || t > lane.front()) {
                    continue;
                }
                float cx = lane.ax + dx * t, cy = lane.ay + dy * t;
                if (Math.hypot(px - cx, py - cy) < lane.width / 2 + 1) {
                    return true;
                }
            }
            for (Wave wave : waves) {
                if (wave.hits(px, py)) {
                    return true;
                }
            }
            for (Beam beam : beams) {
                if (!beam.live()) {
                    continue;
                }
                float dx = px - x, dy = py - y;
                float dist = (float) Math.hypot(dx, dy);
                if (dist < radius) {
                    continue;
                }
                float angle = beam.angle();
                float along = dx * (float) Math.cos(angle) + dy * (float) Math.sin(angle);
                float across = -dx * (float) Math.sin(angle) + dy * (float) Math.cos(angle);
                if (along > 0 && Math.abs(across) < 6f) {
                    return true;
                }
            }
        }
        return false;
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
        gauge = Math.max(Math.min(gauge, stageFloor), gauge * 0.85f);
        // Bosses hit harder in later stages.
        damage += isBoss() ? stage : 0;
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
