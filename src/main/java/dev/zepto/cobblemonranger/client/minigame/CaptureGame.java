package dev.zepto.cobblemonranger.client.minigame;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

    public enum Phase { INTRO, PLAYING, CAPTURED, FAILED }

    public enum AttackStyle { DASH, BURST, SHOCKWAVE, ERUPTION }

    public interface Listener {
        default void onLoop(boolean enclosed, int loops) {}

        default void onLineBroken(int damage) {}

        default void onWindup(AttackStyle style) {}

        default void onCaptured() {}

        default void onFailed() {}
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
    public float hitFlash;
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Shockwave> shockwaves = new ArrayList<>();
    public final List<Hazard> hazards = new ArrayList<>();
    public final List<LoopFlash> flashes = new ArrayList<>();

    public CaptureGame(String type, int catchRate, int level, int energy, int maxEnergy, float lineLength,
                       float loopPower, float sizeHint, long seed, Listener listener) {
        this.random = new Random(seed);
        this.listener = listener;
        this.level = level;
        this.energy = energy;
        this.maxEnergy = maxEnergy;
        this.lineLength = lineLength;
        float catchFactor = clamp(catchRate, 1, 255) / 255f;
        float levelFactor = clamp(level, 1, 100) / 100f;
        this.difficulty = clamp((1 - catchFactor) * 0.7f + levelFactor * 0.3f, 0, 1);
        this.speed = 22f + 38f * difficulty;
        this.gainPerLoop = clamp(0.16f * (0.45f + catchFactor) * (1.25f - levelFactor * 0.6f) * loopPower, 0.035f, 0.34f);
        this.radius = clamp(9f + sizeHint * 5f, 10f, 24f);
        this.signatureAttack = attackFor(type);
        this.wanderAngle = random.nextFloat() * (float) (Math.PI * 2);
        this.attackCooldown = nextCooldown() * 0.6f;
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

    /** 0 for a point drawn just now, 1 for one about to fade. */
    public float pointAge(float[] point) {
        return clamp((clock - point[2]) / LINE_LIFETIME, 0, 1);
    }

    public float lineUsage() {
        return Math.min(1f, totalLineLength() / lineLength);
    }

    // ------------------------------------------------------------------ input

    public void beginStroke(float px, float py) {
        if (phase != Phase.PLAYING) {
            return;
        }
        drawing = true;
        strokeBroken = false;
        line.clear();
        line.add(new float[]{clamp(px, 0, WIDTH), clamp(py, 0, HEIGHT), clock});
    }

    public void dragStroke(float px, float py) {
        if (!drawing || strokeBroken || phase != Phase.PLAYING) {
            return;
        }
        px = clamp(px, 0, WIDTH);
        py = clamp(py, 0, HEIGHT);
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
        boolean enclosed = pointInPolygon(x, y, xs, ys);
        flashes.add(new LoopFlash(xs, ys, enclosed));
        if (enclosed) {
            loops++;
            gauge = Math.min(1f, gauge + gainPerLoop);
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
            listener.onCaptured();
        }
    }

    private void trimLine() {
        while (line.size() > 2 && totalLineLength() > lineLength) {
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
        sinceLastLoop += dt;
        if (sinceLastLoop > GAUGE_DECAY_DELAY) {
            gauge = Math.max(0, gauge - GAUGE_DECAY_RATE * dt);
        }

        movePokemon(dt);
        updateAttacks(dt);
        checkLineHits();
    }

    private void movePokemon(float dt) {
        if (windupTimer > 0) {
            vx *= 0.8f;
            vy *= 0.8f;
        } else if (dashTimer <= 0) {
            wanderTimer -= dt;
            if (wanderTimer <= 0) {
                // Occasionally pause, like the DS Pokémon do between movements.
                paused = !paused && random.nextFloat() < 0.15f;
                wanderAngle += (random.nextFloat() - 0.5f) * 2.4f;
                wanderTimer = paused ? 0.4f + random.nextFloat() * 0.5f : 0.8f + random.nextFloat() * 1.6f;
            }
            float targetSpeed = paused ? 0 : speed * (startledTimer > 0 ? 2.2f : 1f);
            float tx = (float) Math.cos(wanderAngle) * targetSpeed;
            float ty = (float) Math.sin(wanderAngle) * targetSpeed;
            float blend = Math.min(1, dt * 3);
            vx += (tx - vx) * blend;
            vy += (ty - vy) * blend;
            startledTimer = Math.max(0, startledTimer - dt);
        }

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
        } else if (dashTimer <= 0) {
            attackCooldown -= dt;
            if (attackCooldown <= 0) {
                pendingAttack = random.nextFloat() < 0.25f ? AttackStyle.DASH : signatureAttack;
                windupTimer = WINDUP_TIME;
                attackCooldown = nextCooldown();
                listener.onWindup(pendingAttack);
            }
        }

        Iterator<Projectile> projectileIt = projectiles.iterator();
        while (projectileIt.hasNext()) {
            Projectile p = projectileIt.next();
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt;
            if (p.life <= 0 || p.x < -10 || p.x > WIDTH + 10 || p.y < -10 || p.y > HEIGHT + 10) {
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

    private void launch(AttackStyle style) {
        switch (style) {
            case DASH -> {
                float[] aim = aimPoint();
                float angle = (float) Math.atan2(aim[1] - y, aim[0] - x);
                float dashSpeed = speed * 4f;
                vx = (float) Math.cos(angle) * dashSpeed;
                vy = (float) Math.sin(angle) * dashSpeed;
                dashTimer = 0.45f;
            }
            case BURST -> {
                int count = 8;
                int waves = difficulty > 0.6f ? 2 : 1;
                float projectileSpeed = 80f + 50f * difficulty;
                float offset = random.nextFloat() * (float) Math.PI;
                for (int w = 0; w < waves; w++) {
                    for (int i = 0; i < count; i++) {
                        float angle = offset + (float) (Math.PI * 2 * i / count) + w * (float) (Math.PI / count);
                        float s = projectileSpeed * (1f - w * 0.35f);
                        projectiles.add(new Projectile(x, y, (float) Math.cos(angle) * s, (float) Math.sin(angle) * s, 4f));
                    }
                }
            }
            case SHOCKWAVE -> shockwaves.add(new Shockwave(x, y, radius + 60f + 40f * difficulty));
            case ERUPTION -> {
                int count = 3 + Math.round(2 * difficulty);
                for (int i = 0; i < count; i++) {
                    float hx, hy;
                    if (!line.isEmpty() && random.nextFloat() < 0.7f) {
                        float[] p = line.get(random.nextInt(line.size()));
                        hx = p[0] + (random.nextFloat() - 0.5f) * 30f;
                        hy = p[1] + (random.nextFloat() - 0.5f) * 30f;
                    } else {
                        hx = 20 + random.nextFloat() * (WIDTH - 40);
                        hy = 20 + random.nextFloat() * (HEIGHT - 40);
                    }
                    hazards.add(new Hazard(clamp(hx, 0, WIDTH), clamp(hy, 0, HEIGHT), 18f + 6f * difficulty,
                            0.9f + i * 0.12f));
                }
            }
        }
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
        // The Pokémon's own body breaks the line; harder when it is charging.
        if (distanceToLine(x, y) < radius * 0.7f) {
            breakLine(dashTimer > 0 ? 3 + level / 25 : 1 + level / 40);
            return;
        }
        for (Projectile p : projectiles) {
            if (distanceToLine(p.x, p.y) < p.radius + 1) {
                p.life = 0;
                breakLine(2 + level / 35);
                return;
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
                    breakLine(3 + level / 30);
                    return;
                }
            }
        }
        for (Hazard hazard : hazards) {
            if (hazard.exploding() && distanceToLine(hazard.x, hazard.y) < hazard.radius) {
                breakLine(4 + level / 25);
                return;
            }
        }
    }

    private void breakLine(int damage) {
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
