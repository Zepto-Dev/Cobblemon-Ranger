package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.client.minigame.CaptureGame;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Draws the wild Pokémon's attacks with {@link AttackArt} sprites, plus the purely visual particles around them:
 * trails behind dashes and projectiles, energy gathering while it winds up and debris thrown out by blasts. Works in
 * arena units; the screen passes its offset and scale when drawing.
 */
final class AttackEffects {
    private static final int MAX_PARTICLES = 240;
    /** Projectile sprites are drawn this many times their hit radius across. */
    private static final float MISSILE_SIZE = 4.4f;

    private static final class Particle {
        float x, y, vx, vy, age;
        final float life, size, drag;
        final AttackArt.Element element;
        final int tint;

        Particle(AttackArt.Element element, int tint, float x, float y, float vx, float vy, float life, float size,
                 float drag) {
            this.element = element;
            this.tint = tint;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.size = size;
            this.drag = drag;
        }
    }

    private final CaptureGame game;
    private final AttackArt art;
    private final Random random = new Random();
    private final List<Particle> particles = new ArrayList<>();
    private final Set<CaptureGame.Hazard> exploded = Collections.newSetFromMap(new IdentityHashMap<>());
    private float time, dashEmit, chargeEmit;

    AttackEffects(CaptureGame game, AttackArt art) {
        this.game = game;
        this.art = art;
    }

    // ------------------------------------------------------------------ simulation

