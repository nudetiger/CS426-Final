package com.cs426.learningmocha.util;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TreeRulesTest {

    @Test
    public void movingToRootNeverCycles() {
        Map<Long, Long> parents = new HashMap<>();
        parents.put(2L, 1L);
        assertFalse(TreeRules.wouldCreateCycle(2L, null, parents));
    }

    @Test
    public void cannotMoveUnderSelf() {
        assertTrue(TreeRules.wouldCreateCycle(1L, 1L, new HashMap<Long, Long>()));
    }

    @Test
    public void cannotMoveAncestorUnderDescendant() {
        Map<Long, Long> parents = new HashMap<>();
        parents.put(2L, 1L);
        parents.put(3L, 2L);
        assertTrue(TreeRules.wouldCreateCycle(1L, 3L, parents));
        assertFalse(TreeRules.wouldCreateCycle(3L, 1L, parents));
    }

    @Test
    public void siblingMoveIsSafe() {
        Map<Long, Long> parents = new HashMap<>();
        parents.put(2L, 1L);
        parents.put(3L, 1L);
        assertFalse(TreeRules.wouldCreateCycle(2L, 3L, parents));
    }
}
