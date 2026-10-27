package dev.zepto.cobblemonranger.signs;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches a drawn stroke against glyph templates, in the style of the "$1" unistroke recognizer: both are resampled
 * to the same number of points, centred and scaled to the same size, then compared point by point. Glyphs can be drawn in
 * either direction, and closed glyphs from any starting point. Orientation matters, so a V is not a Λ.
 */
public final class SignRecognizer {
    private static final int POINTS = 48;
    /** Average distance (in units of the glyph's size) above which a stroke matches nothing. */
    public static final float MAX_SCORE = 0.16f;

    private SignRecognizer() {
    }

    /** Returns the best template's index, or -1 if none is close enough. */
    public static int recognize(List<float[]> stroke, List<float[][]> templates, List<Boolean> closed) {
        if (stroke.size() < 5 || pathLength(stroke) < 1e-3f) {
            return -1;
        }
        float[][] candidate = normalize(resample(stroke));
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int t = 0; t < templates.size(); t++) {
            float[][] template = normalize(resample(toList(templates.get(t))));
            float score = score(candidate, template, closed.get(t));
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return bestScore <= MAX_SCORE ? best : -1;
    }

    static float score(float[][] candidate, float[][] template, boolean closed) {
        float best = Float.MAX_VALUE;
        int shifts = closed ? POINTS : 1;
        for (int reverse = 0; reverse < 2; reverse++) {
            for (int shift = 0; shift < shifts; shift += 2) {
                float total = 0;
                for (int i = 0; i < POINTS; i++) {
                    int j = (i + shift) % POINTS;
                    float[] c = candidate[reverse == 0 ? j : POINTS - 1 - j];
                    float[] p = template[i];
                    total += (float) Math.hypot(c[0] - p[0], c[1] - p[1]);
                }
                best = Math.min(best, total / POINTS);
            }
        }
        return best;
    }

    private static List<float[]> toList(float[][] points) {
        List<float[]> list = new ArrayList<>();
        for (float[] point : points) {
            list.add(point);
        }
        return list;
    }

    public static float pathLength(List<float[]> points) {
        float length = 0;
        for (int i = 1; i < points.size(); i++) {
            float[] a = points.get(i - 1);
            float[] b = points.get(i);
            length += (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
        }
        return length;
    }

    static float[][] resample(List<float[]> input) {
        float interval = pathLength(input) / (POINTS - 1);
        float[][] out = new float[POINTS][];
        out[0] = new float[]{input.get(0)[0], input.get(0)[1]};
        int count = 1;
        float accumulated = 0;
        float px = input.get(0)[0], py = input.get(0)[1];
        for (int i = 1; i < input.size() && count < POINTS; i++) {
            float qx = input.get(i)[0], qy = input.get(i)[1];
            float d = (float) Math.hypot(qx - px, qy - py);
            while (accumulated + d >= interval && d > 0 && count < POINTS) {
                float t = (interval - accumulated) / d;
                px = px + t * (qx - px);
                py = py + t * (qy - py);
                out[count++] = new float[]{px, py};
                d = (float) Math.hypot(qx - px, qy - py);
                accumulated = 0;
            }
            accumulated += d;
            px = qx;
            py = qy;
        }
        while (count < POINTS) {
            float[] last = input.get(input.size() - 1);
            out[count++] = new float[]{last[0], last[1]};
        }
        return out;
    }

    static float[][] normalize(float[][] points) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float cx = 0, cy = 0;
        for (float[] p : points) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            cx += p[0];
            cy += p[1];
        }
        cx /= points.length;
        cy /= points.length;
        // Each axis is scaled on its own (so a squashed circle still matches), but never by more than 3x the other,
        // which keeps nearly straight strokes from being blown up into big shapes.
        float size = Math.max(1e-3f, Math.max(maxX - minX, maxY - minY));
        float width = Math.max(size / 3, maxX - minX);
        float height = Math.max(size / 3, maxY - minY);
        float[][] out = new float[points.length][];
        for (int i = 0; i < points.length; i++) {
            out[i] = new float[]{(points[i][0] - cx) / width, (points[i][1] - cy) / height};
        }
        return out;
    }
}
