package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, CobblemonRanger.MOD_ID);

    /** Granted by the Surf field move: a friend Pokémon carries the player across water (see SurfHandler). */
    public static final DeferredHolder<MobEffect, MobEffect> SURFING = EFFECTS.register("surfing",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x3FA9F5) {
            });

    private ModEffects() {
    }
}
