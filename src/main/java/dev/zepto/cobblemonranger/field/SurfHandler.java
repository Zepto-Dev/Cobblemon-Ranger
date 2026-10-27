package dev.zepto.cobblemonranger.field;

import dev.zepto.cobblemonranger.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * While Surfing, a player in water rides along the surface in the direction they are looking, faster than any boat.
 * Player movement is client-authoritative, so this only runs for the local player on the client.
 */
public final class SurfHandler {
    private static final double SPEED = 0.42;
    private static final double SPRINT_SPEED = 0.62;

    private SurfHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() || !player.isControlledByLocalInstance()
                || !player.hasEffect(ModEffects.SURFING) || !player.isInWater() || player.isPassenger()
                || player.getAbilities().flying) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        double x = motion.x * 0.8;
        double z = motion.z * 0.8;
        if (player.zza > 0 && look.lengthSqr() > 1e-4) {
            look = look.normalize().scale(player.isSprinting() ? SPRINT_SPEED : SPEED);
            x = look.x;
            z = look.z;
        }
        // Float with the head above water; sneaking dives.
        double y = player.isShiftKeyDown() ? Math.min(motion.y, -0.1)
                : player.isEyeInFluid(FluidTags.WATER) ? 0.12 : Math.max(motion.y, 0.0);
        player.setDeltaMovement(x, y, z);
        player.fallDistance = 0;
        if (player.zza > 0 && player.tickCount % 2 == 0) {
            player.level().addParticle(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.2, player.getZ(),
                    -x, 0.1, -z);
        }
    }
}