    void tick(float dt) {
        time += dt;
        tickSignatures(dt);
        if (game.isDashing()) {
            dashEmit += dt * 40;
            for (; dashEmit >= 1; dashEmit--) {
                float jitter = game.radius * 0.6f;
                emit(art.particle(), 0xFFFFFF, game.x + spread(jitter), game.y + spread(jitter),
                        -game.vx * 0.15f + spread(15), -game.vy * 0.15f + spread(15), 0.45f, 16, 2);
                if (random.nextBoolean()) {
                    emit(art.dust(), art.dustTint(), game.x + spread(jitter), game.y + game.radius * 0.7f,
                            spread(20), -6 + spread(8), 0.5f, 18, 3);
                }
            }
        } else {
            dashEmit = 0;
        }
        if (game.isWindingUp()) {
            // Energy gathers on the Pokémon before it attacks.
            chargeEmit += dt * 30;
            for (; chargeEmit >= 1; chargeEmit--) {
                float angle = random.nextFloat() * (float) (Math.PI * 2);
                float dist = game.radius * 2.2f;
                float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                emit(art.particle(), 0xFFFFFF, game.x + cos * dist, game.y + sin * dist,
                        -cos * dist / 0.35f, -sin * dist / 0.35f, 0.35f, 14, 0);
            }
        } else {
            chargeEmit = 0;
        }
        for (CaptureGame.Projectile p : game.projectiles) {
            if (random.nextFloat() < dt * 14) {
                emit(art.particle(), 0xFFFFFF, p.x, p.y, spread(10), spread(10), 0.3f, 11, 1);
            }
        }
        exploded.removeIf(hazard -> !game.hazards.contains(hazard));
        for (CaptureGame.Hazard hazard : game.hazards) {
            if (hazard.exploding() && exploded.add(hazard)) {
                burst(hazard);
            }
        }

        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle particle = it.next();
            particle.age += dt;
            if (particle.age >= particle.life) {
                it.remove();
                continue;
            }
            particle.x += particle.vx * dt;
            particle.y += particle.vy * dt;
            float slow = Math.max(0, 1 - particle.drag * dt);
            particle.vx *= slow;
            particle.vy *= slow;
        }
    }

    /** Debris and dust thrown out when a blast goes off. */
    private void burst(CaptureGame.Hazard hazard) {
        AttackArt.Element debris = art.debris();
        for (int i = 0; i < 12; i++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float speed = 50 + random.nextFloat() * 50;
            boolean dust = i % 2 == 0;
            emit(dust ? art.dust() : debris, dust ? art.dustTint() : 0xFFFFFF,
                    hazard.x + (float) Math.cos(angle) * hazard.radius * 0.5f,
                    hazard.y + (float) Math.sin(angle) * hazard.radius * 0.5f,
                    (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed, 0.55f, dust ? 20 : 16, 4);
        }
    }

    private void emit(AttackArt.Element element, int tint, float x, float y, float vx, float vy, float life,
                      float size, float drag) {
        if (particles.size() < MAX_PARTICLES) {
            particles.add(new Particle(element, tint, x, y, vx, vy, life * (0.8f + random.nextFloat() * 0.4f), size,
                    drag));
        }
    }

    private float spread(float amount) {
        return (random.nextFloat() - 0.5f) * 2 * amount;
    }

    // ------------------------------------------------------------------ drawing

    /** Warning markers and falling boulders, drawn on the ground under the Pokémon. */
    void drawGround(GuiGraphics graphics, float ox, float oy, float scale) {
        drawSignatureWarnings(graphics, ox, oy, scale);
        AttackArt.begin();
        for (CaptureGame.Hazard hazard : game.hazards) {
            if (hazard.exploding()) {
                continue;
            }
            float hx = ox + hazard.x * scale, hy = oy + hazard.y * scale;
            float size = hazard.radius * 2 * scale * 32 / 28f;
            if (hazard.style != null) {
                // Entei's pillars warn with the fire marker; the birds' storm with a shadow ring.
                float pulse = 0.75f + 0.25f * (float) Math.sin(hazard.timer * 20);
                AttackArt.blast(graphics, signatureBlast(hazard, true), (int) (time * 8) % 2, hx, hy, size, 0xFFFFFF, pulse);
            } else if (art.blast() == AttackArt.Blast.ROCKFALL) {
                AttackArt.blast(graphics, art.blast(), hazard.timer < 0.45f ? 1 : 0, hx, hy, size, 0xFFFFFF, 1);
                // The boulder drops in from above the arena and lands as the timer runs out.
                float drop = hazard.timer * 140 * scale;
                AttackArt.sprite(graphics, AttackArt.Element.ROCK, (int) (time * 10) % 4, hx, hy - drop,
                        hazard.radius * 2.6f * scale, 0, 0xFFFFFF, 1);
            } else {
                float pulse = 0.75f + 0.25f * (float) Math.sin(hazard.timer * 20);
                AttackArt.blast(graphics, art.blast(), (int) (time * 8) % 2, hx, hy, size, blastTint(), pulse);
            }
        }
    }

    /** Blasts, shockwaves, projectiles and particles, drawn over the Pokémon. */
    void drawAir(GuiGraphics graphics, float ox, float oy, float scale) {
        drawSignatures(graphics, ox, oy, scale);
        Shapes shapes = new Shapes(graphics);
        float speed = (float) Math.hypot(game.vx, game.vy);
        if (game.isDashing() && speed > 1) {
            // Speed lines streaming off the Pokémon as it charges.
            float dx = -game.vx / speed, dy = -game.vy / speed;
            for (int i = -2; i <= 2; i++) {
                float side = i * game.radius * 0.45f;
                float start = game.radius * (0.3f + 0.25f * Math.abs(i));
                float length = game.radius * (2.6f - 0.4f * Math.abs(i));
                float x1 = game.x - dy * side + dx * start, y1 = game.y + dx * side + dy * start;
                int alpha = 0xA0 - 0x20 * Math.abs(i);
                shapes.line(ox + x1 * scale, oy + y1 * scale, ox + (x1 + dx * length) * scale,
                        oy + (y1 + dy * length) * scale, 2f * scale, (alpha << 24) | streakColor());
            }
        }
        for (CaptureGame.Shockwave wave : game.shockwaves) {
            float progress = wave.radius / wave.maxRadius;
            int alpha = (int) (0x90 * (1 - progress * 0.7f));
            shapes.ring(ox + wave.x * scale, oy + wave.y * scale, wave.radius * scale, 2.5f * scale,
                    (alpha << 24) | (art.color() & 0xFFFFFF));
        }
        graphics.flush();

        AttackArt.begin();
        for (CaptureGame.Hazard hazard : game.hazards) {
            if (!hazard.exploding()) {
                continue;
            }
            int frame = 2 + Math.min(3, (int) (-hazard.timer / 0.3f * 4));
            AttackArt.Blast blast = hazard.style != null ? signatureBlast(hazard, false) : art.blast();
            AttackArt.blast(graphics, blast, frame, ox + hazard.x * scale, oy + hazard.y * scale,
                    hazard.radius * 2.4f * scale * 32 / 30f, hazard.style != null ? 0xFFFFFF : blastTint(), 1);
            if (hazard.style == CaptureGame.AttackStyle.PILLARS) {
                // A tongue of flame shooting up out of each burst.
                AttackArt.sprite(graphics, AttackArt.Element.FIRE, (int) (time * 12) % 4, ox + hazard.x * scale,
                        oy + (hazard.y - hazard.radius * 1.1f) * scale, hazard.radius * 1.5f * scale,
                        (float) -Math.PI / 2, 0xFFFFFF, 1);
            }
        }
        for (CaptureGame.Hazard hazard : game.hazards) {
            if (hazard.style != CaptureGame.AttackStyle.RAIN || hazard.exploding()) {
                continue;
            }
            // The birds' storm falling onto its markers: Articuno's icicles, Moltres's fireballs.
            AttackArt.Element drop = rainKind();
            if (drop != AttackArt.Element.ELECTRIC) {
                AttackArt.sprite(graphics, drop, drop.oriented ? 0 : (int) (time * 12) % 4, ox + hazard.x * scale,
                        oy + (hazard.y - fallHeight(hazard)) * scale, 18 * scale, drop.oriented ? (float) Math.PI / 2 : 0,
                        0xFFFFFF, 1);
            }
        }
        for (CaptureGame.Shockwave wave : game.shockwaves) {
            // A ring of the type's particles riding the wave's edge.
            float progress = wave.radius / wave.maxRadius;
            int count = Math.max(8, (int) (wave.radius * Math.PI * 2 / 12));
            float spin = time * 0.8f;
            for (int i = 0; i < count; i++) {
                float angle = spin + (float) (Math.PI * 2 * i / count);
                float px = ox + (wave.x + (float) Math.cos(angle) * wave.radius) * scale;
                float py = oy + (wave.y + (float) Math.sin(angle) * wave.radius) * scale;
                int column = 4 + (i + (int) (time * 10)) % 2;
                AttackArt.sprite(graphics, art.particle(), column, px, py, 18 * scale, 0, 0xFFFFFF,
                        1 - progress * progress * 0.6f);
            }
        }
        for (CaptureGame.Projectile p : game.projectiles) {
            if (p.style == CaptureGame.AttackStyle.MIST_BALLS) {
                AttackArt.signature(graphics, AttackArt.SIGNATURE_MIST_BALL, ox + p.x * scale, oy + p.y * scale,
                        p.radius * 3.6f * scale, 0, 0xFFFFFF, 1);
                continue;
            }
            AttackArt.Element missile = art.missile();
            int frame = (int) (time * 12 + System.identityHashCode(p)) % 4;
            float angle = missile.oriented ? (float) Math.atan2(p.vy, p.vx) : 0;
            AttackArt.sprite(graphics, missile, frame, ox + p.x * scale, oy + p.y * scale,
                    p.radius * MISSILE_SIZE * scale, angle, 0xFFFFFF, 1);
        }
        for (Particle particle : particles) {
            float t = particle.age / particle.life;
            int column = 4 + (int) (particle.age * 10) % 2 + (t > 0.55f ? 2 : 0);
            float alpha = t < 0.6f ? 1 : 1 - (t - 0.6f) / 0.4f;
            AttackArt.sprite(graphics, particle.element, column, ox + particle.x * scale, oy + particle.y * scale,
                    particle.size * scale, 0, particle.tint, alpha);
        }
    }

    // ------------------------------------------------------------------ legendary signature attacks

    private static final int OUTLINE = 0x1A1C2C;
    // Five-shade palettes from tools/attack_textures.py, outline to highlight.
    private static final int[] LAVA = {0x3A0A06, 0xA0200C, 0xE85A10, 0xFFA020, 0xFFE070};
    private static final int[] ELECTRIC = {0x6A4A0A, 0xD89A12, 0xFFD23F, 0xFFF08A, 0xFFFFFF};
    private static final int[] PSYCHIC = {0x6A1440, 0xC0306E, 0xF0609A, 0xFFA8CA, 0xFFF0F6};
    private static final int[] GRASS = {0x1C4418, 0x3C9A30, 0x6CD04A, 0xB8F080, 0xF0FFD0};
    private static final int[] FLYING = {0x3A3A7A, 0x7A84D0, 0xB4BEF4, 0xE2E8FF, 0xFFFFFF};
    private static final int[] DRAGON = {0x1E0E5A, 0x4A26B0, 0x7A5AF0, 0xB09AFF, 0x7AF5E6};
    private static final int[] WATER_PAL = {0x1A3A7A, 0x2E6AD0, 0x4FA0F0, 0x9AD4FF, 0xFFFFFF};
    private static final int CRACK = 0x2A1408, CRACK_INNER = 0x4A2A14;
    private static final int AURORA = 0x7FF0FF, AURORA_PINK = 0xE0A8FF, AURORA_MINT = 0x9AF8D8;
    private static final int FOAM = 0xE8F6FF, CRESCENT = 0xF2A8FF, AFTERIMAGE = 0xB09AFF;

    /** Each fissure's crack, as points in arena units, so the crack and its lava follow the same zigzag. */
    private final java.util.Map<CaptureGame.Lane, float[]> cracks = new IdentityHashMap<>();
    /** Where the Pokémon has been during Latios and Latias's laps, newest first, for the afterimage rings. */
    private final float[] trail = new float[2 * TRAIL];
    private static final int TRAIL = 9;
    private int trailCount;
    private float trailEmit;

    private void tickSignatures(float dt) {
        cracks.keySet().removeIf(lane -> !game.lanes.contains(lane));
        if (game.isOrbiting()) {
            trailEmit += dt;
            if (trailEmit >= 0.035f) {
                trailEmit = 0;
                System.arraycopy(trail, 0, trail, 2, trail.length - 2);
                trail[0] = game.x;
                trail[1] = game.y;
                trailCount = Math.min(TRAIL, trailCount + 1);
            }
        } else if (trailCount > 0) {
            trailEmit += dt;
            if (trailEmit >= 0.035f) {
                trailEmit = 0;
                trailCount--;
            }
        }
    }

    private float[] crack(CaptureGame.Lane lane) {
        return cracks.computeIfAbsent(lane, l -> {
            float length = (float) Math.hypot(l.bx - l.ax, l.by - l.ay);
            int steps = Math.max(2, (int) (length / 6));
            float nx = -(l.by - l.ay) / length, ny = (l.bx - l.ax) / length;
            Random jitter = new Random(System.identityHashCode(l));
            float[] points = new float[2 * (steps + 1)];
            float offset = 0;
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                // A wandering offset with small kinks, like a crack in dry ground.
                offset = i == 0 || i == steps ? 0 : Math.max(-6, Math.min(6, offset + (jitter.nextFloat() - 0.5f) * 6));
                points[2 * i] = l.ax + (l.bx - l.ax) * t + nx * offset;
                points[2 * i + 1] = l.ay + (l.by - l.ay) * t + ny * offset;
            }
            return points;
        });
    }

    /** Where the signature attacks will strike: cracks, dashed lanes, the coming wave. */
    private void drawSignatureWarnings(GuiGraphics graphics, float ox, float oy, float scale) {
        Shapes shapes = new Shapes(graphics);
        float blink = 0.6f + 0.4f * (float) Math.sin(time * 18);
        for (CaptureGame.Lane lane : game.lanes) {
            float ax = ox + lane.ax * scale, ay = oy + lane.ay * scale, bx = ox + lane.bx * scale, by = oy + lane.by * scale;
            switch (lane.kind) {
                case FISSURE -> {
                    // A dark jagged crack in the ground, which the lava runs along once it erupts.
                    float fade = lane.live() ? 1 : blink;
                    float[] crack = crack(lane);
                    polyline(shapes, crack, 0, 1, 5 * scale, (alpha(0xF0, fade) << 24) | CRACK, ox, oy, scale);
                    polyline(shapes, crack, 0, 1, 3 * scale, (alpha(0xF0, fade) << 24) | CRACK_INNER, ox, oy, scale);
                }
                case DIVE, AURORA -> {
                    if (!lane.live()) {
                        int color = lane.kind == CaptureGame.LaneKind.AURORA ? AURORA : DRAGON[3];
                        dashed(shapes, ax, ay, bx, by, 1.5f * scale, 8 * scale, (alpha(0xC0, blink) << 24) | color);
                    }
                }
                case CHAIN -> {
                    if (!lane.live()) {
                        dashed(shapes, ax, ay, bx, by, 1f * scale, 5 * scale, (alpha(0x90, blink) << 24) | ELECTRIC[3]);
                    }
                }
            }
        }
        for (CaptureGame.Wave wave : game.waves) {
            if (wave.warning <= 0) {
                continue;
            }
            // Arrows along the edge the wave comes in from, with a gap where it will be safe.
            float extent = wave.alongX ? CaptureGame.HEIGHT : CaptureGame.WIDTH;
            float edge = wave.direction > 0 ? 6 : (wave.alongX ? CaptureGame.WIDTH : CaptureGame.HEIGHT) - 6;
            for (float t = 6; t < extent; t += 14) {
                if (Math.abs(t - wave.gapCenter) < wave.gapHalf) {
                    continue;
                }
                float px = wave.alongX ? edge : t, py = wave.alongX ? t : edge;
                float dx = wave.alongX ? wave.direction * 5 : 0, dy = wave.alongX ? 0 : wave.direction * 5;
                int color = (alpha(0xE0, blink) << 24) | FOAM;
                shapes.line(ox + (px - dx - dy) * scale, oy + (py - dy - dx) * scale, ox + (px + dx) * scale,
                        oy + (py + dy) * scale, 2 * scale, color);
                shapes.line(ox + (px - dx + dy) * scale, oy + (py - dy + dx) * scale, ox + (px + dx) * scale,
                        oy + (py + dy) * scale, 2 * scale, color);
            }
        }
        for (CaptureGame.Beam beam : game.beams) {
            if (!beam.live()) {
                float angle = beam.start;
                float px = ox + game.x * scale, py = oy + game.y * scale;
                dashed(shapes, px, py, px + (float) Math.cos(angle) * 400 * scale, py + (float) Math.sin(angle) * 400 * scale,
                        1.5f * scale, 6 * scale, (alpha(0xB0, blink) << 24) | streakColor());
            }
        }
        graphics.flush();

        // Raikou's three Electric orbs mark the corners from the start.
        AttackArt.begin();
        for (CaptureGame.Lane lane : game.lanes) {
            if (lane.kind == CaptureGame.LaneKind.CHAIN) {
                int frame = (int) (time * 8) % 4;
                AttackArt.sprite(graphics, AttackArt.Element.ELECTRIC, frame, ox + lane.ax * scale, oy + lane.ay * scale,
                        16 * scale, 0, 0xFFFFFF, lane.live() ? 1 : 0.6f + 0.4f * blink);
            }
        }
    }

    private void drawSignatures(GuiGraphics graphics, float ox, float oy, float scale) {
        Shapes shapes = new Shapes(graphics);
        float shimmer = 0.75f + 0.25f * (float) Math.sin(time * 9);
        for (CaptureGame.Lane lane : game.lanes) {
            if (!lane.live()) {
                continue;
            }
            float dx = lane.bx - lane.ax, dy = lane.by - lane.ay;
            float t0 = lane.tail(), t1 = lane.front();
            float ax = ox + (lane.ax + dx * t0) * scale, ay = oy + (lane.ay + dy * t0) * scale;
            float bx = ox + (lane.ax + dx * t1) * scale, by = oy + (lane.ay + dy * t1) * scale;
            switch (lane.kind) {
                case FISSURE -> {
                    // Lava glowing up through the crack: shadow, light and a white-hot core.
                    float[] crack = crack(lane);
                    polyline(shapes, crack, t0, t1, lane.width * 0.6f * scale, 0xFF000000 | LAVA[1], ox, oy, scale);
                    polyline(shapes, crack, t0, t1, lane.width * 0.36f * scale, 0xFF000000 | LAVA[3], ox, oy, scale);
                    polyline(shapes, crack, t0, t1, lane.width * 0.12f * scale, 0xFF000000 | LAVA[4], ox, oy, scale);
                    if (random.nextFloat() < 0.5f && t1 < 1) {
                        float[] front = along(crack, t1);
                        emit(AttackArt.Element.LAVA, 0xFFFFFF, front[0], front[1], spread(30), -30 + spread(20),
                                0.45f, 14, 2);
                    }
                }
                case DIVE -> {
                    // Rayquaza: a purple-and-teal slash left behind the diving Dragon flame, fading as it goes.
                    float fade = Math.max(0.3f, 1 - t0);
                    shapes.line(ax, ay, bx, by, lane.width * 0.55f * scale, (alpha(0xA0, fade) << 24) | DRAGON[0]);
                    shapes.line(ax, ay, bx, by, lane.width * 0.35f * scale, (alpha(0xC8, fade) << 24) | DRAGON[2]);
                    float length = (float) Math.hypot(bx - ax, by - ay);
                    if (length > 1) {
                        float core = Math.min(length, 50 * scale) / length;
                        shapes.line(bx + (ax - bx) * core, by + (ay - by) * core, bx, by, 1.5f * scale, 0xFF000000 | DRAGON[4]);
                    }
                }
                case AURORA -> {
                    // Three wavy ribbons of light hanging across the arena, rippling.
                    float fade = Math.min(1, (lane.travel + lane.active + lane.timer) / 0.4f);
                    float length = (float) Math.hypot(dx, dy);
                    float nx = -dy / length, ny = dx / length;
                    int[] colors = {AURORA_PINK, AURORA, AURORA_MINT};
                    int[] alphas = {0xAA, 0xD2, 0xBE};
                    float ribbon = lane.width * 0.5f;
                    for (int k = 0; k < 3; k++) {
                        int steps = Math.max(8, (int) (length * (t1 - t0) / 5));
                        float px = 0, py = 0;
                        for (int i = 0; i <= steps; i++) {
                            float s = t0 + (t1 - t0) * i / steps;
                            float wave = (float) Math.sin(s * length * 0.07f + time * 6 + k * 1.8f) * ribbon
                                    + (k - 1) * ribbon;
                            float qx = ox + (lane.ax + dx * s + nx * wave) * scale;
                            float qy = oy + (lane.ay + dy * s + ny * wave) * scale;
                            if (i > 0) {
                                shapes.line(px, py, qx, qy, ribbon * scale, (alpha(alphas[k], fade * shimmer) << 24) | colors[k]);
                                shapes.line(px, py, qx, qy, 1 * scale, (alpha(0xC8, fade) << 24) | 0xFFFFFF);
                            }
                            px = qx;
                            py = qy;
                        }
                    }
                }
                case CHAIN -> {
                    // A thick zigzag bolt in the Electric palette, flickering every frame.
                    int seed = (int) (time * 20) + System.identityHashCode(lane);
                    jagged(shapes, ax, ay, bx, by, 6f * scale, 9 * scale, seed, 0xFF000000 | ELECTRIC[0]);
                    jagged(shapes, ax, ay, bx, by, 4f * scale, 9 * scale, seed, 0xFF000000 | ELECTRIC[2]);
                    jagged(shapes, ax, ay, bx, by, 2f * scale, 9 * scale, seed, 0xFFFFFFFF);
                }
            }
        }
        for (CaptureGame.Wave wave : game.waves) {
            if (wave.warning > 0) {
                continue;
            }
            // Kyogre: the water floods the ground behind a bright foam line and drains away in ripples.
            float extent = wave.alongX ? CaptureGame.HEIGHT : CaptureGame.WIDTH;
            float lo = wave.gapCenter - wave.gapHalf, hi = wave.gapCenter + wave.gapHalf;
            float[][] spans = {{0, lo}, {hi, extent}};
            float half = CaptureGame.Wave.BAND / 2;
            float lead = wave.pos + wave.direction * half;
            for (float[] span : spans) {
                if (span[1] <= span[0]) {
                    continue;
                }
                for (int i = 0; i < 10; i++) {
                    // The flood: solid over the wave itself, fading out further behind it.
                    float u1 = lead - wave.direction * (8 + i * 9), u2 = u1 - wave.direction * 9;
                    int a = i < 2 ? 0xA0 : Math.max(0, 0x96 - i * 15);
                    waveRect(shapes, wave, u1, u2, span[0], span[1], a << 24 | WATER_PAL[2], ox, oy, scale);
                }
                for (int i = 1; i < 6; i++) {
                    float u = lead - wave.direction * (8 + i * 17);
                    int a = 0xB4 - i * 25;
                    for (float v = span[0] + (i * 5) % 9; v + 4 < span[1]; v += 9) {
                        waveRect(shapes, wave, u, u + 1, v, v + 4, a << 24 | WATER_PAL[3], ox, oy, scale);
                    }
                }
                waveRect(shapes, wave, lead, lead - wave.direction * 8, span[0], span[1], 0xFF000000 | WATER_PAL[3], ox, oy, scale);
                waveRect(shapes, wave, lead - wave.direction * 2, lead + wave.direction * 2, span[0], span[1], 0xFFFFFFFF,
                        ox, oy, scale);
                waveRect(shapes, wave, lead + wave.direction * 2, lead + wave.direction * 3, span[0], span[1],
                        0xFF000000 | WATER_PAL[0], ox, oy, scale);
            }
        }
        for (CaptureGame.Beam beam : game.beams) {
            if (!beam.live()) {
                continue;
            }
            // Layered like the attack sprites: outline, shadow, colour, light and a white-hot core.
            float angle = beam.angle();
            float px = ox + game.x * scale, py = oy + game.y * scale;
            float ex = px + (float) Math.cos(angle) * 400 * scale, ey = py + (float) Math.sin(angle) * 400 * scale;
            int color = art.color() & 0xFFFFFF;
            shapes.line(px, py, ex, ey, 14 * scale, 0xFF000000 | OUTLINE);
            shapes.line(px, py, ex, ey, 12 * scale, 0xFF000000 | darken(color));
            shapes.line(px, py, ex, ey, 8 * scale, 0xFF000000 | color);
            shapes.line(px, py, ex, ey, 4 * scale, 0xFF000000 | streakColor());
            shapes.line(px, py, ex, ey, 2 * scale, 0xFFFFFFFF);
        }
        for (CaptureGame.Orbiter orbiter : game.orbiters) {
            // A thin blade of white light with a soft pink glow.
            float cx = ox + game.orbiterX(orbiter) * scale, cy = oy + game.orbiterY(orbiter) * scale;
            float fade = Math.min(1, orbiter.life / 0.4f);
            float facing = orbiter.angle + (float) Math.PI / 2;
            shapes.arc(cx, cy, 7 * scale, 5 * scale, facing - 1.4f, 2.8f, (alpha(0x8C, fade) << 24) | CRESCENT);
            shapes.arc(cx, cy, 6.5f * scale, 1.6f * scale, facing - 1.2f, 2.4f, (alpha(0xFF, fade) << 24) | 0xFFFFFF);
        }
        for (CaptureGame.Projectile p : game.projectiles) {
            if (p.style == CaptureGame.AttackStyle.MIST_BALLS) {
                // A swirl spinning round each Mist Ball.
                float start = time * 12 + System.identityHashCode(p) % 7;
                shapes.arc(ox + p.x * scale, oy + p.y * scale, p.radius * 2 * scale, 2 * scale, start, 2.1f,
                        0xE0000000 | PSYCHIC[3]);
            }
        }
        for (CaptureGame.Hazard hazard : game.hazards) {
            if (hazard.style == CaptureGame.AttackStyle.RAIN && !hazard.exploding()
                    && rainKind() == AttackArt.Element.ELECTRIC) {
                // Zapdos: a short bolt striking down onto the marker.
                float hx = ox + hazard.x * scale, hy = oy + (hazard.y - fallHeight(hazard)) * scale;
                int seed = (int) (time * 20) + System.identityHashCode(hazard);
                jagged(shapes, hx, hy - 30 * scale, hx, hy + 2 * scale, 4 * scale, 6 * scale, seed, 0xFF000000 | ELECTRIC[0]);
                jagged(shapes, hx, hy - 30 * scale, hx, hy + 2 * scale, 2 * scale, 6 * scale, seed, 0xFFFFFFFF);
            } else if (hazard.style == CaptureGame.AttackStyle.RAIN && !hazard.exploding()
                    && rainKind() == AttackArt.Element.FIRE) {
                // Moltres: a streak of flame above each falling fireball.
                float hx = ox + hazard.x * scale, hy = oy + (hazard.y - fallHeight(hazard)) * scale;
                shapes.line(hx, hy - 18 * scale, hx, hy, 3 * scale, 0xA0FFC23E);
            }
        }
        if (trailCount > 1) {
            // Latios and Latias: rings left in the wake of their laps, shrinking and fading.
            for (int i = trailCount - 1; i >= 1; i--) {
                float r = game.radius * (1.6f - i * 0.11f);
                shapes.ring(ox + trail[2 * i] * scale, oy + trail[2 * i + 1] * scale, r * scale, 2 * scale,
                        (alpha(0xFF - i * 26, 1) << 24) | AFTERIMAGE);
            }
        }
        float barrier = game.barrierRadius();
        if (barrier > 0) {
            // Mewtwo: two outlined rings with glowing dots running between them, like a Ranger sign circle.
            float fade = game.barrierFade();
            float px = ox + game.x * scale, py = oy + game.y * scale;
            boolean up = game.isBarrierUp();
            float strength = up ? 1 : 0.5f;
            shapes.disc(px, py, barrier * scale, (alpha(0x30, fade * strength) << 24) | PSYCHIC[3]);
            shapes.ring(px, py, barrier * scale, 6 * scale, (alpha(0xFF, fade * strength) << 24) | PSYCHIC[0]);
            shapes.ring(px, py, barrier * scale, 4 * scale, (alpha(0xFF, fade * strength * shimmer) << 24) | PSYCHIC[2]);
            shapes.ring(px, py, (barrier - 7) * scale, 4 * scale, (alpha(0xFF, fade * strength) << 24) | PSYCHIC[0]);
            shapes.ring(px, py, (barrier - 7) * scale, 2 * scale, (alpha(0xFF, fade * strength) << 24) | PSYCHIC[2]);
            for (int i = 0; i < 8; i++) {
                float a = (float) (Math.PI / 4 * i) - time * 3;
                float r = (barrier - 3.5f) * scale;
                shapes.disc(px + (float) Math.cos(a) * r, py + (float) Math.sin(a) * r, 2 * scale,
                        (alpha(0xFF, fade) << 24) | PSYCHIC[4]);
            }
        }
        if (game.isWindy()) {
            // Lugia: long dithered streaks drifting with the wind, wrapped around the arena.
            float wx = game.windX(), wy = game.windY();
            float len = (float) Math.hypot(wx, wy);
            float ux = wx / len, uy = wy / len;
            float dot = Math.max(1, scale);
            for (int i = 0; i < 16; i++) {
                float sx = ((i * 83.3f + time * wx * 2.6f) % CaptureGame.WIDTH + CaptureGame.WIDTH) % CaptureGame.WIDTH;
                float sy = ((i * 29.7f + time * wy * 2.6f) % CaptureGame.HEIGHT + CaptureGame.HEIGHT) % CaptureGame.HEIGHT;
                for (int k = 0; k < 20; k++) {
                    if ((k + i) % 3 == 0) {
                        continue;
                    }
                    float qx = ox + (sx + ux * k * 2) * scale, qy = oy + (sy + uy * k * 2) * scale;
                    shapes.rect(qx, qy, qx + dot, qy + dot, 0xFF000000 | (k > 10 ? FLYING[3] : FLYING[2]));
                }
            }
        }
        if (game.rewindFx >= 0) {
            // Celebi: a clock face where it lands, its hand spinning backwards.
            float fade = 1 - game.rewindFx / CaptureGame.REWIND_FX_TIME;
            float fx = ox + game.rewindFromX * scale, fy = oy + game.rewindFromY * scale;
            float tx = ox + game.x * scale, ty = oy + game.y * scale;
            float r = game.radius * 1.6f;
            int light = (alpha(0xF0, fade) << 24) | GRASS[3], dark = (alpha(0xF0, fade) << 24) | GRASS[0];
            shapes.ring(fx, fy, game.radius * scale * (1 + game.rewindFx * 1.5f), 2 * scale, light);
            dashed(shapes, fx, fy, tx, ty, 2 * scale, 6 * scale, light);
            shapes.ring(tx, ty, r * scale, 4 * scale, dark);
            shapes.ring(tx, ty, (r - 0.5f) * scale, 2 * scale, light);
            for (int i = 0; i < 12; i++) {
                float a = (float) (Math.PI / 6 * i);
                float d = (r - 4) * scale;
                shapes.rect(tx + (float) Math.cos(a) * d - 0.5f * scale, ty + (float) Math.sin(a) * d - 0.5f * scale,
                        tx + (float) Math.cos(a) * d + 0.5f * scale, ty + (float) Math.sin(a) * d + 0.5f * scale,
                        (alpha(0xFF, fade) << 24) | GRASS[4]);
            }
            float hand = -game.rewindFx * 18;
            float hx = tx + (float) Math.cos(hand) * (r - 5) * scale, hy = ty + (float) Math.sin(hand) * (r - 5) * scale;
            shapes.line(tx, ty, hx, hy, 3 * scale, dark);
            shapes.line(tx, ty, hx, hy, 1 * scale, (alpha(0xFF, fade) << 24) | GRASS[4]);
        }
        graphics.flush();

        AttackArt.begin();
        for (CaptureGame.Lane lane : game.lanes) {
            if (lane.kind == CaptureGame.LaneKind.FISSURE && lane.live() && lane.front() < 1) {
                // Fire bursts riding the front of the eruption.
                float[] front = along(crack(lane), lane.front());
                AttackArt.blast(graphics, AttackArt.Blast.FIRE, 3 + (int) (time * 12) % 3, ox + front[0] * scale,
                        oy + (front[1] - 6) * scale, 29 * scale, 0xFFFFFF, 1);
            }
        }
        for (CaptureGame.Lane lane : game.lanes) {
            if (lane.kind == CaptureGame.LaneKind.DIVE && lane.live() && lane.front() < 1) {
                // The Dragon projectile's flame at double size, diving along the lane.
                float t = lane.front();
                AttackArt.sprite(graphics, AttackArt.Element.DRAGON, (int) (time * 12) % 4,
                        ox + (lane.ax + (lane.bx - lane.ax) * t) * scale, oy + (lane.ay + (lane.by - lane.ay) * t) * scale,
                        32 * scale, (float) Math.atan2(lane.by - lane.ay, lane.bx - lane.ax), 0xFFFFFF, 1);
            }
        }
        for (CaptureGame.Beam beam : game.beams) {
            if (beam.live()) {
                float angle = beam.angle();
                AttackArt.signature(graphics, AttackArt.SIGNATURE_BURST, ox + (game.x + (float) Math.cos(angle) * 10) * scale,
                        oy + (game.y + (float) Math.sin(angle) * 10) * scale, 22 * scale * (0.9f + 0.1f * shimmer),
                        time * 4, streakColor(), 1);
            }
        }
    }

    /** The blast row for a signature attack's marker or burst. */
    private AttackArt.Blast signatureBlast(CaptureGame.Hazard hazard, boolean warning) {
        if (hazard.style == CaptureGame.AttackStyle.PILLARS || (!warning && rainKind() == AttackArt.Element.FIRE)) {
            return AttackArt.Blast.FIRE;
        }
        return AttackArt.Blast.STEAM;
    }

    /** A rectangle in a wave's own terms: u runs the way it travels, v along its front. */
    private static void waveRect(Shapes shapes, CaptureGame.Wave wave, float u1, float u2, float v1, float v2, int color,
                                 float ox, float oy, float scale) {
        float a = Math.min(u1, u2), b = Math.max(u1, u2);
        if (wave.alongX) {
            shapes.rect(ox + a * scale, oy + v1 * scale, ox + b * scale, oy + v2 * scale, color);
        } else {
            shapes.rect(ox + v1 * scale, oy + a * scale, ox + v2 * scale, oy + b * scale, color);
        }
    }

    /** Which bird's storm falls: Moltres's fireballs, Zapdos's bolts or Articuno's icicles. */
    private AttackArt.Element rainKind() {
        return switch (art.missile()) {
            case FIRE, LAVA, STEAM -> AttackArt.Element.FIRE;
            case ELECTRIC -> AttackArt.Element.ELECTRIC;
            case ICE, WATER, BUBBLE -> AttackArt.Element.ICE;
            default -> art.missile();
        };
    }

    /** How high above its marker a falling drop of the storm still is. */
    private static float fallHeight(CaptureGame.Hazard hazard) {
        return Math.max(0, hazard.timer / hazard.warning) * 90;
    }

    /** Part of a polyline of arena points, from fraction t0 to t1 of its length, with rounded joints. */
    private static void polyline(Shapes shapes, float[] points, float t0, float t1, float thickness, int color, float ox,
                                 float oy, float scale) {
        if (t1 <= t0) {
            return;
        }
        int n = points.length / 2;
        float total = 0;
        for (int i = 1; i < n; i++) {
            total += (float) Math.hypot(points[2 * i] - points[2 * i - 2], points[2 * i + 1] - points[2 * i - 1]);
        }
        float from = t0 * total, to = t1 * total, walked = 0;
        for (int i = 1; i < n; i++) {
            float x0 = points[2 * i - 2], y0 = points[2 * i - 1], x1 = points[2 * i], y1 = points[2 * i + 1];
            float seg = (float) Math.hypot(x1 - x0, y1 - y0);
            float a = Math.max(from, walked), b = Math.min(to, walked + seg);
            if (b > a && seg > 0) {
                float ka = (a - walked) / seg, kb = (b - walked) / seg;
                float sx = ox + (x0 + (x1 - x0) * ka) * scale, sy = oy + (y0 + (y1 - y0) * ka) * scale;
                float ex = ox + (x0 + (x1 - x0) * kb) * scale, ey = oy + (y0 + (y1 - y0) * kb) * scale;
                shapes.line(sx, sy, ex, ey, thickness, color);
                if (b < to) {
                    shapes.disc(ex, ey, thickness / 2, color);
                }
            }
            walked += seg;
        }
    }

    /** The point a fraction t along a polyline of arena points. */
    private static float[] along(float[] points, float t) {
        int n = points.length / 2;
        float total = 0;
        for (int i = 1; i < n; i++) {
            total += (float) Math.hypot(points[2 * i] - points[2 * i - 2], points[2 * i + 1] - points[2 * i - 1]);
        }
        float goal = Math.max(0, Math.min(1, t)) * total;
        for (int i = 1; i < n; i++) {
            float x0 = points[2 * i - 2], y0 = points[2 * i - 1], x1 = points[2 * i], y1 = points[2 * i + 1];
            float seg = (float) Math.hypot(x1 - x0, y1 - y0);
            if (goal <= seg || i == n - 1) {
                float k = seg == 0 ? 0 : Math.min(1, goal / seg);
                return new float[]{x0 + (x1 - x0) * k, y0 + (y1 - y0) * k};
            }
            goal -= seg;
        }
        return new float[]{points[0], points[1]};
    }

    private static int darken(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (r * 3 / 5) << 16 | (g * 3 / 5) << 8 | b * 3 / 5;
    }

    private static int alpha(int alpha, float fade) {
        return Math.max(0, Math.min(255, (int) (alpha * fade)));
    }

    private static void dashed(Shapes shapes, float ax, float ay, float bx, float by, float thickness, float dash, int color) {
        float length = (float) Math.hypot(bx - ax, by - ay);
        if (length < 1e-3f) {
            return;
        }
        float ux = (bx - ax) / length, uy = (by - ay) / length;
        for (float t = 0; t < length; t += dash * 2) {
            float end = Math.min(length, t + dash);
            shapes.line(ax + ux * t, ay + uy * t, ax + ux * end, ay + uy * end, thickness, color);
        }
    }

    /** A zigzag from a to b: a crack in the ground, or a bolt of lightning when the seed changes every frame. */
    private static void jagged(Shapes shapes, float ax, float ay, float bx, float by, float thickness, float amplitude,
                               int seed, int color) {
        float length = (float) Math.hypot(bx - ax, by - ay);
        if (length < 1e-3f) {
            return;
        }
        int steps = Math.max(2, (int) (length / (amplitude * 1.6f)));
        float nx = -(by - ay) / length, ny = (bx - ax) / length;
        Random jitter = new Random(seed);
        float px = ax, py = ay;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float offset = i == steps ? 0 : (jitter.nextFloat() - 0.5f) * amplitude;
            float qx = ax + (bx - ax) * t + nx * offset, qy = ay + (by - ay) * t + ny * offset;
            shapes.line(px, py, qx, qy, thickness, color);
            px = qx;
            py = qy;
        }
    }

    /** The type's colour lifted towards white so the streaks show on any ground. */
    private int streakColor() {
        int color = art.color();
        int r = ((color >> 16) & 0xFF) + 255 >> 1, g = ((color >> 8) & 0xFF) + 255 >> 1, b = (color & 0xFF) + 255 >> 1;
        return (r << 16) | (g << 8) | b;
    }

    private int blastTint() {
        return art.blast() == AttackArt.Blast.GENERIC ? art.color() : 0xFFFFFF;
    }
}
