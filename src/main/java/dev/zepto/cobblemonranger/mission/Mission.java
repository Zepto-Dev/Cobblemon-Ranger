package dev.zepto.cobblemonranger.mission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.zepto.cobblemonranger.field.FieldMove;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

import java.util.Locale;
import java.util.UUID;

/**
 * A Ranger mission handed out by a villager. {@code target} depends on the type: a Pokémon type for
 * {@link Type#CAPTURE_TYPE}, a field move id for {@link Type#FIELD_MOVE}, an item id for {@link Type#DELIVER}.
 * {@code tier} (1 to 5) is how hard the mission is, set by the level of the Ranger who gave it.
 */
public record Mission(Type type, String target, int required, int progress, UUID giver, Component giverName,
                      int emeralds, int points, int xp, int tier) {
    public static final Codec<Mission> CODEC = RecordCodecBuilder.create(i -> i.group(
            Type.CODEC.fieldOf("type").forGetter(Mission::type),
            Codec.STRING.optionalFieldOf("target", "").forGetter(Mission::target),
            Codec.INT.fieldOf("required").forGetter(Mission::required),
            Codec.INT.optionalFieldOf("progress", 0).forGetter(Mission::progress),
            UUIDUtil.CODEC.fieldOf("giver").forGetter(Mission::giver),
            ComponentSerialization.CODEC.fieldOf("giver_name").forGetter(Mission::giverName),
            Codec.INT.fieldOf("emeralds").forGetter(Mission::emeralds),
            Codec.INT.fieldOf("points").forGetter(Mission::points),
            Codec.INT.fieldOf("xp").forGetter(Mission::xp),
            Codec.INT.optionalFieldOf("tier", 1).forGetter(Mission::tier)
    ).apply(i, Mission::new));

    public boolean isComplete() {
        return type != Type.DELIVER && progress >= required;
    }

    public Mission withProgress(int amount) {
        return new Mission(type, target, required, Math.min(required, progress + amount), giver, giverName, emeralds,
                points, xp, tier);
    }

    public Item deliveryItem() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(target));
    }

    public Component description() {
        String key = "mission.cobblemonranger." + type.getSerializedName();
        return switch (type) {
            case CAPTURE_TYPE -> Component.translatable(key, required, typeName(target));
            case FIELD_MOVE -> {
                FieldMove move = FieldMove.byId(target);
                yield Component.translatable(key, move == null ? target : move.displayName(), required);
            }
            case DELIVER -> Component.translatable(key, required, deliveryItem().getDescription());
            default -> Component.translatable(key, required);
        };
    }

    public static Component typeName(String type) {
        return Component.translatable("type.cobblemonranger." + type.toLowerCase(Locale.ROOT));
    }

    public enum Type implements StringRepresentable {
        CAPTURE("capture"),
        CAPTURE_TYPE("capture_type"),
        FIELD_MOVE("field_move"),
        ASSIST("assist"),
        BATTLE("battle"),
        DELIVER("deliver");

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final String name;

        Type(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
