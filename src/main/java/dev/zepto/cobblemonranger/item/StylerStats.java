package dev.zepto.cobblemonranger.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.zepto.cobblemonranger.registry.ModDataComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * Upgrade levels of a Capture Styler. Each stat starts at 0 (shown as Lv. 1) and can be raised to {@link #MAX_LEVEL}
 * with upgrade modules at a smithing table.
 */
public record StylerStats(int energy, int line, int power) {
    public static final int MAX_LEVEL = 4;
    public static final StylerStats DEFAULT = new StylerStats(0, 0, 0);

    public static final Codec<StylerStats> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, MAX_LEVEL).optionalFieldOf("energy", 0).forGetter(StylerStats::energy),
            Codec.intRange(0, MAX_LEVEL).optionalFieldOf("line", 0).forGetter(StylerStats::line),
            Codec.intRange(0, MAX_LEVEL).optionalFieldOf("power", 0).forGetter(StylerStats::power)
    ).apply(i, StylerStats::new));

    public static final StreamCodec<ByteBuf, StylerStats> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StylerStats::energy,
            ByteBufCodecs.VAR_INT, StylerStats::line,
            ByteBufCodecs.VAR_INT, StylerStats::power,
            StylerStats::new);

    public static final int BASE_ENERGY = 30;
    public static final int ENERGY_PER_LEVEL = 15;

    public static StylerStats get(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.STYLER_STATS.get(), DEFAULT);
    }

    public int maxEnergy() {
        return BASE_ENERGY + ENERGY_PER_LEVEL * energy;
    }

    /** Longest capture line, in minigame arena units (the arena is 320x200). */
    public float lineLength() {
        return 360f + 110f * line;
    }

    /** Multiplier applied to the friendship gained per loop. */
    public float loopPower() {
        return 1f + 0.2f * power;
    }

    public int level(Stat stat) {
        return switch (stat) {
            case ENERGY -> energy;
            case LINE -> line;
            case POWER -> power;
        };
    }

    public StylerStats upgraded(Stat stat) {
        return switch (stat) {
            case ENERGY -> new StylerStats(Math.min(MAX_LEVEL, energy + 1), line, power);
            case LINE -> new StylerStats(energy, Math.min(MAX_LEVEL, line + 1), power);
            case POWER -> new StylerStats(energy, line, Math.min(MAX_LEVEL, power + 1));
        };
    }

    public enum Stat implements StringRepresentable {
        ENERGY("energy"),
        LINE("line"),
        POWER("power");

        public static final Codec<Stat> CODEC = StringRepresentable.fromEnum(Stat::values);
        public static final StreamCodec<ByteBuf, Stat> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

        private final String name;

        Stat(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
