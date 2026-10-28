package dev.zepto.cobblemonranger.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.zepto.cobblemonranger.CobblemonRanger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Which sprites the capture minigame draws for a wild Pokémon's attacks. The look follows the Pokémon's type, and
 * some types change with the arena: water turns to bubbles underwater and to ice shards in the snow, fire turns to
 * steam on water and to lava in the Nether, and rock and ground attacks drop boulders from the ceiling in caves.
 * Sprites come from two atlases drawn by tools/attack_textures.py.
 */
public record AttackArt(Element missile, Element particle, Blast blast, int color, Element dust, int dustTint) {
    public static final ResourceLocation ATTACKS = CobblemonRanger.id("textures/gui/capture/attacks.png");
    public static final ResourceLocation BLASTS = CobblemonRanger.id("textures/gui/capture/blasts.png");
    static final int CELL = 16, BLAST_CELL = 32;
    private static final int ATTACKS_WIDTH = 8 * CELL, ATTACKS_HEIGHT = 23 * CELL;
    private static final int BLASTS_WIDTH = 6 * BLAST_CELL, BLASTS_HEIGHT = 8 * BLAST_CELL;

    /** Rows of the attacks atlas: projectile frames in columns 0-3, particle frames in 4-7. */
    public enum Element {
        NORMAL, FIRE(true), WATER(true), GRASS, ELECTRIC, ICE(true), FIGHTING, POISON, GROUND, FLYING(true), PSYCHIC,
        BUG(true), ROCK, GHOST(true), DRAGON(true), DARK, STEEL, FAIRY, BUBBLE, STEAM, LAVA, SAND,
        /** Grey dust kicked up from the ground, tinted with the arena's colour. */
        DUST;

        /** Drawn pointing right and turned to face where the projectile flies; the others spin through frames. */
        public final boolean oriented;

        Element() {
            this(false);
        }

        Element(boolean oriented) {
            this.oriented = oriented;
        }

        static Element of(String type) {
            for (Element element : values()) {
                if (element.ordinal() < BUBBLE.ordinal() && element.name().toLowerCase(Locale.ROOT).equals(type)) {
                    return element;
                }
            }
            return NORMAL;
        }
    }

    /** Rows of the blasts atlas: warning marker in columns 0-1, the blast in 2-5. */
    public enum Blast {
        FIRE(Element.FIRE), GROUND(Element.GROUND), ROCK(Element.ROCK), LAVA(Element.LAVA), SAND(Element.SAND),
        STEAM(Element.STEAM),
        /** White, tinted with the type's colour, for types without a blast of their own. */
        GENERIC(null),
        /** A boulder falls from the cave ceiling: a growing shadow, then the rock shatters. */
        ROCKFALL(Element.ROCK);

        /** Debris thrown out by the blast; null uses the attack's own particle. */
        final Element debris;

        Blast(Element debris) {
            this.debris = debris;
        }
    }

    public static AttackArt resolve(String pokemonType, ArenaTheme theme, int plantTint, int typeColor) {
        String type = pokemonType == null ? "" : pokemonType.toLowerCase(Locale.ROOT);
        Element element = Element.of(type);
        boolean cave = theme == ArenaTheme.CAVE || theme == ArenaTheme.DEEP_CAVE || theme == ArenaTheme.LUSH_CAVE;
        boolean hot = theme == ArenaTheme.NETHER || theme == ArenaTheme.DESERT || theme == ArenaTheme.BADLANDS;

        Element missile = switch (element) {
            case WATER -> theme == ArenaTheme.WATER ? Element.BUBBLE : theme == ArenaTheme.SNOW ? Element.ICE : element;
            case ICE -> hot ? Element.WATER : element;
            case FIRE -> theme == ArenaTheme.WATER ? Element.STEAM : element;
            default -> element;
        };
        Blast blast = switch (element) {
            case FIRE -> theme == ArenaTheme.WATER ? Blast.STEAM : theme == ArenaTheme.NETHER ? Blast.LAVA : Blast.FIRE;
            case GROUND -> cave ? Blast.ROCKFALL : theme == ArenaTheme.DESERT || theme == ArenaTheme.BEACH
                    || theme == ArenaTheme.BADLANDS ? Blast.SAND : Blast.GROUND;
            case ROCK -> cave ? Blast.ROCKFALL : Blast.ROCK;
            default -> Blast.GENERIC;
        };
        Element dust = theme == ArenaTheme.WATER ? Element.BUBBLE : Element.DUST;
        int dustTint = switch (theme) {
            case MEADOW, FOREST, JUNGLE, SWAMP, LUSH_CAVE -> plantTint;
            case DESERT, BEACH -> 0xEAD7A0;
            case BADLANDS -> 0xD9884E;
            case MOUNTAIN, CAVE, GRAVEL -> 0xB0B4BC;
            case DEEP_CAVE -> 0x6E7284;
            case NETHER -> 0xB8574E;
            case END -> 0xF0F0B8;
            case MUSHROOM -> 0xB8A4B8;
            case SNOW, WATER -> 0xFFFFFF;
        };
        return new AttackArt(missile, missile, blast, typeColor, dust, dustTint);
    }

    /** Debris for this attack's blasts. */
    Element debris() {
        return blast.debris == null ? particle : blast.debris;
    }

    // ------------------------------------------------------------------ drawing

    /** Call before a run of sprite draws: Shapes' render type leaves blending off after a flush. */
    static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** One cell of the attacks atlas centred on (x, y), {@code size} GUI pixels across. */
    static void sprite(GuiGraphics graphics, Element element, int column, float x, float y, float size, float angle,
                       int tint, float alpha) {
        draw(graphics, ATTACKS, ATTACKS_WIDTH, ATTACKS_HEIGHT, CELL, column, element.ordinal(), x, y, size, angle, tint,
                alpha);
    }

    /** One cell of the blasts atlas centred on (x, y). */
    static void blast(GuiGraphics graphics, Blast blast, int column, float x, float y, float size, int tint, float alpha) {
        draw(graphics, BLASTS, BLASTS_WIDTH, BLASTS_HEIGHT, BLAST_CELL, column, blast.ordinal(), x, y, size, 0, tint,
                alpha);
    }

    private static void draw(GuiGraphics graphics, ResourceLocation texture, int textureWidth, int textureHeight,
                             int cell, int column, int row, float x, float y, float size, float angle, int tint,
                             float alpha) {
        graphics.setColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        if (angle != 0) {
            graphics.pose().mulPose(Axis.ZP.rotation(angle));
        }
        graphics.pose().scale(size / cell, size / cell, 1);
        graphics.blit(texture, -cell / 2, -cell / 2, cell, cell, column * cell, row * cell, cell, cell,
                textureWidth, textureHeight);
        graphics.pose().popPose();
        graphics.setColor(1, 1, 1, 1);
    }
}
