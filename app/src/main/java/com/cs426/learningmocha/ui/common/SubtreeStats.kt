package com.cs426.learningmocha.ui.common

import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType

/**
 * How many posts live under a node, broken down by learning status — the numbers behind the
 * meter a folder shows above its contents.
 *
 * Counted over the whole subtree rather than direct children only: a branch whose posts all sit
 * two folders down would otherwise report itself as empty, which is exactly when the summary is
 * most worth having.
 */
data class SubtreeStats(
    val total: Int = 0,
    val none: Int = 0,
    val reading: Int = 0,
    val inProgress: Int = 0,
    val finished: Int = 0,
) {
    val isEmpty: Boolean get() = total == 0

    fun count(status: LearningStatus): Int = when (status) {
        LearningStatus.NONE -> none
        LearningStatus.READING -> reading
        LearningStatus.IN_PROGRESS -> inProgress
        LearningStatus.FINISHED -> finished
    }

    /** Whole percent of [total], for the caption under the meter. Zero when there is nothing. */
    fun percent(status: LearningStatus): Int =
        if (total == 0) 0 else Math.round(count(status) * 100f / total)

    operator fun plus(other: SubtreeStats) = SubtreeStats(
        total = total + other.total,
        none = none + other.none,
        reading = reading + other.reading,
        inProgress = inProgress + other.inProgress,
        finished = finished + other.finished,
    )

    companion object {
        /** Key for the library root, which has no node id of its own. */
        const val ROOT = -1L

        private val EMPTY = SubtreeStats()

        private fun of(post: Node) = SubtreeStats(
            total = 1,
            none = if (post.status == LearningStatus.NONE) 1 else 0,
            reading = if (post.status == LearningStatus.READING) 1 else 0,
            inProgress = if (post.status == LearningStatus.IN_PROGRESS) 1 else 0,
            finished = if (post.status == LearningStatus.FINISHED) 1 else 0,
        )

        /**
         * Rolls the whole library up in one pass, so Browse can label every row it is about to
         * draw without running a query per row.
         *
         * @return stats for each subtree, keyed by node id, plus the library total under [ROOT].
         *   A post counts itself as well as any sub-posts beneath it.
         */
        fun index(nodes: List<Node>): Map<Long, SubtreeStats> {
            val childrenOf = HashMap<Long, MutableList<Node>>()
            val roots = ArrayList<Node>()
            for (node in nodes) {
                val parent = node.parentId
                if (parent == null) {
                    roots.add(node)
                } else {
                    childrenOf.getOrPut(parent) { ArrayList() }.add(node)
                }
            }
            val out = HashMap<Long, SubtreeStats>(nodes.size + 1)
            // Iterative post-order. A deep library must not depend on the JVM stack, and the
            // seen-set means a parent cycle from a corrupt row cannot hang the screen.
            val seen = HashSet<Long>()
            for (root in roots) {
                val stack = ArrayList<Pair<Node, Boolean>>()
                stack.add(root to false)
                while (stack.isNotEmpty()) {
                    val (node, expanded) = stack.removeAt(stack.lastIndex)
                    if (expanded) {
                        var sum = if (node.type == NodeType.POST) of(node) else EMPTY
                        childrenOf[node.id]?.forEach { child -> sum += out[child.id] ?: EMPTY }
                        out[node.id] = sum
                    } else {
                        if (!seen.add(node.id)) continue
                        stack.add(node to true)
                        childrenOf[node.id]?.forEach { child -> stack.add(child to false) }
                    }
                }
            }
            out[ROOT] = roots.fold(EMPTY) { acc, node -> acc + (out[node.id] ?: EMPTY) }
            return out
        }
    }
}
