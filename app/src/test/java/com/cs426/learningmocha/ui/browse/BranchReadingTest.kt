package com.cs426.learningmocha.ui.browse

import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchReadingTest {

    private fun post(
        id: Long,
        title: String,
        parentId: Long? = 1L,
        nextPostId: Long? = null,
    ) = Node(
        id = id,
        parentId = parentId,
        type = NodeType.POST,
        title = title,
        nextPostId = nextPostId,
    )

    private fun folder(id: Long, title: String, parentId: Long? = 1L) =
        Node(id = id, parentId = parentId, type = NodeType.FOLDER, title = title)

    private fun branch(id: Long, title: String) =
        Node(id = id, parentId = null, type = NodeType.BRANCH, title = title)

    private fun titles(nodes: List<Node>) = nodes.map { it.title }

    @Test
    fun aBranchWithNoPrerequisitesReadsInTreeOrder() {
        val all = listOf(branch(1, "Algo"), post(2, "Beta"), post(3, "Alpha"))
        assertEquals(
            listOf("Alpha", "Beta"),
            titles(BranchReading.order(1L, all, emptyMap())),
        )
    }

    @Test
    fun theBranchItselfIsNotPartOfItsOwnReadingOrder() {
        val all = listOf(branch(1, "Algo"), post(2, "Alpha"))
        assertEquals(listOf("Alpha"), titles(BranchReading.order(1L, all, emptyMap())))
    }

    @Test
    fun containersAreNotReadButTheirPostsAre() {
        val all = listOf(
            branch(1, "Algo"),
            folder(2, "Graphs"),
            post(3, "BFS", parentId = 2L),
            post(4, "Sorting"),
        )
        assertEquals(
            listOf("BFS", "Sorting"),
            titles(BranchReading.order(1L, all, emptyMap())),
        )
    }

    @Test
    fun subPostsFollowTheirParentPost() {
        val all = listOf(
            branch(1, "Algo"),
            post(2, "Trees"),
            post(3, "AVL", parentId = 2L),
        )
        assertEquals(listOf("Trees", "AVL"), titles(BranchReading.order(1L, all, emptyMap())))
    }

    @Test
    fun aPrerequisiteInsideTheBranchIsReadFirst() {
        // Tree order alone would give Advanced, Basics — alphabetical.
        val all = listOf(branch(1, "Algo"), post(2, "Advanced"), post(3, "Basics"))
        val requires = mapOf(2L to listOf(3L))
        assertEquals(
            listOf("Basics", "Advanced"),
            titles(BranchReading.order(1L, all, requires)),
        )
    }

    /** Cross-branch dependencies are real, but they cannot reorder a branch they are not in. */
    @Test
    fun aPrerequisiteOutsideTheBranchIsIgnored() {
        val all = listOf(
            branch(1, "Algo"),
            post(2, "Advanced"),
            branch(8, "Other"),
            post(9, "Elsewhere", parentId = 8L),
        )
        val requires = mapOf(2L to listOf(9L))
        assertEquals(listOf("Advanced"), titles(BranchReading.order(1L, all, requires)))
    }

    /**
     * nextPostId is the sequence the user already chained by hand. Reading order has to agree
     * with it, or "read this branch" would contradict the reader's own "next post" card.
     */
    @Test
    fun aNextPostChainIsFollowed() {
        val all = listOf(
            branch(1, "Alphabet"),
            post(2, "Letter C", nextPostId = null),
            post(3, "Letter A", nextPostId = 4L),
            post(4, "Letter B", nextPostId = 2L),
        )
        assertEquals(
            listOf("Letter A", "Letter B", "Letter C"),
            titles(BranchReading.order(1L, all, emptyMap())),
        )
    }

    /**
     * A loop must not swallow the posts caught in it. Data can arrive looped from an import
     * written before the cycle check existed, and a branch that silently reads short is worse
     * than one that reads in an imperfect order.
     */
    @Test
    fun aCycleFallsBackToTreeOrderInsteadOfDroppingPosts() {
        val all = listOf(branch(1, "Algo"), post(2, "Alpha"), post(3, "Beta"))
        val requires = mapOf(2L to listOf(3L), 3L to listOf(2L))
        val order = BranchReading.order(1L, all, requires)
        assertEquals(2, order.size)
        assertTrue(titles(order).containsAll(listOf("Alpha", "Beta")))
    }

    @Test
    fun anEmptyBranchReadsAsEmpty() {
        val all = listOf(branch(1, "Algo"))
        assertTrue(BranchReading.order(1L, all, emptyMap()).isEmpty())
    }

    // --- Outline: the structure sheet behind the ⊞ button ---

    @Test
    fun theOutlineKeepsContainersAndTheirDepth() {
        val all = listOf(
            branch(1, "Algo"),
            folder(2, "Graphs"),
            post(3, "BFS", parentId = 2L),
            post(4, "Sorting"),
        )
        val rows = BranchReading.outline(1L, all)
        assertEquals(listOf("Graphs", "BFS", "Sorting"), rows.map { it.node.title })
        assertEquals(listOf(0, 1, 0), rows.map { it.depth })
    }

    @Test
    fun theOutlineExcludesTheBranchItself() {
        val all = listOf(branch(1, "Algo"), post(2, "Alpha"))
        assertEquals(listOf("Alpha"), BranchReading.outline(1L, all).map { it.node.title })
    }
}
