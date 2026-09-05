package com.cs426.learningmocha.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrereqRulesTest {

    /** requires.get(a) = everything a already depends on. */
    private static Map<Long, List<Long>> edges(Object... pairs) {
        Map<Long, List<Long>> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            long from = ((Number) pairs[i]).longValue();
            long to = ((Number) pairs[i + 1]).longValue();
            List<Long> current = map.get(from);
            if (current == null) {
                current = new ArrayList<>();
                map.put(from, current);
            }
            current.add(to);
        }
        return map;
    }

    @Test
    public void aPostCannotRequireItself() {
        assertTrue(PrereqRules.wouldCycle(1L, 1L, new HashMap<Long, List<Long>>()));
    }

    @Test
    public void anUnrelatedPrerequisiteIsFine() {
        assertFalse(PrereqRules.wouldCycle(1L, 2L, edges(3L, 4L)));
    }

    @Test
    public void aDirectBackEdgeIsACycle() {
        // A already requires B, so B requiring A closes the loop.
        assertTrue(PrereqRules.wouldCycle(2L, 1L, edges(1L, 2L)));
    }

    @Test
    public void anIndirectBackEdgeIsACycle() {
        // A requires B requires C. C requiring A would make the three unreachable.
        assertTrue(PrereqRules.wouldCycle(3L, 1L, edges(1L, 2L, 2L, 3L)));
    }

    /** Two paths to the same ancestor is a diamond, not a loop — it must stay allowed. */
    @Test
    public void aDiamondIsNotACycle() {
        Map<Long, List<Long>> map = edges(4L, 2L, 4L, 3L, 2L, 1L, 3L, 1L);
        assertFalse(PrereqRules.wouldCycle(4L, 1L, map));
    }

    /**
     * A library that already holds a loop — an import from a build without this check — must
     * not hang the editor. The walk terminates and reports the loop rather than spinning.
     */
    @Test
    public void anExistingLoopInTheDataTerminates() {
        assertTrue(PrereqRules.wouldCycle(5L, 1L, edges(1L, 2L, 2L, 1L)));
    }

    @Test
    public void aPostWithNoPrerequisitesAcceptsAnything() {
        assertFalse(PrereqRules.wouldCycle(9L, 1L, edges(1L, 2L, 2L, 3L)));
    }

    /**
     * A whole batch is checked before anything is written, so an edge accepted earlier in the
     * batch has to constrain the ones after it — not just what was already in the database.
     */
    @Test
    public void anEdgeThatCyclesAgainstAnEarlierEdgeInTheSameBatchIsRefused() {
        Map<Long, List<Long>> map = new HashMap<>();
        assertFalse(PrereqRules.wouldCycle(1L, 2L, map));
        map.put(1L, new ArrayList<>(Arrays.asList(2L)));
        assertTrue(PrereqRules.wouldCycle(2L, 1L, map));
    }
}
