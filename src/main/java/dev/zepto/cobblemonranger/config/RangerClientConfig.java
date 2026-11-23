package dev.zepto.cobblemonranger.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings, in {@code config/cobblemonranger-client.toml}. Also editable from the Mods screen. */
public final class RangerClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAPTURE_MUSIC;
    public static final ModConfigSpec.IntValue CAPTURE_MUSIC_VOLUME;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CAPTURE_MUSIC = builder
                .comment("Play music during the capture minigame and a jingle when a capture succeeds.",
                        "Minecraft's normal background music pauses while it plays.")
                .define("captureMusic", true);
        CAPTURE_MUSIC_VOLUME = builder
                .comment("Volume of the capture music and jingle, in percent. Minecraft's Music volume slider applies on top.")
                .defineInRange("captureMusicVolume", 100, 0, 100);
        SPEC = builder.build();
    }

    private RangerClientConfig() {
    }

    public static boolean musicEnabled() {
        return !SPEC.isLoaded() || CAPTURE_MUSIC.get() && CAPTURE_MUSIC_VOLUME.get() > 0;
    }

    /** 0..1; falls back to the default when the config isn't loaded yet. */
    public static float musicVolume() {
        return (SPEC.isLoaded() ? CAPTURE_MUSIC_VOLUME.get() : CAPTURE_MUSIC_VOLUME.getDefault()) / 100f;
    }
}
