package dev.zepto.cobblemonranger.signs;

import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-wide Ranger Sign state, saved with the overworld: when the weather and time signs can next be used by anyone.
 * Times are overworld game ticks, like the players' own sign cooldowns.
 */
public final class SignWorldData extends SavedData {
    private static final String NAME = CobblemonRanger.MOD_ID + "_signs";
    private static final Factory<SignWorldData> FACTORY = new Factory<>(SignWorldData::new, SignWorldData::load, null);

    private long worldSignReadyAt;

    public static SignWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static SignWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        SignWorldData data = new SignWorldData();
        data.worldSignReadyAt = tag.getLong("world_sign_ready_at");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("world_sign_ready_at", worldSignReadyAt);
        return tag;
    }

    public long worldSignReadyAt() {
        return worldSignReadyAt;
    }

    public void setWorldSignReadyAt(long readyAt) {
        this.worldSignReadyAt = readyAt;
        setDirty();
    }
}
