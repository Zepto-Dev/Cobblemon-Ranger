package dev.zepto.cobblemonranger.ranger;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Puts every sent-out party Pokémon away before the server shuts down.
 *
 * <p>While stopping, Minecraft unloads chunks before it saves the world. Cobblemon removes an owned Pokémon the moment
 * its owner stops seeing it, and during that unload this happens while Minecraft is still looping over the chunk's
 * entities. The loop then fails, and the world is never saved. The Partner Pokémon is almost always out, so this
 * mod made it easy to hit. Removing the Pokémon here, before any chunk unloads, avoids it: Cobblemon does the same
 * removal at logout.
 */
public final class ShutdownSafety {
    private ShutdownSafety() {
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                // Through Entity: only vanilla methods, so the call survives Cobblemon updates.
                Entity entity = pokemon.getEntity();
                if (entity != null && !entity.isRemoved()) {
                    entity.remove(Entity.RemovalReason.DISCARDED);
                }
            }
        }
    }
}
