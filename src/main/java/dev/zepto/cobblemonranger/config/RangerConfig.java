package dev.zepto.cobblemonranger.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings, in {@code serverconfig/cobblemonranger-server.toml} of each world. */
public final class RangerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MISSION_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue CAPTURE_LEVEL_CAP_BASE;
    public static final ModConfigSpec.IntValue CAPTURE_LEVEL_CAP_PER_LEVEL;
    public static final ModConfigSpec.IntValue PARTNER_FIELD_MOVE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue HQ_CHARGE_COOLDOWN_MINUTES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MISSION_COOLDOWN_MINUTES = builder
                .comment("Real-time minutes a player waits after handing in a mission before any Ranger offers them another.",
                        "20 minutes is one Minecraft day. Missions per rank average about 3, so this sets the pace of ranking up.")
                .defineInRange("missionCooldownMinutes", 20, 0, 24 * 60);
        CAPTURE_LEVEL_CAP_BASE = builder
                .comment("Highest Pok\u00e9mon level a level 1 styler can capture. Set to 100 to turn the level cap off.")
                .defineInRange("captureLevelCapBase", 15, 1, 100);
        CAPTURE_LEVEL_CAP_PER_LEVEL = builder
                .comment("How much each styler level after the first raises the capture level cap.",
                        "With the defaults a styler can capture up to Lv. 15 at styler Lv. 1, Lv. 50 at styler Lv. 8",
                        "and anything from styler Lv. 18.")
                .defineInRange("captureLevelCapPerLevel", 5, 0, 100);
        PARTNER_FIELD_MOVE_COOLDOWN_SECONDS = builder
                .comment("Seconds the Partner Pok\u00e9mon rests after using a field move before it can use another.")
                .defineInRange("partnerFieldMoveCooldownSeconds", 4, 0, 600);
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

    /**
     * Highest Pok\u00e9mon level a styler of the given level can capture. Server configs are synced to clients, but
     * outside a world (a tooltip on the title screen) the defaults are used.
     */
    public static int captureLevelCap(int stylerLevel) {
        int base = SPEC.isLoaded() ? CAPTURE_LEVEL_CAP_BASE.get() : CAPTURE_LEVEL_CAP_BASE.getDefault();
        int perLevel = SPEC.isLoaded() ? CAPTURE_LEVEL_CAP_PER_LEVEL.get() : CAPTURE_LEVEL_CAP_PER_LEVEL.getDefault();
        return Math.min(100, base + perLevel * (Math.max(1, stylerLevel) - 1));
    }

    public static int partnerFieldMoveCooldownTicks() {
        return PARTNER_FIELD_MOVE_COOLDOWN_SECONDS.get() * 20;
    }

    public static long hqChargeCooldownTicks() {
        return HQ_CHARGE_COOLDOWN_MINUTES.get() * 20L * 60L;
    }
}
