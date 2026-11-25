package dev.zepto.cobblemonranger.field;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector3f;

/** The particle each field move shows. */
public final class FieldMoveParticles {
    private static final DustParticleOptions PSYCHIC = new DustParticleOptions(new Vector3f(0.98f, 0.33f, 0.53f), 1f);

    private FieldMoveParticles() {
    }

    public static ParticleOptions flash() {
        return ParticleTypes.END_ROD;
    }

    public static ParticleOptions tailwind() {
        return ParticleTypes.CLOUD;
    }

    public static ParticleOptions spook() {
        return ParticleTypes.SOUL;
    }

    public static ParticleOptions charm() {
        return ParticleTypes.HEART;
    }

    public static ParticleOptions magnet() {
        return ParticleTypes.ELECTRIC_SPARK;
    }

    public static ParticleOptions decompose() {
        return ParticleTypes.WITCH;
    }

    public static ParticleOptions stringShot() {
        return ParticleTypes.WHITE_ASH;
    }

    public static ParticleOptions telekinesis() {
        return PSYCHIC;
    }
}
