package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.config.RangerClientConfig;
import dev.zepto.cobblemonranger.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Music of the capture minigame: a looping theme while the capture runs and a jingle when it succeeds. Both play on
 * the Music channel, so Minecraft's Music slider applies, and the normal background music is held off meanwhile.
 */
public final class CaptureMusic {
    /** Ticks between nudges that keep vanilla music from starting; each nudge adds 100 ticks of delay. */
    private static final int HOLD_OFF_INTERVAL = 80;

    private static Loop loop;

    private CaptureMusic() {
    }

    /** Starts the capture theme. Returns false when music is turned off in the client config. */
    public static boolean start() {
        return start(false);
    }

    /**
     * Starts the capture theme; a boss capture plays it a little faster and higher, which lifts it into a more urgent
     * key. Returns false when music is turned off in the client config.
     */
    public static boolean start(boolean boss) {
        stop(0.2f);
        if (!RangerClientConfig.musicEnabled()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getMusicManager().stopPlaying();
        loop = new Loop(ModSounds.CAPTURE_MUSIC.get(), RangerClientConfig.musicVolume(), boss ? 1.12f : 1f);
        minecraft.getSoundManager().play(loop);
        return true;
    }

    /** Fades the capture theme out over the given number of seconds. */
    public static void stop(float fadeSeconds) {
        if (loop != null) {
            loop.fadeOut(fadeSeconds);
            loop = null;
        }
    }

    /** Ends the theme and plays the victory jingle. Returns false when music is turned off. */
    public static boolean victory() {
        stop(0.25f);
        if (!RangerClientConfig.musicEnabled()) {
            return false;
        }
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModSounds.VICTORY_MUSIC.get().getLocation(),
                SoundSource.MUSIC, RangerClientConfig.musicVolume(), 1f, SoundInstance.createUnseededRandom(), false, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        return true;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final float fullVolume;
        private float fadeStep;
        private int age;

        Loop(SoundEvent event, float volume, float pitch) {
            super(event, SoundSource.MUSIC, RandomSource.create());
            this.pitch = pitch;
            this.fullVolume = volume;
            this.volume = volume;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        void fadeOut(float seconds) {
            fadeStep = seconds <= 0 ? fullVolume : fullVolume / (seconds * 20f);
        }

        @Override
        public void tick() {
            if (fadeStep > 0) {
                volume -= fadeStep;
                if (volume <= 0.001f) {
                    volume = 0;
                    stop();
                }
                return;
            }
            if (++age % HOLD_OFF_INTERVAL == 0) {
                Minecraft.getInstance().getMusicManager().stopPlaying();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
