package dev.zepto.cobblemonranger.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings, in {@code serverconfig/cobblemonranger-server.toml} of each world. */
public final class RangerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MISSION_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue HQ_CHARGE_COOLDOWN_MINUTES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MISSION_COOLDOWN_MINUTES = builder
                .comment("Real-time minutes a player waits after handing in a mission before any Ranger offers them another.",
                        "20 minutes is one Minecraft day. Missions per rank average about 3, so this sets the pace of ranking up.")
                .defineInRange("missionCooldownMinutes", 20, 0, 24 * 60);
        HQ_CHARGE_COOLDOWN_MINUTES = builder
                .comment("Real-time minutes a player waits between styler recharges at a Ranger HQ Terminal.")
                .defineInRange("hqChargeCooldownMinutes", 10, 0, 360);
        SPEC = builder.build();
    }

    private RangerConfig() {
    }

    public static long missionCooldownTicks() {
        return MISSION_COOLDOWN_MINUTES.get() * 20L * 60L;
    }

    public static long hqChargeCooldownTicks() {
        return HQ_CHARGE_COOLDOWN_MINUTES.get() * 20L * 60L;
    }
}
