package com.cs426.learningmocha.ui.browse

import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseQueryTest {

    private fun post(
        id: Long,
        title: String,
        status: LearningStatus = LearningStatus.NONE,
        favorite: Boolean = false,
        updatedAt: Long = id,
        createdAt: Long = id,
    ) = Node(
        id = id,
        type = NodeType.POST,
        title = title,
        status = status,
        favorite = favorite,
        updatedAt = updatedAt,
        createdAt = createdAt,
    )

    private fun folder(id: Long, title: String) =
        Node(id = id, type = NodeType.FOLDER, title = title)

    private fun titles(nodes: List<Node>) = nodes.map { it.title }

    // Browse is a place to navigate before it is a list of articles, so containers lead.
    @Test
    fun containersSortBeforePostsWhateverTheSort() {
        val nodes = listOf(post(1, "Alpha"), folder(2, "Zulu"))
        for (sort in BrowseSort.entries) {
            assertEquals(
                "sort $sort put a post before a folder",
                listOf("Zulu", "Alpha"),
                titles(BrowseQuery.apply(nodes, BrowseFilter(), sort)),
            )
        }
    }

    @Test
    fun sortsByTitleBothWays() {
        val nodes = listOf(post(1, "beta"), post(2, "Alpha"), post(3, "Gamma"))
        assertEquals(
            listOf("Alpha", "beta", "Gamma"),
            titles(BrowseQuery.apply(nodes, BrowseFilter(), BrowseSort.TITLE_ASC)),
        )
        assertEquals(
            listOf("Gamma", "beta", "Alpha"),
            titles(BrowseQuery.apply(nodes, BrowseFilter(), BrowseSort.TITLE_DESC)),
        )
    }

    @Test
    fun sortsByUpdatedTime() {
        val nodes = listOf(
            post(1, "Old", updatedAt = 100),
            post(2, "New", updatedAt = 300),
            post(3, "Mid", updatedAt = 200),
        )
        assertEquals(
            listOf("New", "Mid", "Old"),
            titles(BrowseQuery.apply(nodes, BrowseFilter(), BrowseSort.UPDATED_DESC)),
        )
        assertEquals(
            listOf("Old", "Mid", "New"),
            titles(BrowseQuery.apply(nodes, BrowseFilter(), BrowseSort.UPDATED_ASC)),
        )
    }

    // Untouched first: status order is the order of work still to do, not the enum order.
    @Test
    fun sortsByStatusFromUntouchedToDone() {
        val nodes = listOf(
            post(1, "Done", LearningStatus.FINISHED),
            post(2, "Untouched", LearningStatus.NONE),
            post(3, "Halfway", LearningStatus.IN_PROGRESS),
            post(4, "Started", LearningStatus.READING),
        )
        assertEquals(
            listOf("Untouched", "Started", "Halfway", "Done"),
            titles(BrowseQuery.apply(nodes, BrowseFilter(), BrowseSort.STATUS)),
        )
    }

    // Two posts written in the same millisecond must not swap places between refreshes.
    @Test
    fun breaksTiesByTitle() {
        val nodes = listOf(post(1, "Beta", updatedAt = 5), post(2, "Alpha", updatedAt = 5))
        assertEquals(
            listOf("Alpha", "Beta"),
            titles(BrowseQuery.apply(nodes, BrowseFilter(), BrowseSort.UPDATED_DESC)),
        )
    }

    @Test
    fun anEmptyFilterKeepsEverything() {
        val nodes = listOf(post(1, "A"), folder(2, "B"))
        assertEquals(2, BrowseQuery.apply(nodes, BrowseFilter(), BrowseSort.TITLE_ASC).size)
        assertFalse(BrowseFilter().isActive)
    }

    @Test
    fun filtersPostsByStatus() {
        val nodes = listOf(
            post(1, "Reading", LearningStatus.READING),
            post(2, "Done", LearningStatus.FINISHED),
        )
        val filter = BrowseFilter(statuses = setOf(LearningStatus.READING))
        assertEquals(
            listOf("Reading"),
            titles(BrowseQuery.apply(nodes, filter, BrowseSort.TITLE_ASC)),
        )
    }

    // The tree still has to be walkable while a status filter is on, or the filter can only
    // ever be used from the folder the matches already happen to be in.
    @Test
    fun aStatusFilterLeavesContainersVisible() {
        val nodes = listOf(folder(1, "Folder"), post(2, "Done", LearningStatus.FINISHED))
        val filter = BrowseFilter(statuses = setOf(LearningStatus.READING))
        assertEquals(
            listOf("Folder"),
            titles(BrowseQuery.apply(nodes, filter, BrowseSort.TITLE_ASC)),
        )
    }

    // A type filter is the exception: asking for posts only means the folders go too.
    @Test
    fun aTypeFilterCanHideContainers() {
        val nodes = listOf(folder(1, "Folder"), post(2, "Post"))
        val filter = BrowseFilter(types = setOf(NodeType.POST))
        assertEquals(
            listOf("Post"),
            titles(BrowseQuery.apply(nodes, filter, BrowseSort.TITLE_ASC)),
        )
    }

    @Test
    fun filtersToFavorites() {
        val nodes = listOf(post(1, "Starred", favorite = true), post(2, "Plain"))
        val filter = BrowseFilter(favoritesOnly = true)
        assertEquals(
            listOf("Starred"),
            titles(BrowseQuery.apply(nodes, filter, BrowseSort.TITLE_ASC)),
        )
    }

    @Test
    fun countsTheFacetsThatAreNarrowingTheList() {
        val filter = BrowseFilter(
            types = setOf(NodeType.POST),
            statuses = setOf(LearningStatus.READING),
            favoritesOnly = true,
        )
        assertTrue(filter.isActive)
        assertEquals(3, filter.activeCount)
    }

    @Test
    fun anUnknownStoredSortFallsBackToTheDefault() {
        assertEquals(BrowseSort.TITLE_ASC, BrowseSort.from("SOMETHING_ELSE"))
        assertEquals(BrowseSort.TITLE_ASC, BrowseSort.from(null))
        assertEquals(BrowseSort.STATUS, BrowseSort.from("STATUS"))
    }
}
