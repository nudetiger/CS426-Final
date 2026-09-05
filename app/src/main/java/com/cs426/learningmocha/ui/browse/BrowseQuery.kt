package com.cs426.learningmocha.ui.browse

import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.ui.common.Readiness

/**
 * How the current folder is ordered. This replaced hand reordering: dragging rows only ever
 * described one folder, could not be undone, and told the user nothing about what they had.
 * A sort is re-derived from the data instead, so it keeps meaning something as the library grows.
 */
enum class BrowseSort(val labelRes: Int) {
    TITLE_ASC(R.string.browse_sort_title_asc),
    TITLE_DESC(R.string.browse_sort_title_desc),
    UPDATED_DESC(R.string.browse_sort_updated_desc),
    UPDATED_ASC(R.string.browse_sort_updated_asc),
    CREATED_DESC(R.string.browse_sort_created_desc),
    STATUS(R.string.browse_sort_status),
    READY_FIRST(R.string.browse_sort_ready),
    ;

    companion object {
        /** Falls back to the default rather than throwing on a value from an older build. */
        fun from(name: String?): BrowseSort =
            entries.firstOrNull { it.name == name } ?: TITLE_ASC
    }
}

/**
 * What the current folder shows. An empty set means "no opinion", which is what keeps the
 * default view unfiltered without a separate "all" flag per facet.
 *
 * [statuses] and [favoritesOnly] are asked of posts only. Branches and folders stay visible
 * so the tree is still walkable while a filter is on — hiding the folder that holds the
 * matches would make the filter impossible to use from anywhere but the root.
 */
data class BrowseFilter(
    val types: Set<NodeType> = emptySet(),
    val statuses: Set<LearningStatus> = emptySet(),
    val favoritesOnly: Boolean = false,
    /** Only posts whose every prerequisite has been started. See [Readiness]. */
    val readyOnly: Boolean = false,
) {
    val isActive: Boolean
        get() = types.isNotEmpty() || statuses.isNotEmpty() || favoritesOnly || readyOnly

    /** How many facets are narrowing the list, for the badge on the Filter button. */
    val activeCount: Int
        get() = (if (types.isNotEmpty()) 1 else 0) +
            (if (statuses.isNotEmpty()) 1 else 0) +
            (if (favoritesOnly) 1 else 0) +
            (if (readyOnly) 1 else 0)

    fun matches(node: Node, readiness: Map<Long, Readiness>): Boolean {
        if (types.isNotEmpty() && node.type !in types) return false
        if (node.type != NodeType.POST) return true
        if (statuses.isNotEmpty() && node.status !in statuses) return false
        if (favoritesOnly && !node.favorite) return false
        // A post with no entry has no prerequisites, which is ready — never blocked.
        if (readyOnly && readiness[node.id]?.isReady == false) return false
        return true
    }
}

/** Applies a [BrowseFilter] and a [BrowseSort] to one folder's children. */
object BrowseQuery {

    /**
     * Containers come before posts whatever the sort is, the way every file browser groups
     * them: the list is a place to navigate first and a list of articles second.
     */
    fun apply(
        children: List<Node>,
        filter: BrowseFilter,
        sort: BrowseSort,
        readiness: Map<Long, Readiness> = emptyMap(),
    ): List<Node> = children
        .filter { filter.matches(it, readiness) }
        .sortedWith(comparator(sort, readiness))

    private fun comparator(sort: BrowseSort, readiness: Map<Long, Readiness>): Comparator<Node> {
        val containersFirst = compareBy<Node> { if (it.type == NodeType.POST) 1 else 0 }
        val within: Comparator<Node> = when (sort) {
            BrowseSort.TITLE_ASC -> compareBy { it.title.lowercase() }
            BrowseSort.TITLE_DESC -> compareByDescending { it.title.lowercase() }
            BrowseSort.UPDATED_DESC -> compareByDescending { it.updatedAt }
            BrowseSort.UPDATED_ASC -> compareBy { it.updatedAt }
            BrowseSort.CREATED_DESC -> compareByDescending { it.createdAt }
            // Reading order, not enum order: what is untouched sorts first because that is
            // what the user still has to pick up.
            BrowseSort.STATUS -> compareBy { statusRank(it.status) }
            // What you could pick up right now, first — then whatever is closest to that.
            // The percentage is the tie-break rather than the whole sort so a post one
            // prerequisite short still ranks above one that needs four.
            BrowseSort.READY_FIRST -> compareBy(
                { if (readiness[it.id]?.isReady != false) 0 else 1 },
                { -(readiness[it.id]?.percent ?: 100) },
            )
        }
        // Title breaks every tie, so two posts updated in the same millisecond keep a stable
        // order between refreshes instead of swapping places under the user.
        return containersFirst.then(within).thenBy { it.title.lowercase() }
    }

    private fun statusRank(status: LearningStatus): Int = when (status) {
        LearningStatus.NONE -> 0
        LearningStatus.READING -> 1
        LearningStatus.IN_PROGRESS -> 2
        LearningStatus.FINISHED -> 3
    }
}
