package com.cs426.learningmocha.ai.engine

import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType

object KbIndex {
    const val MAX_CHARS = 4000

    fun build(nodes: List<Node>): String {
        val byParent = nodes.groupBy { it.parentId }
        val sb = StringBuilder()
        fun walk(parentId: Long?, depth: Int) {
            val kids = byParent[parentId].orEmpty().sortedBy { it.orderIndex }
            for (node in kids) {
                if (sb.length >= MAX_CHARS) return
                sb.append("  ".repeat(depth))
                    .append("- ")
                    .append(node.title)
                    .append(" (")
                    .append(node.type.name.lowercase())
                    .append(")")
                if (node.type == NodeType.POST && node.status != LearningStatus.NONE) {
                    sb.append(" [").append(node.status.name.lowercase()).append("]")
                }
                sb.append('\n')
                walk(node.id, depth + 1)
            }
        }
        walk(null, 0)
        return if (sb.length <= MAX_CHARS) sb.toString() else sb.substring(0, MAX_CHARS)
    }
}
