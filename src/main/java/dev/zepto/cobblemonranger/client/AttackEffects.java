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
