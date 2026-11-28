package dev.zepto.cobblemonranger.rescue;

import dev.zepto.cobblemonranger.field.FieldMove;
import dev.zepto.cobblemonranger.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The ways a wild Pokémon can be trapped, and the field move that frees it. Each block scenario puts a ring of trap
 * blocks around the Pokémon: {@code radius} blocks out, {@code height} blocks tall, optionally with a roof
 * ({@code cap}) or taller corners. {@link #MOBS} has no blocks: hostile mobs corner the Pokémon instead.
 */
public enum RescueScenario implements StringRepresentable {
    RUBBLE("rubble", FieldMove.ROCK_SMASH, 1, 1, false, true),
    TANGLE("tangle", FieldMove.CUT, 1, 2, false, false),
    FIRE("fire", FieldMove.SURF, 2, 1, false, false),
    ICE("ice", FieldMove.BURN, 1, 2, true, false),
    SAND("sand", FieldMove.DIG, 1, 1, false, false),
    MOBS("mobs", FieldMove.SPOOK, 2, 0, false, false);

    public static final com.mojang.serialization.Codec<RescueScenario> CODEC =
            StringRepresentable.fromEnum(RescueScenario::values);

    private final String id;
    private final FieldMove move;
    private final int radius;
    private final int height;
    private final boolean cap;
    private final boolean tallCorners;

    RescueScenario(String id, FieldMove move, int radius, int height, boolean cap, boolean tallCorners) {
        this.id = id;
        this.move = move;
        this.radius = radius;
        this.height = height;
        this.cap = cap;
        this.tallCorners = tallCorners;
    }

    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public FieldMove move() {
        return move;
    }

    public int radius() {
        return radius;
    }

    public Block block() {
        return switch (this) {
            case RUBBLE -> ModBlocks.RESCUE_RUBBLE.get();
            case TANGLE -> ModBlocks.RESCUE_TANGLE.get();
            case FIRE -> ModBlocks.RESCUE_FIRE.get();
            case ICE -> ModBlocks.RESCUE_ICE.get();
            case SAND -> ModBlocks.RESCUE_SAND.get();
            case MOBS -> null;
        };
    }

    /** "trapped under rubble" and so on, for the alert. */
    public Component trappedText() {
        return Component.translatable("rescue.cobblemonranger." + id);
    }

    /** Where the trap blocks go around a Pokémon standing at {@code center}. */
    public List<BlockPos> trapPositions(BlockPos center) {
        List<BlockPos> positions = new ArrayList<>();
        if (height == 0) {
            return positions;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                    continue;
                }
                for (int y = 0; y < height; y++) {
                    positions.add(center.offset(dx, y, dz));
                }
                if (tallCorners && Math.abs(dx) == radius && Math.abs(dz) == radius) {
                    positions.add(center.offset(dx, height, dz));
                }
            }
        }
        if (cap) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    positions.add(center.offset(dx, height, dz));
                }
            }
        }
        return positions;
    }

    public static RescueScenario byId(String id) {
        for (RescueScenario scenario : values()) {
            if (scenario.id.equals(id)) {
                return scenario;
            }
        }
        return null;
    }
}
