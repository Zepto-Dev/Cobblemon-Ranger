package dev.zepto.cobblemonranger.field;

import dev.zepto.cobblemonranger.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;

/** The glow particle each field move shows. */
public final class FieldMoveParticles {
    private FieldMoveParticles() {
    }

    public static ParticleOptions of(FieldMove move) {
        return ModParticles.FIELD_MOVES.get(move).get();
    }

    public static ParticleOptions flash() {
        return of(FieldMove.FLASH);
    }

    public static ParticleOptions tailwind() {
        return of(FieldMove.TAILWIND);
    }

    public static ParticleOptions spook() {
        return of(FieldMove.SPOOK);
    }

    public static ParticleOptions charm() {
        return of(FieldMove.CHARM);
    }

    public static ParticleOptions magnet() {
        return of(FieldMove.MAGNET);
    }

    public static ParticleOptions decompose() {
        return of(FieldMove.DECOMPOSE);
    }

    public static ParticleOptions stringShot() {
        return of(FieldMove.STRING_SHOT);
    }

    public static ParticleOptions telekinesis() {
        return of(FieldMove.TELEKINESIS);
    }
}
