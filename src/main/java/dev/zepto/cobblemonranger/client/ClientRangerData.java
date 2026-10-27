package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.network.RangerSyncPayload;
import dev.zepto.cobblemonranger.signs.RangerSign;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/** The local player's Ranger rank, mission and sign cooldowns, as last sent by the server. */
public final class ClientRangerData {
    private static int rank = 1;
    private static int points;
    private static int nextRankPoints = -1;
    private static Component mission = Component.empty();
    /** Sign id to the {@link System#currentTimeMillis()} at which it is ready again. */
    private static final Map<String, Long> READY_AT = new HashMap<>();

    private ClientRangerData() {
    }

    static void update(RangerSyncPayload payload) {
        rank = payload.rank();
        points = payload.points();
        nextRankPoints = payload.nextRankPoints();
        mission = payload.mission();
        READY_AT.clear();
        long now = System.currentTimeMillis();
        payload.signCooldowns().forEach((sign, ticks) -> READY_AT.put(sign, now + ticks * 50L));
    }

    public static int rank() {
        return rank;
    }

    public static int points() {
        return points;
    }

    public static int nextRankPoints() {
        return nextRankPoints;
    }

    public static Component mission() {
        return mission;
    }

    public static boolean isUnlocked(RangerSign sign) {
        return rank >= sign.rank();
    }

    /** Seconds until the sign can be used again, or 0 if it is ready. */
    public static int cooldownSeconds(RangerSign sign) {
        long left = READY_AT.getOrDefault(sign.id(), 0L) - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }
}
