package dev.zepto.cobblemonranger.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A field move's glow particle: plays its 4 frames over its life while drifting and slowing down. */
public class FieldMoveParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected FieldMoveParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                                SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.xd = dx + (random.nextDouble() - 0.5) * 0.02;
        this.yd = dy + 0.01;
        this.zd = dz + (random.nextDouble() - 0.5) * 0.02;
        this.friction = 0.9f;
        this.gravity = 0;
        this.quadSize = 0.12f + random.nextFloat() * 0.06f;
        this.lifetime = 14 + random.nextInt(10);
        this.hasPhysics = false;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setSpriteFromAge(sprites);
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0; // glows at full brightness, even in caves
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** The rescue alert beam: bigger and slower, so it can be seen from 40 blocks away. */
    public static class BeamProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public BeamProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            FieldMoveParticle particle = new FieldMoveParticle(level, x, y, z, dx, dy, dz, sprites);
            particle.quadSize = 0.35f + particle.random.nextFloat() * 0.15f;
            particle.lifetime = 24 + particle.random.nextInt(12);
            particle.yd = 0.04;
            return particle;
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new FieldMoveParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
