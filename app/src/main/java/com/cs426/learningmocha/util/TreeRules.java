package com.cs426.learningmocha.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure tree invariants used before a move is written.
 * Framework-free so it can be unit-tested on the JVM.
 */
public final class TreeRules {

    private TreeRules() {}

    /**
     * @param parentById child id → parent id; root nodes are omitted (no mapping)
     * @return true if placing {@code movingId} under {@code newParentId} would cycle
     */
    public static boolean wouldCreateCycle(
            long movingId,
            Long newParentId,
            Map<Long, Long> parentById
    ) {
        if (newParentId == null) {
            return false;
        }
        if (newParentId == movingId) {
            return true;
        }
        Long cursor = newParentId;
        Set<Long> seen = new HashSet<>();
        while (cursor != null) {
            if (cursor == movingId) {
                return true;
            }
            if (!seen.add(cursor)) {
                return true;
            }
            cursor = parentById.get(cursor);
        }
        return false;
    }
}
