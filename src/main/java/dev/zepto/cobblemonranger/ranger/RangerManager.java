package dev.zepto.cobblemonranger.ranger;

import dev.zepto.cobblemonranger.mission.Mission;
import dev.zepto.cobblemonranger.network.RangerSyncPayload;
import dev.zepto.cobblemonranger.registry.ModAttachments;
import dev.zepto.cobblemonranger.signs.RangerSign;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Reads and writes a player's {@link RangerData} and keeps the client's copy (for the HUD and sign screen) in sync. */
public final class RangerManager {
    private RangerManager() {
    }

    public static RangerData get(ServerPlayer player) {
        return player.getData(ModAttachments.RANGER);
    }

    public static void update(ServerPlayer player, UnaryOperator<RangerData> change) {
        player.setData(ModAttachments.RANGER, change.apply(get(player)));
        sync(player);
    }

    public static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    public static void sync(ServerPlayer player) {
        RangerData data = get(player);
        Component mission = data.mission().map(RangerManager::missionLine).orElse(Component.empty());
        Map<String, Integer> cooldowns = new HashMap<>();
        long now = now(player);
        for (RangerSign sign : RangerSign.values()) {
            long readyAt = data.signCooldowns().getOrDefault(sign.id(), 0L);
            if (readyAt > now) {
                cooldowns.put(sign.id(), (int) (readyAt - now));
            }
        }
        PacketDistributor.sendToPlayer(player, new RangerSyncPayload(data.rank(), data.points(),
                RangerData.pointsForNextRank(data.rank()), mission, cooldowns));
    }

    public static Component missionLine(Mission mission) {
        if (mission.type() == Mission.Type.DELIVER) {
            return mission.description();
        }
        return Component.translatable("hud.cobblemonranger.mission_progress", mission.description(),
                mission.progress(), mission.required());
    }

    public static Component rankTitle(int rank) {
        return Component.translatable("rank.cobblemonranger." + rank);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
