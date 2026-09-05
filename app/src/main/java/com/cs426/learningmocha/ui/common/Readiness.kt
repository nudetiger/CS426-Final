package com.cs426.learningmocha.ui.common

import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType

/**
 * How far through a post's prerequisites the reader is — the bar at the top of a post, and the
 * thing Browse sorts and filters on.
 *
 * Direct prerequisites only. If Consensus requires Raft and Raft requires Networking, opening
 * Consensus says one thing about Raft; Raft's own bar then says the thing about Networking.
 * The transitive closure was the alternative, and it turns a six-deep chain into a wall of six
 * rows on every post in it — truthful and unusable. Composing one hop at a time gets the reader
 * to the same place, one screen at a time.
 *
 * "Ready" means every prerequisite has at least been opened, not finished. A library where
 * nothing has been ticked off yet would otherwise report itself as entirely blocked.
 */
data class Readiness(val required: List<Node>) {

    val total: Int get() = required.size

    val started: Int get() = required.count { it.status != LearningStatus.NONE }

    val finished: Int get() = required.count { it.status == LearningStatus.FINISHED }

    /** Nothing required is ready by definition, which is what keeps most posts unblocked. */
    val isReady: Boolean get() = started == total

    /** Whole percent started. 100 when nothing is required, so it agrees with [isReady]. */
    val percent: Int
        get() = if (total == 0) 100 else Math.round(started * 100f / total)

    /**
     * The same breakdown [StatusMeterBinder] draws over a folder in Browse, so the bar on a post
     * is recognisably the same object — and "amber means reading" only has to be learned once.
     */
    val stats: SubtreeStats
        get() = SubtreeStats(
            total = total,
            none = required.count { it.status == LearningStatus.NONE },
            reading = required.count { it.status == LearningStatus.READING },
            inProgress = required.count { it.status == LearningStatus.IN_PROGRESS },
            finished = finished,
        )

    companion object {
        /**
         * Readiness for every post in [nodes], in one pass, so Browse can sort and filter a
         * folder without a query per row.
         *
         * @param requires post id → the ids it directly requires. Ids with no surviving post
         *   are dropped: a prerequisite whose post was deleted is not a thing left to read.
         */
        fun index(nodes: List<Node>, requires: Map<Long, List<Long>>): Map<Long, Readiness> {
            val byId = nodes.associateBy { it.id }
            return nodes.asSequence()
                .filter { it.type == NodeType.POST }
                .associate { post ->
                    val needed = requires[post.id].orEmpty()
                        .mapNotNull { byId[it] }
                        .sortedBy { it.title.lowercase() }
                    post.id to Readiness(needed)
                }
        }
    }
}
