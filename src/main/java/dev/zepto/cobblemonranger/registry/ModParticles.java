package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import dev.zepto.cobblemonranger.field.FieldMove;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** One glow particle per field move, drawn by tools/field_move_art.py (4 frames each). */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, CobblemonRanger.MOD_ID);

    public static final Map<FieldMove, DeferredHolder<ParticleType<?>, SimpleParticleType>> FIELD_MOVES =
            new EnumMap<>(FieldMove.class);

    /** The column of sparkles over a trapped Pokémon in a rescue event. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RESCUE_BEAM = PARTICLES.register(
            "rescue_beam", () -> new SimpleParticleType(true));

    static {
        for (FieldMove move : FieldMove.values()) {
            FIELD_MOVES.put(move, PARTICLES.register(move.id(), () -> new SimpleParticleType(false)));
        }
    }

    private ModParticles() {
    }
}
