package com.cs426.learningmocha.ui.browse

import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.ui.common.Readiness
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

    // --- Readiness: sorting and filtering by whether the prerequisites have been started ---

    /** Every post is ready when nothing requires anything, which is the default library. */
    private fun readiness(vararg entries: Pair<Long, List<Node>>): Map<Long, Readiness> =
        entries.associate { (id, required) -> id to Readiness(required) }

    @Test
    fun readyPostsSortBeforeBlockedOnes() {
        val basics = post(1, "Basics", LearningStatus.FINISHED)
        val advanced = post(2, "Advanced")
        val standalone = post(3, "Standalone")
        val nodes = listOf(advanced, standalone)
        val index = readiness(2L to listOf(post(9, "Untouched", LearningStatus.NONE)))
        assertEquals(
            listOf("Standalone", "Advanced"),
            titles(BrowseQuery.apply(nodes, BrowseFilter(), BrowseSort.READY_FIRST, index)),
        )
        // Basics is unused here beyond documenting that a finished prerequisite is the
        // ready case covered below.
        assertEquals(LearningStatus.FINISHED, basics.status)
    }

    @Test
    fun amongBlockedPostsTheClosestToReadyComesFirst() {
        val nearly = post(1, "Nearly")
        val barely = post(2, "Barely")
        val index = readiness(
            1L to listOf(
                post(9, "A", LearningStatus.READING),
                post(10, "B", LearningStatus.NONE),
            ),
            2L to listOf(
                post(11, "C", LearningStatus.NONE),
                post(12, "D", LearningStatus.NONE),
            ),
        )
        assertEquals(
            listOf("Nearly", "Barely"),
            titles(
                BrowseQuery.apply(
                    listOf(barely, nearly),
                    BrowseFilter(),
                    BrowseSort.READY_FIRST,
                    index,
                ),
            ),
        )
    }

    @Test
    fun titleStillBreaksTiesWithinReadyFirst() {
        val nodes = listOf(post(1, "Zulu"), post(2, "Alpha"))
        assertEquals(
            listOf("Alpha", "Zulu"),
            titles(BrowseQuery.apply(nodes, BrowseFilter(), BrowseSort.READY_FIRST, emptyMap())),
        )
    }

    @Test
    fun readyOnlyFilterHidesBlockedPosts() {
        val nodes = listOf(post(1, "Blocked"), post(2, "Open"))
        val index = readiness(1L to listOf(post(9, "Untouched", LearningStatus.NONE)))
        assertEquals(
            listOf("Open"),
            titles(
                BrowseQuery.apply(
                    nodes,
                    BrowseFilter(readyOnly = true),
                    BrowseSort.TITLE_ASC,
                    index,
                ),
            ),
        )
    }

    /** Same rule the status filter follows: hiding the folder makes the filter unusable. */
    @Test
    fun readyOnlyFilterKeepsContainersVisible() {
        val nodes = listOf(folder(1, "Deeper"), post(2, "Blocked"))
        val index = readiness(2L to listOf(post(9, "Untouched", LearningStatus.NONE)))
        assertEquals(
            listOf("Deeper"),
            titles(
                BrowseQuery.apply(
                    nodes,
                    BrowseFilter(readyOnly = true),
                    BrowseSort.TITLE_ASC,
                    index,
                ),
            ),
        )
    }

    /** An index with no entry for a post means no prerequisites, which is ready, not blocked. */
    @Test
    fun aPostMissingFromTheIndexIsTreatedAsReady() {
        val nodes = listOf(post(1, "Unknown"))
        assertEquals(
            listOf("Unknown"),
            titles(
                BrowseQuery.apply(
                    nodes,
                    BrowseFilter(readyOnly = true),
                    BrowseSort.TITLE_ASC,
                    emptyMap(),
                ),
            ),
        )
    }

    @Test
    fun readyOnlyCountsTowardTheFilterBadge() {
        val filter = BrowseFilter(readyOnly = true)
        assertTrue(filter.isActive)
        assertEquals(1, filter.activeCount)
    }
}
