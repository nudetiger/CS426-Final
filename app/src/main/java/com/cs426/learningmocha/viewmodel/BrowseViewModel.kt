package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.ui.browse.BrowseFilter
import com.cs426.learningmocha.ui.browse.BrowseQuery
import com.cs426.learningmocha.ui.browse.BrowseSort
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.SubtreeStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BrowseUiState(
    val listState: ListState = ListState.LOADING,
    val parentId: Long? = null,
    val title: String = "",
    val breadcrumbs: List<Node> = emptyList(),
    val children: List<Node> = emptyList(),
    /** Subtree post counts by status, keyed by node id — the meter and the row captions. */
    val stats: Map<Long, SubtreeStats> = emptyMap(),
    /** Direct child counts, so a row can say a post has sub-posts without walking the tree. */
    val childCounts: Map<Long, Int> = emptyMap(),
    val sort: BrowseSort = BrowseSort.TITLE_ASC,
    val filter: BrowseFilter = BrowseFilter(),
    /** How many children the folder holds before filtering, for the "N hidden" hint. */
    val unfilteredCount: Int = 0,
    val errorMessage: String? = null,
) {
    /** Stats for the folder being shown; the library total at the root. */
    val currentStats: SubtreeStats
        get() = stats[parentId ?: SubtreeStats.ROOT] ?: SubtreeStats()
}

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp

    private val parentId = MutableStateFlow(readSavedParent())
    private val sort = MutableStateFlow(BrowseSort.from(app.settings.browseSort))
    private val filter = MutableStateFlow(BrowseFilter())

    private val messages = Channel<String>(Channel.BUFFERED)
    val messagesFlow = messages.receiveAsFlow()

    val uiState: StateFlow<BrowseUiState> = parentId.flatMapLatest { pid ->
        combine(
            app.treeRepository.observeChildren(pid),
            flow { emit(loadHeader(pid)) },
            sort,
            filter,
        ) { children, header, order, criteria ->
            // One walk of the library per refresh, shared by the meter above the list and by
            // every row in it. The alternative — a count query per row — is what makes a tree
            // browser scroll badly, and this library is small enough to roll up in memory.
            val all = app.treeRepository.allNodes()
            val stats = SubtreeStats.index(all)
            val childCounts = all.groupingBy { it.parentId ?: SubtreeStats.ROOT }.eachCount()
            val visible = BrowseQuery.apply(children, criteria, order)
            BrowseUiState(
                listState = if (visible.isEmpty()) ListState.EMPTY else ListState.CONTENT,
                parentId = pid,
                title = header.first,
                breadcrumbs = header.second,
                children = visible,
                stats = stats,
                childCounts = childCounts,
                sort = order,
                filter = criteria,
                unfilteredCount = children.size,
            )
        }
    }.catch { error ->
        emit(BrowseUiState(listState = ListState.ERROR, errorMessage = error.message))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BrowseUiState(),
    )

    fun open(id: Long?) {
        parentId.value = id
        savedStateHandle[KEY_PARENT] = id ?: ROOT
    }

    fun goUp() {
        val current = parentId.value ?: return
        viewModelScope.launch {
            val node = app.treeRepository.getNode(current)
            open(node?.parentId)
        }
    }

    fun setSort(value: BrowseSort) {
        sort.value = value
        app.settings.browseSort = value.name
    }

    fun setFilter(value: BrowseFilter) {
        filter.value = value
    }

    fun clearFilter() = setFilter(BrowseFilter())

    fun create(type: NodeType, title: String) {
        viewModelScope.launch {
            runCatching { app.treeRepository.create(parentId.value, type, title) }
                .onFailure { messages.send(it.message ?: "Could not create") }
        }
    }

    fun rename(id: Long, title: String) {
        viewModelScope.launch {
            runCatching { app.treeRepository.rename(id, title) }
                .onFailure { messages.send(it.message ?: "Could not rename") }
        }
    }

    fun move(id: Long, newParentId: Long?) {
        viewModelScope.launch {
            runCatching { app.treeRepository.move(id, newParentId) }
                .onFailure { messages.send(it.message ?: "Could not move") }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { app.treeRepository.delete(id) }
                .onFailure { messages.send(it.message ?: "Could not delete") }
        }
    }

    /** Starring from the list, so a post no longer has to be opened to be pinned. */
    fun toggleFavorite(node: Node) {
        viewModelScope.launch {
            runCatching { app.postRepository.setFavorite(node.id, !node.favorite) }
                .onFailure { messages.send(it.message ?: "Could not update") }
        }
    }

    fun setStatus(node: Node, status: LearningStatus) {
        viewModelScope.launch {
            runCatching { app.postRepository.setStatus(node.id, status) }
                .onFailure { messages.send(it.message ?: "Could not update") }
        }
    }

    suspend fun possibleParents(movingId: Long): List<Node> =
        app.treeRepository.possibleParents(movingId)

    private suspend fun loadHeader(pid: Long?): Pair<String, List<Node>> {
        if (pid == null) {
            return getApplication<Application>().getString(R.string.browse_library) to emptyList()
        }
        val crumbs = app.treeRepository.breadcrumbs(pid)
        val title = crumbs.lastOrNull()?.title
            ?: getApplication<Application>().getString(R.string.nav_browse)
        return title to crumbs
    }

    private fun readSavedParent(): Long? {
        val stored = savedStateHandle.get<Long>(KEY_PARENT) ?: ROOT
        return stored.takeUnless { it == ROOT }
    }

    companion object {
        private const val KEY_PARENT = "browseParentId"
        private const val ROOT = -1L
    }
}
