package com.cs426.learningmocha.ui.common

import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessTest {

    private fun post(id: Long, title: String, status: LearningStatus = LearningStatus.NONE) =
        Node(id = id, type = NodeType.POST, title = title, status = status)

    @Test
    fun `a post with no prerequisites is ready`() {
        val readiness = Readiness(emptyList())
        assertTrue(readiness.isReady)
        assertEquals(0, readiness.total)
        assertEquals(100, readiness.percent)
    }

    /**
     * "Ready" is the user's own definition: every prerequisite at least opened. Requiring them
     * all *finished* would make a library where nothing is ticked off look entirely blocked.
     */
    @Test
    fun `a post is ready once every prerequisite has been started`() {
        val readiness = Readiness(
            listOf(
                post(1, "Raft", LearningStatus.READING),
                post(2, "Networking", LearningStatus.FINISHED),
            ),
        )
        assertTrue(readiness.isReady)
        assertEquals(2, readiness.started)
    }

    @Test
    fun `one untouched prerequisite blocks the post`() {
        val readiness = Readiness(
            listOf(
                post(1, "Raft", LearningStatus.FINISHED),
                post(2, "Networking", LearningStatus.NONE),
            ),
        )
        assertFalse(readiness.isReady)
        assertEquals(1, readiness.started)
        assertEquals(50, readiness.percent)
    }

    @Test
    fun `finished is counted apart from started`() {
        val readiness = Readiness(
            listOf(
                post(1, "Raft", LearningStatus.FINISHED),
                post(2, "Networking", LearningStatus.READING),
                post(3, "Consensus", LearningStatus.IN_PROGRESS),
            ),
        )
        assertEquals(3, readiness.started)
        assertEquals(1, readiness.finished)
    }

    /** The bar is the same widget Browse draws over a folder, so it needs the same breakdown. */
    @Test
    fun `the meter stats break the prerequisites down by status`() {
        val stats = Readiness(
            listOf(
                post(1, "A", LearningStatus.NONE),
                post(2, "B", LearningStatus.READING),
                post(3, "C", LearningStatus.IN_PROGRESS),
                post(4, "D", LearningStatus.FINISHED),
            ),
        ).stats
        assertEquals(4, stats.total)
        assertEquals(1, stats.none)
        assertEquals(1, stats.reading)
        assertEquals(1, stats.inProgress)
        assertEquals(1, stats.finished)
    }

    @Test
    fun `index resolves ids to posts in title order`() {
        val nodes = listOf(post(1, "Consensus"), post(2, "Raft"), post(3, "Networking"))
        val index = Readiness.index(nodes, mapOf(1L to listOf(2L, 3L)))
        assertEquals(listOf("Networking", "Raft"), index.getValue(1L).required.map { it.title })
    }

    /** A prerequisite whose post was deleted must not be counted as forever-unstarted. */
    @Test
    fun `index drops prerequisite ids that no longer exist`() {
        val nodes = listOf(post(1, "Consensus"), post(2, "Raft", LearningStatus.FINISHED))
        val index = Readiness.index(nodes, mapOf(1L to listOf(2L, 99L)))
        assertEquals(1, index.getValue(1L).total)
        assertTrue(index.getValue(1L).isReady)
    }

    @Test
    fun `index gives a post with no prerequisites a ready entry`() {
        val index = Readiness.index(listOf(post(1, "Consensus")), emptyMap())
        assertTrue(index.getValue(1L).isReady)
        assertEquals(0, index.getValue(1L).total)
    }

    /** Containers have no prerequisites of their own; only posts appear in the index. */
    @Test
    fun `index skips containers`() {
        val nodes = listOf(
            post(1, "Consensus"),
            Node(id = 2, type = NodeType.FOLDER, title = "Distributed"),
        )
        val index = Readiness.index(nodes, emptyMap())
        assertEquals(setOf(1L), index.keys)
    }
}
