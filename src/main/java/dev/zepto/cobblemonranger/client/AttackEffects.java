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
            if (art.blast() == AttackArt.Blast.ROCKFALL) {
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
            AttackArt.blast(graphics, art.blast(), frame, ox + hazard.x * scale, oy + hazard.y * scale,
                    hazard.radius * 2.4f * scale * 32 / 30f, blastTint(), 1);
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

    private static final int LAVA = 0xFF7A1A, LAVA_HOT = 0xFFE066, AURORA = 0x7FF0FF, AURORA_PINK = 0xE0A8FF;
    private static final int THUNDER = 0xFFE040, WATER = 0x3A7FE0, FOAM = 0xE8F6FF, CRESCENT = 0xF2A8FF;
    private static final int PSYCHIC = 0xB070FF;

    /** Where the signature attacks will strike: cracks, dashed lanes, the coming wave. */
    private void drawSignatureWarnings(GuiGraphics graphics, float ox, float oy, float scale) {
        Shapes shapes = new Shapes(graphics);
        float blink = 0.6f + 0.4f * (float) Math.sin(time * 18);
        for (CaptureGame.Lane lane : game.lanes) {
            float ax = ox + lane.ax * scale, ay = oy + lane.ay * scale, bx = ox + lane.bx * scale, by = oy + lane.by * scale;
            switch (lane.kind) {
                case FISSURE -> {
                    // A jagged crack in the ground, glowing once it erupts.
                    int crack = lane.live() ? 0xE0301008 : alpha(0xC0, blink) << 24 | 0x301008;
                    jagged(shapes, ax, ay, bx, by, 3f * scale, (lane.width * 0.5f) * scale, System.identityHashCode(lane),
                            crack);
                }
                case DIVE, AURORA -> {
                    if (!lane.live()) {
                        int color = lane.kind == CaptureGame.LaneKind.AURORA ? AURORA : streakColor();
                        dashed(shapes, ax, ay, bx, by, 1.5f * scale, 8 * scale, (alpha(0xC0, blink) << 24) | color);
                    }
                }
                case CHAIN -> {
                    if (!lane.live()) {
                        dashed(shapes, ax, ay, bx, by, 1f * scale, 5 * scale, (alpha(0x90, blink) << 24) | THUNDER);
                        shapes.disc(ax, ay, 4 * scale, (alpha(0xFF, blink) << 24) | THUNDER);
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
                    shapes.line(ax, ay, bx, by, lane.width * scale, 0xC0000000 | LAVA);
                    shapes.line(ax, ay, bx, by, lane.width * 0.45f * scale, 0xFF000000 | LAVA_HOT);
                    // Lava spurting at the front of the eruption.
                    if (random.nextFloat() < 0.6f && t1 < 1) {
                        emit(art.debris(), 0xFFFFFF, lane.ax + dx * t1, lane.ay + dy * t1, spread(40), -30 + spread(25),
                                0.45f, 14, 2);
                    }
                }
                case DIVE -> {
                    // A streak that fades towards its tail, brightest at the diving head.
                    for (int k = 0; k < 4; k++) {
                        float s0 = k / 4f, s1 = (k + 1) / 4f;
                        float x0 = ax + (bx - ax) * s0, y0 = ay + (by - ay) * s0;
                        float x1 = ax + (bx - ax) * s1, y1 = ay + (by - ay) * s1;
                        int a = 0x40 + 0x30 * k;
                        shapes.line(x0, y0, x1, y1, lane.width * (0.6f + 0.15f * k) * scale, a << 24 | (art.color() & 0xFFFFFF));
                        shapes.line(x0, y0, x1, y1, lane.width * 0.3f * scale, (a + 0x30) << 24 | 0xFFFFFF);
                    }
                    shapes.disc(bx, by, lane.width * 0.7f * scale, 0xF0FFFFFF);
                }
                case AURORA -> {
                    float fade = Math.min(1, (lane.travel + lane.active + lane.timer) / 0.4f);
                    shapes.line(ax, ay, bx, by, lane.width * 1.6f * scale, (alpha(0x50, fade * shimmer) << 24) | AURORA_PINK);
                    shapes.line(ax, ay, bx, by, lane.width * scale, (alpha(0xB0, fade * shimmer) << 24) | AURORA);
                    shapes.line(ax, ay, bx, by, lane.width * 0.3f * scale, (alpha(0xE0, fade) << 24) | 0xFFFFFF);
                }
                case CHAIN -> {
                    int seed = (int) (time * 20) + System.identityHashCode(lane);
                    jagged(shapes, ax, ay, bx, by, 3f * scale, 7 * scale, seed, 0xC0000000 | THUNDER);
                    jagged(shapes, ax, ay, bx, by, 1.2f * scale, 7 * scale, seed, 0xF0FFFFFF);
                    shapes.disc(ax, ay, 4 * scale, 0xFF000000 | THUNDER);
                }
            }
        }
        for (CaptureGame.Wave wave : game.waves) {
            if (wave.warning > 0) {
                continue;
            }
            float extent = wave.alongX ? CaptureGame.HEIGHT : CaptureGame.WIDTH;
            float lo = wave.gapCenter - wave.gapHalf, hi = wave.gapCenter + wave.gapHalf;
            float[][] spans = {{0, lo}, {hi, extent}};
            float half = CaptureGame.Wave.BAND / 2;
            for (float[] span : spans) {
                if (span[1] <= span[0]) {
                    continue;
                }
                float x1, y1, x2, y2;
                if (wave.alongX) {
                    x1 = wave.pos - half;
                    x2 = wave.pos + half;
                    y1 = span[0];
                    y2 = span[1];
                } else {
                    x1 = span[0];
                    x2 = span[1];
                    y1 = wave.pos - half;
                    y2 = wave.pos + half;
                }
                shapes.rect(ox + x1 * scale, oy + y1 * scale, ox + x2 * scale, oy + y2 * scale, 0xC0000000 | WATER);
                // A lighter crest on the front half of the wave.
                float c1 = wave.alongX ? (wave.direction > 0 ? wave.pos : x1) : x1;
                float c2 = wave.alongX ? (wave.direction > 0 ? x2 : wave.pos) : x2;
                float d1 = wave.alongX ? y1 : (wave.direction > 0 ? wave.pos : y1);
                float d2 = wave.alongX ? y2 : (wave.direction > 0 ? y2 : wave.pos);
                shapes.rect(ox + c1 * scale, oy + d1 * scale, ox + c2 * scale, oy + d2 * scale, 0x60000000 | 0x8CC8FF);
                // Foam along the leading edge.
                float lead = wave.pos + wave.direction * half;
                if (wave.alongX) {
                    shapes.rect(ox + (lead - 2) * scale, oy + y1 * scale, ox + (lead + 2) * scale, oy + y2 * scale,
                            0xF0000000 | FOAM);
                } else {
                    shapes.rect(ox + x1 * scale, oy + (lead - 2) * scale, ox + x2 * scale, oy + (lead + 2) * scale,
                            0xF0000000 | FOAM);
                }
            }
        }
        for (CaptureGame.Beam beam : game.beams) {
            if (!beam.live()) {
                continue;
            }
            float angle = beam.angle();
            float px = ox + game.x * scale, py = oy + game.y * scale;
            float ex = px + (float) Math.cos(angle) * 400 * scale, ey = py + (float) Math.sin(angle) * 400 * scale;
            shapes.line(px, py, ex, ey, 12 * scale * shimmer, 0x70000000 | (art.color() & 0xFFFFFF));
            shapes.line(px, py, ex, ey, 6 * scale, 0xD0000000 | streakColor());
            shapes.line(px, py, ex, ey, 2.5f * scale, 0xFFFFFFFF);
            shapes.disc(px, py, 9 * scale * shimmer, 0xB0FFFFFF);
        }
        for (CaptureGame.Orbiter orbiter : game.orbiters) {
            float cx = ox + game.orbiterX(orbiter) * scale, cy = oy + game.orbiterY(orbiter) * scale;
            float fade = Math.min(1, orbiter.life / 0.4f);
            float facing = orbiter.angle + (float) Math.PI / 2;
            shapes.arc(cx, cy, 6 * scale, 3.5f * scale, facing - 1.4f, 2.8f, (alpha(0xE0, fade) << 24) | CRESCENT);
            shapes.arc(cx, cy, 6.5f * scale, 1.2f * scale, facing - 1.1f, 2.2f, (alpha(0xFF, fade) << 24) | 0xFFFFFF);
        }
        float barrier = game.barrierRadius();
        if (barrier > 0) {
            float fade = game.barrierFade();
            float px = ox + game.x * scale, py = oy + game.y * scale;
            boolean up = game.isBarrierUp();
            shapes.disc(px, py, barrier * scale, (alpha(up ? 0x30 : 0x14, fade) << 24) | PSYCHIC);
            shapes.ring(px, py, barrier * scale, (up ? 2.5f : 1.2f) * scale, (alpha(0xD0, fade * shimmer) << 24) | PSYCHIC);
            // Six panels spinning round the barrier's edge.
            for (int i = 0; i < 6; i++) {
                float a = time * 1.5f + (float) (Math.PI * 2 * i / 6);
                shapes.arc(px, py, (barrier - 3) * scale, 1.5f * scale, a, 0.6f, (alpha(0xC0, fade) << 24) | 0xFFFFFF);
            }
        }
        if (game.isWindy()) {
            float wx = game.windX(), wy = game.windY();
            float len = (float) Math.hypot(wx, wy);
            float ux = wx / len, uy = wy / len;
            for (int i = 0; i < 14; i++) {
                // Streaks drifting with the wind, wrapped around the arena.
                float sx = ((i * 73.3f + time * wx * 2.2f) % CaptureGame.WIDTH + CaptureGame.WIDTH) % CaptureGame.WIDTH;
                float sy = ((i * 41.7f + time * wy * 2.2f) % CaptureGame.HEIGHT + CaptureGame.HEIGHT) % CaptureGame.HEIGHT;
                shapes.line(ox + sx * scale, oy + sy * scale, ox + (sx + ux * 22) * scale, oy + (sy + uy * 22) * scale,
                        1.5f * scale, 0x90FFFFFF);
            }
        }
        if (game.rewindFx >= 0) {
            float fade = 1 - game.rewindFx / CaptureGame.REWIND_FX_TIME;
            float fx = ox + game.rewindFromX * scale, fy = oy + game.rewindFromY * scale;
            float tx = ox + game.x * scale, ty = oy + game.y * scale;
            int color = (alpha(0xF0, fade) << 24) | 0xB8FF8A;
            shapes.disc(tx, ty, game.radius * 1.4f * scale, (alpha(0x50, fade) << 24) | 0xB8FF8A);
            shapes.ring(fx, fy, game.radius * scale * (1 + game.rewindFx * 1.5f), 3 * scale, color);
            dashed(shapes, fx, fy, tx, ty, 3 * scale, 6 * scale, color);
            // A clock face where it lands, its hand turning back.
            shapes.ring(tx, ty, game.radius * 1.4f * scale, 1.5f * scale, color);
            float hand = -game.rewindFx * 12;
            shapes.line(tx, ty, tx + (float) Math.cos(hand) * game.radius * scale, ty + (float) Math.sin(hand) * game.radius * scale,
                    1.5f * scale, color);
        }
        graphics.flush();
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
