package dev.zepto.cobblemonranger.ranger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.zepto.cobblemonranger.mission.Mission;
import net.minecraft.core.UUIDUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the mod remembers about a player as a Ranger: their Partner Pokémon, Ranger points (which decide their
 * rank), the active mission and Ranger Sign cooldowns. Stored as a player attachment that survives death.
 */
public record RangerData(Optional<UUID> partner, int points, int missionsDone, Optional<Mission> mission,
                         Map<String, Long> signCooldowns) {
    public static final RangerData EMPTY = new RangerData(Optional.empty(), 0, 0, Optional.empty(), Map.of());

    public static final Codec<RangerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("partner").forGetter(RangerData::partner),
            Codec.INT.optionalFieldOf("points", 0).forGetter(RangerData::points),
            Codec.INT.optionalFieldOf("missions_done", 0).forGetter(RangerData::missionsDone),
            Mission.CODEC.optionalFieldOf("mission").forGetter(RangerData::mission),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("sign_cooldowns", Map.of())
                    .forGetter(RangerData::signCooldowns)
    ).apply(i, RangerData::new));

    /** Ranger points needed for each rank; rank N needs {@code RANK_POINTS[N - 1]}. */
    private static final int[] RANK_POINTS = {0, 40, 100, 180, 280, 400, 550, 750, 1000, 1300};
    public static final int MAX_RANK = RANK_POINTS.length;

    public static int rankFor(int points) {
        int rank = 1;
        while (rank < MAX_RANK && points >= RANK_POINTS[rank]) {
            rank++;
        }
        return rank;
    }

    /** Points needed to reach {@code rank}. */
    public static int pointsForRank(int rank) {
        return RANK_POINTS[Math.max(1, Math.min(rank, MAX_RANK)) - 1];
    }

    /** Points needed for the next rank, or -1 at the top rank. */
    public static int pointsForNextRank(int rank) {
        return rank >= MAX_RANK ? -1 : RANK_POINTS[rank];
    }

    public int rank() {
        return rankFor(points);
    }

    public RangerData withPartner(Optional<UUID> partner) {
        return new RangerData(partner, points, missionsDone, mission, signCooldowns);
    }

    public RangerData withPoints(int points) {
        return new RangerData(partner, points, missionsDone, mission, signCooldowns);
    }

    public RangerData withMission(Optional<Mission> mission) {
        return new RangerData(partner, points, missionsDone, mission, signCooldowns);
    }

    public RangerData withMissionCompleted(int earnedPoints) {
        return new RangerData(partner, points + earnedPoints, missionsDone + 1, Optional.empty(), signCooldowns);
    }

    public RangerData withSignCooldown(String sign, long readyAt) {
        Map<String, Long> cooldowns = new HashMap<>(signCooldowns);
        cooldowns.put(sign, readyAt);
        return new RangerData(partner, points, missionsDone, mission, cooldowns);
    }
}
