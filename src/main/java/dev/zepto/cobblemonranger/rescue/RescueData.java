package dev.zepto.cobblemonranger.rescue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The rescue events of one dimension, saved with it. An event that has ended stays here until every one of its trap
 * blocks has been removed, which can wait until the chunk it is in loads again.
 */
public final class RescueData extends SavedData {
    private static final String NAME = CobblemonRanger.MOD_ID + "_rescues";
    private static final Factory<RescueData> FACTORY = new Factory<>(RescueData::new, RescueData::load, null);
    private static final Codec<List<Event>> EVENTS_CODEC = Event.CODEC.listOf();

    /** One trapped Pokémon. {@code blocks} are the trap blocks still standing; {@code ended} means only cleanup is left. */
    public static final class Event {
        public static final Codec<Event> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(e -> e.id),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(e -> e.owner),
                RescueScenario.CODEC.fieldOf("scenario").forGetter(e -> e.scenario),
                BlockPos.CODEC.fieldOf("center").forGetter(e -> e.center),
                BlockPos.CODEC.listOf().fieldOf("blocks").forGetter(e -> List.copyOf(e.blocks)),
                UUIDUtil.CODEC.fieldOf("pokemon").forGetter(e -> e.pokemon),
                UUIDUtil.CODEC.listOf().optionalFieldOf("mobs", List.of()).forGetter(e -> List.copyOf(e.mobs)),
                Codec.LONG.fieldOf("expires_at").forGetter(e -> e.expiresAt),
                Codec.BOOL.optionalFieldOf("ended", false).forGetter(e -> e.ended)
        ).apply(i, Event::new));

        public final UUID id;
        public final UUID owner;
        public final RescueScenario scenario;
        public final BlockPos center;
        public final Set<BlockPos> blocks;
        public final UUID pokemon;
        public final Set<UUID> mobs;
        public final long expiresAt;
        public boolean ended;
        /** Seconds in a row the trapped Pokémon was missing from a loaded chunk (not saved). */
        public int missing;

        public Event(UUID id, UUID owner, RescueScenario scenario, BlockPos center, List<BlockPos> blocks, UUID pokemon,
                     List<UUID> mobs, long expiresAt, boolean ended) {
            this.id = id;
            this.owner = owner;
            this.scenario = scenario;
            this.center = center;
            this.blocks = new HashSet<>(blocks);
            this.pokemon = pokemon;
            this.mobs = new HashSet<>(mobs);
            this.expiresAt = expiresAt;
            this.ended = ended;
        }
    }

    private final List<Event> events = new ArrayList<>();

    public static RescueData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static RescueData load(CompoundTag tag, HolderLookup.Provider registries) {
        RescueData data = new RescueData();
        Tag list = tag.get("events");
        if (list != null) {
            EVENTS_CODEC.parse(NbtOps.INSTANCE, list)
                    .resultOrPartial(error -> CobblemonRanger.LOGGER.warn("Bad rescue event data: {}", error))
                    .ifPresent(data.events::addAll);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        EVENTS_CODEC.encodeStart(NbtOps.INSTANCE, events).result().ifPresent(list -> tag.put("events", list));
        return tag;
    }

    public List<Event> events() {
        return events;
    }

    public void add(Event event) {
        events.add(event);
        setDirty();
    }

    public void remove(Event event) {
        events.remove(event);
        setDirty();
    }

    public Event byId(UUID id) {
        for (Event event : events) {
            if (event.id.equals(id)) {
                return event;
            }
        }
        return null;
    }

    /** The running (not ended) event that owns the trap block at {@code pos}, if any. */
    public Event activeAt(BlockPos pos) {
        for (Event event : events) {
            if (!event.ended && event.blocks.contains(pos)) {
                return event;
            }
        }
        return null;
    }
}
