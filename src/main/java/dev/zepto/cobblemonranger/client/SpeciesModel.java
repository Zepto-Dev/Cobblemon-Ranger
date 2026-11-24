package dev.zepto.cobblemonranger.client;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.util.Set;

/** Draws a species' Cobblemon model in a box, turning slowly, the way Cobblemon's own summary screen does. */
public class SpeciesModel {
    /** Matches Cobblemon's summary screen, which also draws with {@link ProfileTransformType#SUMMARY}. */
    private static final float BASE_SCALE = 2.7f;
    private static final float DEGREES_PER_SECOND = 30f;

    private Species species;
    private RenderablePokemon renderable;
    private FloatingState state;
    private long startMillis;

    public void render(GuiGraphics graphics, Species newSpecies, int x, int y, int w, int h, float partialTick) {
        if (newSpecies != species) {
            species = newSpecies;
            renderable = new RenderablePokemon(newSpecies, Set.of(), ItemStack.EMPTY);
            state = new FloatingState();
            startMillis = System.currentTimeMillis();
        }
        float spin = (System.currentTimeMillis() - startMillis) / 1000f * DEGREES_PER_SECOND;
        PoseStack pose = graphics.pose();
        graphics.enableScissor(x, y, x + w, y + h);
        pose.pushPose();
        // The SUMMARY profile draws the model well below the origin, so the origin sits above the box.
        pose.translate(x + w / 2.0, y - h * 0.3, 100);
        float scale = BASE_SCALE * 1.4f * Math.min(1f, h / 100f);
        pose.scale(scale, scale, scale);
        Quaternionf rotation = new Quaternionf().rotationXYZ((float) Math.toRadians(13),
                (float) Math.toRadians(35 + spin), 0);
        try {
            PokemonGuiUtilsKt.drawProfilePokemon(renderable, pose, rotation, PoseType.PROFILE, state, partialTick,
                    20f, ProfileTransformType.SUMMARY, false, 1f, 1f, 1f, 1f, 0f, 0f, 15);
        } catch (RuntimeException e) {
            // A broken or missing model from a species addon shouldn't take the whole screen down.
            species = null;
        }
        pose.popPose();
        graphics.disableScissor();
    }
}
