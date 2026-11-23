package dev.zepto.cobblemonranger.client;

import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Sprites for Poké Assist effects, from the atlas drawn by tools/assist_textures.py. */
final class AssistArt {
    static final ResourceLocation TEXTURE = CobblemonRanger.id("textures/gui/capture/assists.png");
    static final int CELL = 16;
    private static final int WIDTH = 8 * CELL, HEIGHT = 7 * CELL;

    /** Rows of the atlas, in order. Crags and shards stand on the bottom of their cell; the rest are centred. */
    enum Sprite {
        CRAG(4), SHARD(4), WISP(2), SMOKE(3), POISON_BUBBLE(3), HEART(1), SNOWFLAKE(1), SPARKLE(1), BATTERY(6);

        final int variants;

        Sprite(int variants) {
            this.variants = variants;
        }

        int row() {
            return switch (this) {
                case HEART, SNOWFLAKE, SPARKLE -> 5;
                case BATTERY -> 6;
                default -> ordinal();
            };
        }

        int column(int variant) {
            return switch (this) {
                case SNOWFLAKE -> 1;
                case SPARKLE -> 2;
                default -> Math.floorMod(variant, variants);
            };
        }
    }

    private AssistArt() {
    }

    /** Draws a sprite centred on (x, y), {@code size} GUI pixels across. */
    static void sprite(GuiGraphics graphics, Sprite sprite, int variant, float x, float y, float size, float alpha) {
        AttackArt.draw(graphics, TEXTURE, WIDTH, HEIGHT, CELL, sprite.column(variant), sprite.row(), x, y, size, 0,
                0xFFFFFF, Math.max(0, Math.min(1, alpha)));
    }

    /** Draws a crag or shard so that its base sits on (x, y). */
    static void standing(GuiGraphics graphics, Sprite sprite, int variant, float x, float y, float size, float alpha) {
        sprite(graphics, sprite, variant, x, y - size * 6 / CELL, size, alpha);
    }
}
