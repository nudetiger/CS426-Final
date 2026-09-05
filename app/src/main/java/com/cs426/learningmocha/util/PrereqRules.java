package com.cs426.learningmocha.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure invariants for the prerequisite graph, checked before an edge is written.
 *
 * Prerequisites form a DAG, not a tree: a post may require several others, and one post may be
 * required by many. That is why this cannot reuse {@link TreeRules}, which only ever has one
 * parent to follow. A cycle here is worse than a cycle in the tree — every post in the loop
 * becomes permanently unreadable, since none of them can have its prerequisites met first.
 *
 * Framework-free so it can be unit-tested on the JVM.
 */
public final class PrereqRules {

    private PrereqRules() {}

    /**
     * @param postId     the post that would gain a prerequisite
     * @param requiresId the post it would come to depend on
     * @param requires   post id → everything it already requires directly; absent means none
     * @return true if the edge would close a loop, itself included
     */
    public static boolean wouldCycle(long postId, long requiresId, Map<Long, List<Long>> requires) {
        if (postId == requiresId) {
            return true;
        }
        // Walk out from the new prerequisite. If the post we are adding it to is reachable, it
        // already sits upstream, and pointing back at it closes the loop.
        Deque<Long> pending = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        pending.push(requiresId);
        while (!pending.isEmpty()) {
            long current = pending.pop();
            if (current == postId) {
                return true;
            }
            // A loop already in the data — an import from a build without this check — must
            // terminate the walk rather than spin. Reporting it as a cycle is also honest:
            // an edge into a broken component cannot be untangled by accepting one more.
            if (!seen.add(current)) {
                return true;
            }
            List<Long> next = requires.get(current);
            if (next != null) {
                for (Long id : next) {
                    if (id != null) {
                        pending.push(id);
                    }
                }
            }
        }
        return false;
    }

}
