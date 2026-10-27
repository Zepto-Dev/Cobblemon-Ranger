package dev.zepto.cobblemonranger.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.zepto.cobblemonranger.registry.ModDataComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The styler's own level, raised with XP from captures and missions. Every level adds a little energy, line length
 * and loop power on top of the upgrade modules, like the Styler Level in the DS games.
 */
public record StylerProgress(int level, int xp) {
    public static final int MAX_LEVEL = 20;
    public static final StylerProgress DEFAULT = new StylerProgress(1, 0);

    public static final Codec<StylerProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, MAX_LEVEL).optionalFieldOf("level", 1).forGetter(StylerProgress::level),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("xp", 0).forGetter(StylerProgress::xp)
    ).apply(i, StylerProgress::new));

    public static final StreamCodec<ByteBuf, StylerProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StylerProgress::level,
            ByteBufCodecs.VAR_INT, StylerProgress::xp,
            StylerProgress::new);

    public static StylerProgress get(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.STYLER_PROGRESS.get(), DEFAULT);
    }

    public static int xpToNext(int level) {
        return 40 + 20 * level;
    }

    public boolean isMaxed() {
        return level >= MAX_LEVEL;
    }

    /** Adds XP, carrying over into as many levels as it covers. */
    public StylerProgress withXp(int amount) {
        int newLevel = level;
        int newXp = xp + Math.max(0, amount);
        while (newLevel < MAX_LEVEL && newXp >= xpToNext(newLevel)) {
            newXp -= xpToNext(newLevel);
            newLevel++;
        }
        return new StylerProgress(newLevel, newLevel >= MAX_LEVEL ? 0 : newXp);
    }

    public int energyBonus() {
        return 2 * (level - 1);
    }

    public float lineBonus() {
        return 10f * (level - 1);
    }

    public float powerBonus() {
        return 0.025f * (level - 1);
    }
}
