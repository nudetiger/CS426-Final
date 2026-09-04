package com.cs426.learningmocha.util;

import java.util.Arrays;
import java.util.Random;

/**
 * Deterministic Fruchterman–Reingold force-directed layout for the knowledge graph.
 *
 * Framework-free so it can run off the main thread and be unit-tested on the JVM. Everything
 * is primitive-typed and the result is flattened to {@code {x0,y0,x1,y1,…}} so the graph view
 * can read positions without unboxing or allocating per frame.
 */
public final class ForceLayout {

    /** Layout stops visibly improving well before this; the cap bounds worst-case cost. */
    private static final int MAX_ITERATIONS = 600;

    /** Below this two points count as coincident and get a deterministic nudge instead. */
    private static final float MIN_DISTANCE = 0.01f;

    /** Golden angle: spreads the initial spiral evenly so the first iteration is not degenerate. */
    private static final float GOLDEN_ANGLE = 2.39996323f;

    private ForceLayout() {}

    /**
     * Iteration budget that keeps a large graph responsive while letting a small one settle
     * completely: roughly constant total work regardless of node count.
     */
    public static int suggestedIterations(int nodeCount) {
        if (nodeCount <= 1) {
            return 1;
        }
        int budget = 60000 / nodeCount;
        if (budget < 80) {
            budget = 80;
        }
        if (budget > 400) {
            budget = 400;
        }
        return budget;
    }

    /**
     * @param nodeCount number of nodes; positions are returned for indices {@code 0..nodeCount-1}
     * @param edgeFrom  edge source indices; entries outside {@code [0,nodeCount)}, self-loops and
     *                  any entry past the shorter of the two arrays are ignored
     * @param edgeTo    edge target indices, paired with {@code edgeFrom}
     * @param seed      same seed and same graph always produce the same positions
     * @return {@code {x0,y0,x1,y1,…}}, every coordinate inside {@code [0,width] x [0,height]}
     */
    public static float[] layout(
            int nodeCount,
            int[] edgeFrom,
            int[] edgeTo,
            float width,
            float height,
            int iterations,
            long seed
    ) {
        if (nodeCount <= 0) {
            return new float[0];
        }
        final float w = width > 0f ? width : 1f;
        final float h = height > 0f ? height : 1f;
        if (nodeCount == 1) {
            return new float[]{w * 0.5f, h * 0.5f};
        }

        final float[] pos = new float[nodeCount * 2];
        seedPositions(pos, nodeCount, w, h, new Random(seed));

        final int[][] edges = sanitize(edgeFrom, edgeTo, nodeCount);
        final int[] from = edges[0];
        final int[] to = edges[1];

        final int steps = clampIterations(iterations);
        final float k = (float) Math.sqrt((w * h) / nodeCount);
        final float kSquared = k * k;
        final float[] dispX = new float[nodeCount];
        final float[] dispY = new float[nodeCount];

        float temperature = 0.12f * Math.min(w, h);
        final float cooling = temperature / (steps + 1);

        for (int step = 0; step < steps; step++) {
            Arrays.fill(dispX, 0f);
            Arrays.fill(dispY, 0f);

            for (int i = 0; i < nodeCount; i++) {
                for (int j = i + 1; j < nodeCount; j++) {
                    float dx = pos[i * 2] - pos[j * 2];
                    float dy = pos[i * 2 + 1] - pos[j * 2 + 1];
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist < MIN_DISTANCE) {
                        // Deterministic separation for stacked nodes: depends only on the
                        // indices, so no randomness is consumed inside the hot loop.
                        dx = MIN_DISTANCE * (1 + (i % 3));
                        dy = MIN_DISTANCE * (1 + (j % 3));
                        dist = (float) Math.sqrt(dx * dx + dy * dy);
                    }
                    final float push = kSquared / dist;
                    final float ux = dx / dist * push;
                    final float uy = dy / dist * push;
                    dispX[i] += ux;
                    dispY[i] += uy;
                    dispX[j] -= ux;
                    dispY[j] -= uy;
                }
            }

            for (int e = 0; e < from.length; e++) {
                final int a = from[e];
                final int b = to[e];
                final float dx = pos[a * 2] - pos[b * 2];
                final float dy = pos[a * 2 + 1] - pos[b * 2 + 1];
                final float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < MIN_DISTANCE) {
                    continue;
                }
                final float pull = (dist * dist) / k;
                final float ux = dx / dist * pull;
                final float uy = dy / dist * pull;
                dispX[a] -= ux;
                dispY[a] -= uy;
                dispX[b] += ux;
                dispY[b] += uy;
            }

            for (int i = 0; i < nodeCount; i++) {
                final float len = (float) Math.sqrt(dispX[i] * dispX[i] + dispY[i] * dispY[i]);
                if (len > MIN_DISTANCE) {
                    final float capped = Math.min(len, temperature);
                    pos[i * 2] += dispX[i] / len * capped;
                    pos[i * 2 + 1] += dispY[i] / len * capped;
                }
                pos[i * 2] = clamp(pos[i * 2], 0f, w);
                pos[i * 2 + 1] = clamp(pos[i * 2 + 1], 0f, h);
            }

            temperature -= cooling;
            if (temperature < 0f) {
                temperature = 0f;
            }
        }
        return pos;
    }

    /**
     * Sunflower spiral rather than a plain circle: isolated nodes (which only ever feel
     * repulsion) start spread across the whole box instead of piling up at one radius.
     */
    private static void seedPositions(float[] pos, int nodeCount, float w, float h, Random random) {
        final float cx = w * 0.5f;
        final float cy = h * 0.5f;
        final float radius = 0.42f * Math.min(w, h);
        final float jitter = radius * 0.02f;
        for (int i = 0; i < nodeCount; i++) {
            final float r = radius * (float) Math.sqrt((i + 0.5f) / nodeCount);
            final float angle = i * GOLDEN_ANGLE;
            final float x = cx + r * (float) Math.cos(angle) + (random.nextFloat() - 0.5f) * jitter;
            final float y = cy + r * (float) Math.sin(angle) + (random.nextFloat() - 0.5f) * jitter;
            pos[i * 2] = clamp(x, 0f, w);
            pos[i * 2 + 1] = clamp(y, 0f, h);
        }
    }

    private static int[][] sanitize(int[] edgeFrom, int[] edgeTo, int nodeCount) {
        final int available = (edgeFrom == null || edgeTo == null)
                ? 0
                : Math.min(edgeFrom.length, edgeTo.length);
        int[] from = new int[available];
        int[] to = new int[available];
        int kept = 0;
        for (int e = 0; e < available; e++) {
            final int a = edgeFrom[e];
            final int b = edgeTo[e];
            if (a < 0 || b < 0 || a >= nodeCount || b >= nodeCount || a == b) {
                continue;
            }
            from[kept] = a;
            to[kept] = b;
            kept++;
        }
        if (kept != available) {
            from = Arrays.copyOf(from, kept);
            to = Arrays.copyOf(to, kept);
        }
        return new int[][]{from, to};
    }

    private static int clampIterations(int requested) {
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, MAX_ITERATIONS);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
