package dev.zepto.cobblemonranger.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/** Untextured lines, rings and discs for the capture screen, drawn as GUI quads. */
final class Shapes {
    private final VertexConsumer consumer;
    private final Matrix4f matrix;

    Shapes(GuiGraphics graphics) {
        this.consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        this.matrix = graphics.pose().last().pose();
    }

    void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
        // Emitted in both windings so face culling can never hide it.
        vertex(x1, y1, color);
        vertex(x2, y2, color);
        vertex(x3, y3, color);
        vertex(x4, y4, color);
        vertex(x4, y4, color);
        vertex(x3, y3, color);
        vertex(x2, y2, color);
        vertex(x1, y1, color);
    }

    void rect(float x1, float y1, float x2, float y2, int color) {
        quad(x1, y1, x1, y2, x2, y2, x2, y1, color);
    }

    void line(float ax, float ay, float bx, float by, float thickness, int color) {
        float dx = bx - ax;
        float dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) {
            return;
        }
        float nx = -dy / len * thickness / 2;
        float ny = dx / len * thickness / 2;
        quad(ax + nx, ay + ny, ax - nx, ay - ny, bx - nx, by - ny, bx + nx, by + ny, color);
    }

    void arc(float cx, float cy, float radius, float thickness, float start, float sweep, int color) {
        int segments = Math.max(4, (int) (Math.abs(sweep) / (Math.PI * 2) * 48));
        float inner = radius - thickness / 2;
        float outer = radius + thickness / 2;
        for (int i = 0; i < segments; i++) {
            float a0 = start + sweep * i / segments;
            float a1 = start + sweep * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(cx + c0 * inner, cy + s0 * inner, cx + c0 * outer, cy + s0 * outer,
                    cx + c1 * outer, cy + s1 * outer, cx + c1 * inner, cy + s1 * inner, color);
        }
    }

    void ring(float cx, float cy, float radius, float thickness, int color) {
        arc(cx, cy, radius, thickness, 0, (float) (Math.PI * 2), color);
    }

    void disc(float cx, float cy, float radius, int color) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2 * i / segments);
            float a1 = (float) (Math.PI * 2 * (i + 1) / segments);
            float x0 = cx + (float) Math.cos(a0) * radius, y0 = cy + (float) Math.sin(a0) * radius;
            float x1 = cx + (float) Math.cos(a1) * radius, y1 = cy + (float) Math.sin(a1) * radius;
            quad(cx, cy, x0, y0, x1, y1, x1, y1, color);
        }
    }

    private void vertex(float x, float y, int color) {
        consumer.addVertex(matrix, x, y, 0).setColor(color);
    }
}
