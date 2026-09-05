package com.cs426.learningmocha.ui.browse

import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType

/** One row of a branch's structure sheet: the node and how deep it sits under the branch. */
data class OutlineRow(val node: Node, val depth: Int)

/**
 * The order a branch is read in, and the outline behind its structure sheet.
 *
 * Three things could each claim to define the order, and this reconciles them rather than
 * picking one: the tree, because that is the shape the user filed the posts into and the order
 * they see in Browse; `nextPostId`, because a chained series is a sequence the user wrote down
 * by hand; and prerequisites, because those are real dependencies and reading around one makes
 * the post that needed it harder than it should be.
 *
 * So: the tree gives the baseline sequence, and both kinds of edge then pull posts earlier
 * where they have to. With no edges at all it degenerates to exactly tree order, which is what
 * most branches will do.
 */
object BranchReading {

    /**
     * The posts under [rootId], in the order to read them. Containers are walked through but
     * never returned — you read articles, not folders.
     *
     * @param requires post id → the ids it directly requires. Edges pointing outside this
     *   branch are ignored: a cross-branch dependency is real, but it cannot reorder a branch
     *   it is not part of, and honouring it would mean reading posts the user did not ask for.
     */
    fun order(rootId: Long, all: List<Node>, requires: Map<Long, List<Long>>): List<Node> {
        val base = outline(rootId, all)
            .map { it.node }
            .filter { it.type == NodeType.POST }
        if (base.size < 2) return base

        // Tree position doubles as the tie-break priority: when several posts are equally
        // unblocked, the one the user would have reached first in Browse wins.
        val rank = base.withIndex().associate { (index, node) -> node.id to index }
        val blockedBy = HashMap<Long, MutableSet<Long>>()
        val unlocks = HashMap<Long, MutableList<Long>>()

        fun edge(before: Long, after: Long) {
            if (before == after) return
            if (before !in rank || after !in rank) return
            if (blockedBy.getOrPut(after) { HashSet() }.add(before)) {
                unlocks.getOrPut(before) { ArrayList() }.add(after)
            }
        }

        for (node in base) {
            requires[node.id]?.forEach { required -> edge(required, node.id) }
            node.nextPostId?.let { next -> edge(node.id, next) }
        }

        // Kahn's algorithm, always taking the lowest-ranked ready post so the result is stable
        // and, with no edges, identical to `base`.
        val ready = base.filter { blockedBy[it.id].isNullOrEmpty() }.toMutableList()
        val remaining = blockedBy.mapValues { (_, v) -> v.toMutableSet() }.toMutableMap()
        val out = ArrayList<Node>(base.size)
        val placed = HashSet<Long>()
        while (ready.isNotEmpty()) {
            val next = ready.minByOrNull { rank.getValue(it.id) } ?: break
            ready.remove(next)
            if (!placed.add(next.id)) continue
            out.add(next)
            unlocks[next.id]?.forEach { blockedId ->
                val blockers = remaining[blockedId] ?: return@forEach
                blockers.remove(next.id)
                if (blockers.isEmpty()) {
                    base.firstOrNull { it.id == blockedId }?.let(ready::add)
                }
            }
        }
        // Whatever a cycle swallowed comes back in tree order. An imperfect order beats a
        // branch that silently reads short, and a library can arrive looped from an import
        // written before the prerequisite cycle check existed.
        base.filterNot { it.id in placed }.forEach(out::add)
        return out
    }

    /**
     * The branch's subtree, flattened depth-first with containers kept, for the structure
     * sheet. The branch itself is excluded — it is the sheet's title, not a row in it.
     *
     * Siblings come out containers-first then by title, matching what Browse shows under its
     * default sort, so the sheet looks like the folder the user just came from.
     */
    fun outline(rootId: Long, all: List<Node>): List<OutlineRow> {
        val childrenOf = all.groupBy { it.parentId }
        val out = ArrayList<OutlineRow>()
        val seen = HashSet<Long>()

        fun walk(parentId: Long, depth: Int) {
            val children = childrenOf[parentId].orEmpty()
                .sortedWith(
                    compareBy<Node> { if (it.type == NodeType.POST) 1 else 0 }
                        .thenBy { it.title.lowercase() },
                )
            for (child in children) {
                // A parent cycle from a corrupt row must not hang the sheet.
                if (!seen.add(child.id)) continue
                out.add(OutlineRow(child, depth))
                walk(child.id, depth + 1)
            }
        }

        walk(rootId, 0)
        return out
    }
}
