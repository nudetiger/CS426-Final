package com.cs426.learningmocha.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForceLayoutTest {

    private static final float BOX = 1000f;
    private static final long SEED = 42L;

    @Test
    public void sameSeedProducesSameLayout() {
        int[] from = {0, 1, 2, 3};
        int[] to = {1, 2, 3, 0};
        float[] first = ForceLayout.layout(6, from, to, BOX, BOX, 120, SEED);
        float[] second = ForceLayout.layout(6, from, to, BOX, BOX, 120, SEED);
        assertArrayEquals(first, second, 0f);
    }

    @Test
    public void differentSeedProducesDifferentLayout() {
        int[] from = {0, 1, 2, 3};
        int[] to = {1, 2, 3, 0};
        float[] first = ForceLayout.layout(20, from, to, BOX, BOX, 120, SEED);
        float[] second = ForceLayout.layout(20, from, to, BOX, BOX, 120, SEED + 1);
        boolean identical = true;
        for (int i = 0; i < first.length; i++) {
            if (first[i] != second[i]) {
                identical = false;
                break;
            }
        }
        assertFalse("a different seed should move at least one node", identical);
    }

    @Test
    public void everyNodeStaysInsideTheBox() {
        int[] from = {0, 0, 0, 0, 1, 2};
        int[] to = {1, 2, 3, 4, 2, 3};
        float[] pos = ForceLayout.layout(12, from, to, 640f, 480f, 200, SEED);
        assertEquals(24, pos.length);
        for (int i = 0; i < 12; i++) {
            assertTrue("x in range", pos[i * 2] >= 0f && pos[i * 2] <= 640f);
            assertTrue("y in range", pos[i * 2 + 1] >= 0f && pos[i * 2 + 1] <= 480f);
        }
    }

    @Test
    public void ringNeighboursEndUpCloserThanOppositeNodes() {
        // A 6-cycle relaxes into a hexagon, so adjacent nodes must be nearer than antipodes.
        int[] from = {0, 1, 2, 3, 4, 5};
        int[] to = {1, 2, 3, 4, 5, 0};
        float[] pos = ForceLayout.layout(6, from, to, BOX, BOX, 300, SEED);
        assertTrue(
                "linked neighbours should be closer than unlinked antipodes",
                distance(pos, 0, 1) < distance(pos, 0, 3)
        );
    }

    @Test
    public void twoCliquesSeparate() {
        int[] from = {0, 0, 0, 1, 1, 2, 4, 4, 4, 5, 5, 6};
        int[] to = {1, 2, 3, 2, 3, 3, 5, 6, 7, 6, 7, 7};
        float[] pos = ForceLayout.layout(8, from, to, BOX, BOX, 300, SEED);
        assertTrue(
                "average distance inside a clique should be smaller than across cliques",
                meanDistance(pos, 0, 4, 0, 4) < meanDistance(pos, 0, 4, 4, 8)
        );
    }

    @Test
    public void isolatedNodesAreSpreadOutNotStacked() {
        float[] pos = ForceLayout.layout(5, new int[0], new int[0], BOX, BOX, 150, SEED);
        for (int i = 0; i < 5; i++) {
            for (int j = i + 1; j < 5; j++) {
                assertTrue("nodes " + i + " and " + j + " overlap", distance(pos, i, j) > 1f);
            }
        }
    }

    @Test
    public void emptyGraphReturnsNoPositions() {
        assertEquals(0, ForceLayout.layout(0, new int[0], new int[0], BOX, BOX, 100, SEED).length);
        assertEquals(0, ForceLayout.layout(-3, null, null, BOX, BOX, 100, SEED).length);
    }

    @Test
    public void singleNodeSitsInTheCentre() {
        float[] pos = ForceLayout.layout(1, new int[]{0}, new int[]{0}, 400f, 200f, 100, SEED);
        assertEquals(2, pos.length);
        assertEquals(200f, pos[0], 0.001f);
        assertEquals(100f, pos[1], 0.001f);
    }

    @Test
    public void outOfRangeAndSelfEdgesAreIgnored() {
        int[] validFrom = {0};
        int[] validTo = {1};
        int[] noisyFrom = {0, 5, -1, 2, 99};
        int[] noisyTo = {1, 1, 2, 2, -4};
        float[] clean = ForceLayout.layout(3, validFrom, validTo, BOX, BOX, 120, SEED);
        float[] noisy = ForceLayout.layout(3, noisyFrom, noisyTo, BOX, BOX, 120, SEED);
        assertArrayEquals("bad edge indices must be dropped, not applied", clean, noisy, 0f);
    }

    @Test
    public void mismatchedEdgeArraysDoNotCrash() {
        float[] pos = ForceLayout.layout(4, new int[]{0, 1, 2}, new int[]{1}, BOX, BOX, 80, SEED);
        assertEquals(8, pos.length);
    }

    @Test
    public void degenerateBoxAndIterationCountsAreTolerated() {
        float[] pos = ForceLayout.layout(3, new int[]{0}, new int[]{1}, 0f, -5f, 0, SEED);
        assertEquals(6, pos.length);
        for (float value : pos) {
            assertTrue(value >= 0f && value <= 1f);
        }
    }

    @Test
    public void suggestedIterationsStayWithinBudget() {
        assertEquals(1, ForceLayout.suggestedIterations(0));
        assertEquals(1, ForceLayout.suggestedIterations(1));
        assertEquals(400, ForceLayout.suggestedIterations(10));
        assertEquals(200, ForceLayout.suggestedIterations(300));
        assertEquals(80, ForceLayout.suggestedIterations(5000));
    }

    private static float distance(float[] pos, int a, int b) {
        float dx = pos[a * 2] - pos[b * 2];
        float dy = pos[a * 2 + 1] - pos[b * 2 + 1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Mean distance over every pair drawn from [aStart,aEnd) x [bStart,bEnd), skipping i==j. */
    private static float meanDistance(float[] pos, int aStart, int aEnd, int bStart, int bEnd) {
        float total = 0f;
        int count = 0;
        for (int i = aStart; i < aEnd; i++) {
            for (int j = bStart; j < bEnd; j++) {
                if (i == j) {
                    continue;
                }
                total += distance(pos, i, j);
                count++;
            }
        }
        return count == 0 ? 0f : total / count;
    }
}
