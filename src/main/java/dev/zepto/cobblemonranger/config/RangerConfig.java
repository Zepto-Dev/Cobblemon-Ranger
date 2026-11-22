package dev.zepto.cobblemonranger.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings, in {@code serverconfig/cobblemonranger-server.toml} of each world. */
public final class RangerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MISSION_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue WORLD_SIGN_PERSONAL_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue WORLD_SIGN_SHARED_COOLDOWN_MINUTES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MISSION_COOLDOWN_MINUTES = builder
                .comment("Real-time minutes a player waits after handing in a mission before any Ranger offers them another.",
                        "20 minutes is one Minecraft day. Missions per rank average about 3, so this sets the pace of ranking up.")
                .defineInRange("missionCooldownMinutes", 20, 0, 24 * 60);
        builder.push("signs");
        WORLD_SIGN_PERSONAL_COOLDOWN_MINUTES = builder
                .comment("Real-time minutes before a player can use the same weather or time sign again",
                        "(Groudon, Kyogre, Cresselia, Darkrai).")
                .defineInRange("worldSignPersonalCooldownMinutes", 60, 0, 24 * 60);
        WORLD_SIGN_SHARED_COOLDOWN_MINUTES = builder
                .comment("Real-time minutes after anyone uses a weather or time sign before anyone in the world can use",
                        "any of them again. Stops players on a server from flipping the weather back and forth.")
                .defineInRange("worldSignSharedCooldownMinutes", 20, 0, 24 * 60);
        builder.pop();
        SPEC = builder.build();
    }

    private RangerConfig() {
    }

    public static long missionCooldownTicks() {
        return MISSION_COOLDOWN_MINUTES.get() * 20L * 60L;
    }

    public static long worldSignPersonalCooldownTicks() {
        return valueOr(WORLD_SIGN_PERSONAL_COOLDOWN_MINUTES, 60) * 20L * 60L;
    }

    public static long worldSignSharedCooldownTicks() {
        return valueOr(WORLD_SIGN_SHARED_COOLDOWN_MINUTES, 20) * 20L * 60L;
    }

    /** Server config isn't loaded on a client outside a world, where only the default matters. */
    private static int valueOr(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
