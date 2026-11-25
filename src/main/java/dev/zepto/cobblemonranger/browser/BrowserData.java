package dev.zepto.cobblemonranger.browser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A player's Ranger Browser: every species (and form) they have befriended with a styler, and which Guide pages they
 * have unlocked and read. Stored as a player attachment that survives death.
 *
 * @param species species key (see {@link BrowserManager#keyOf}) to how often it was befriended
 * @param hinted  whether the player was told how to open the Browser
 */
public record BrowserData(Map<String, Entry> species, Set<String> unlocked, Set<String> read, boolean hinted) {
    public static final BrowserData EMPTY = new BrowserData(Map.of(), Set.of(), Set.of(), false);

    /** How often one species was befriended, and whether one of them was an Alpha. */
    public record Entry(int count, boolean alpha) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("count", 1).forGetter(Entry::count),
                Codec.BOOL.optionalFieldOf("alpha", false).forGetter(Entry::alpha)
        ).apply(i, Entry::new));

        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::count,
                ByteBufCodecs.BOOL, Entry::alpha,
                Entry::new);
    }

    private static final Codec<Set<String>> STRING_SET = Codec.STRING.listOf()
            .xmap(list -> Set.copyOf(new HashSet<>(list)), List::copyOf);

    public static final Codec<BrowserData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Entry.CODEC).optionalFieldOf("species", Map.of())
                    .forGetter(BrowserData::species),
            STRING_SET.optionalFieldOf("guide_unlocked", Set.of()).forGetter(BrowserData::unlocked),
            STRING_SET.optionalFieldOf("guide_read", Set.of()).forGetter(BrowserData::read),
            Codec.BOOL.optionalFieldOf("hinted", false).forGetter(BrowserData::hinted)
    ).apply(i, BrowserData::new));

    public boolean isUnlocked(GuidePage page) {
        return page.startsUnlocked() || unlocked.contains(page.id());
    }

    public boolean isRead(GuidePage page) {
        return read.contains(page.id());
    }

    public BrowserData withSpecies(String key, Entry entry) {
        Map<String, Entry> map = new HashMap<>(species);
        map.put(key, entry);
        return new BrowserData(map, unlocked, read, hinted);
    }

    public BrowserData withUnlocked(GuidePage page) {
        Set<String> set = new HashSet<>(unlocked);
        set.add(page.id());
        return new BrowserData(species, set, read, hinted);
    }

    public BrowserData withRead(GuidePage page) {
        Set<String> set = new HashSet<>(read);
        set.add(page.id());
        return new BrowserData(species, unlocked, set, hinted);
    }

    public BrowserData withHinted() {
        return new BrowserData(species, unlocked, read, true);
    }

    /** Distinct species befriended, counting every form of a species once. */
    public int speciesCount() {
        Set<String> base = new HashSet<>();
        for (String key : species.keySet()) {
            int slash = key.indexOf('/');
            base.add(slash < 0 ? key : key.substring(0, slash));
        }
        return base.size();
    }
}
