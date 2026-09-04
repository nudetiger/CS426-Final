package com.cs426.learningmocha.ai.engine

import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KbIndexTest {

    @Test
    fun emptyKbProducesEmptyIndex() {
        assertEquals("", KbIndex.build(emptyList()))
    }

    @Test
    fun nestsChildrenAndOrdersBySiblingIndex() {
        val nodes = listOf(
            Node(id = 1, type = NodeType.BRANCH, title = "Backend", orderIndex = 1),
            Node(id = 2, type = NodeType.BRANCH, title = "Algorithms", orderIndex = 0),
            Node(
                id = 3,
                parentId = 1,
                type = NodeType.POST,
                title = "Spring Boot",
                status = LearningStatus.FINISHED,
            ),
        )
        val index = KbIndex.build(nodes)
        assertEquals(
            """
            - Algorithms (branch)
            - Backend (branch)
              - Spring Boot (post) [finished]
            """.trimIndent(),
            index.trimEnd(),
        )
    }

    @Test
    fun omitsStatusWhenNone() {
        val nodes = listOf(Node(id = 1, type = NodeType.POST, title = "Raft"))
        assertEquals("- Raft (post)", KbIndex.build(nodes).trimEnd())
    }

    // The index rides in every prompt, so the cap is what keeps token cost bounded.
    @Test
    fun capsOutputLength() {
        val nodes = (1..4000).map {
            Node(id = it.toLong(), type = NodeType.POST, title = "Post number $it", orderIndex = it)
        }
        assertTrue(KbIndex.build(nodes).length <= KbIndex.MAX_CHARS)
    }

    @Test
    fun ignoresChildrenOfMissingParents() {
        val orphan = Node(id = 5, parentId = 99, type = NodeType.POST, title = "Orphan")
        assertEquals("", KbIndex.build(listOf(orphan)))
    }
}
