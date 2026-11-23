package dev.zepto.cobblemonranger.registry;

import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound events of the mod; the files are listed in assets/cobblemonranger/sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, CobblemonRanger.MOD_ID);

    /** Looping theme of the capture minigame (made by tools/music.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPTURE_MUSIC = register("music.capture");
    /** Short jingle when a capture succeeds. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VICTORY_MUSIC = register("music.victory");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CobblemonRanger.id(name)));
    }

    private ModSounds() {
    }
}
