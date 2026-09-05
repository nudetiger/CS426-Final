package com.cs426.learningmocha.ui.common

import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtreeStatsTest {

    private fun branch(id: Long, title: String, parentId: Long? = null) =
        Node(id = id, parentId = parentId, type = NodeType.BRANCH, title = title)

    private fun folder(id: Long, title: String, parentId: Long?) =
        Node(id = id, parentId = parentId, type = NodeType.FOLDER, title = title)

    private fun post(
        id: Long,
        title: String,
        parentId: Long?,
        status: LearningStatus = LearningStatus.NONE,
    ) = Node(id = id, parentId = parentId, type = NodeType.POST, title = title, status = status)

    @Test
    fun anEmptyLibraryRollsUpToNothing() {
        val stats = SubtreeStats.index(emptyList())
        assertTrue(stats[SubtreeStats.ROOT]!!.isEmpty)
    }

    // The whole reason it walks the subtree: a branch whose posts sit two folders down is
    // not an empty branch.
    @Test
    fun countsPostsAnywhereBeneathANode() {
        val nodes = listOf(
            branch(1, "Root"),
            folder(2, "Mid", 1),
            post(3, "Deep", 2, LearningStatus.FINISHED),
            post(4, "Shallow", 1, LearningStatus.READING),
        )
        val stats = SubtreeStats.index(nodes)
        assertEquals(2, stats[1L]!!.total)
        assertEquals(1, stats[1L]!!.finished)
        assertEquals(1, stats[1L]!!.reading)
        assertEquals(1, stats[2L]!!.total)
    }

    @Test
    fun aPostCountsItselfAndItsSubPosts() {
        val nodes = listOf(
            post(1, "Parent", null, LearningStatus.READING),
            post(2, "Child", 1, LearningStatus.FINISHED),
        )
        val stats = SubtreeStats.index(nodes)
        assertEquals(2, stats[1L]!!.total)
        assertEquals(1, stats[1L]!!.reading)
        assertEquals(1, stats[1L]!!.finished)
        assertEquals(1, stats[2L]!!.total)
    }

    @Test
    fun theRootKeyHoldsTheWholeLibrary() {
        val nodes = listOf(
            branch(1, "A"),
            post(2, "One", 1, LearningStatus.NONE),
            branch(3, "B"),
            post(4, "Two", 3, LearningStatus.IN_PROGRESS),
        )
        val stats = SubtreeStats.index(nodes)[SubtreeStats.ROOT]!!
        assertEquals(2, stats.total)
        assertEquals(1, stats.none)
        assertEquals(1, stats.inProgress)
    }

    @Test
    fun percentagesAreOfTheSubtreeTotal() {
        val nodes = listOf(
            branch(1, "Root"),
            post(2, "a", 1, LearningStatus.FINISHED),
            post(3, "b", 1, LearningStatus.FINISHED),
            post(4, "c", 1, LearningStatus.NONE),
            post(5, "d", 1, LearningStatus.NONE),
        )
        val stats = SubtreeStats.index(nodes)[1L]!!
        assertEquals(50, stats.percent(LearningStatus.FINISHED))
        assertEquals(50, stats.percent(LearningStatus.NONE))
        assertEquals(0, stats.percent(LearningStatus.READING))
    }

    @Test
    fun percentagesAreZeroRatherThanUndefinedWhenEmpty() {
        assertEquals(0, SubtreeStats().percent(LearningStatus.READING))
    }

    // A corrupt parent link must not hang the screen that is trying to summarise it.
    @Test
    fun aParentCycleTerminates() {
        val nodes = listOf(
            Node(id = 1, parentId = 2, type = NodeType.FOLDER, title = "A"),
            Node(id = 2, parentId = 1, type = NodeType.FOLDER, title = "B"),
            branch(3, "Reachable"),
            post(4, "Counted", 3),
        )
        val stats = SubtreeStats.index(nodes)
        assertEquals(1, stats[SubtreeStats.ROOT]!!.total)
    }
}
