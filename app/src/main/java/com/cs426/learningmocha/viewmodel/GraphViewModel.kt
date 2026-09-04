package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.repo.GraphRepository
import com.cs426.learningmocha.data.repo.GraphSnapshot
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.util.ForceLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A snapshot plus positions in the layout's own square world space; `GraphView` fits that
 * square to whatever screen it gets, so rotating the device never re-runs the layout.
 */
class PositionedGraph(
    val snapshot: GraphSnapshot,
    val positions: FloatArray,
    val worldSize: Float,
)

data class GraphUiState(
    val listState: ListState = ListState.LOADING,
    val graph: PositionedGraph? = null,
    val includeTags: Boolean = false,
    val focused: Boolean = false,
    val canWiden: Boolean = false,
    val selectedId: Long? = null,
    val errorMessage: String? = null,
)

class GraphViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository = GraphRepository((application as LearningMochaApp).database)
    private val focusPostId: Long = savedStateHandle.get<Long>(ARG_FOCUS_POST_ID) ?: 0L

    private val _uiState = MutableStateFlow(
        GraphUiState(focused = focusPostId != 0L, canWiden = focusPostId != 0L),
    )
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun setIncludeTags(include: Boolean) {
        if (_uiState.value.includeTags == include) return
        _uiState.update { it.copy(includeTags = include) }
        load()
    }

    /** Drops the one-post focus and draws the whole library instead. */
    fun widenToLibrary() {
        if (!_uiState.value.focused) return
        _uiState.update { it.copy(focused = false) }
        load()
    }

    fun select(nodeId: Long?) {
        if (_uiState.value.selectedId == nodeId) return
        _uiState.update { it.copy(selectedId = nodeId) }
    }

    fun retry() = load()

    private fun load() {
        val state = _uiState.value
        val activeFocus = if (state.focused) focusPostId else 0L
        val includeTags = state.includeTags
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(listState = ListState.LOADING, errorMessage = null) }
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    repository.snapshot(activeFocus, includeTags)
                }
                if (snapshot.nodes.isEmpty()) {
                    _uiState.update {
                        it.copy(listState = ListState.EMPTY, graph = null, selectedId = null)
                    }
                    return@launch
                }
                val positioned = withContext(Dispatchers.Default) { position(snapshot) }
                _uiState.update { current ->
                    val stillPresent = snapshot.nodes.any { it.id == current.selectedId }
                    current.copy(
                        listState = ListState.CONTENT,
                        graph = positioned,
                        selectedId = if (stillPresent) current.selectedId else null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(listState = ListState.ERROR, errorMessage = error.message)
                }
            }
        }
    }

    private fun position(snapshot: GraphSnapshot): PositionedGraph {
        val count = snapshot.nodes.size
        val from = IntArray(snapshot.edges.size) { snapshot.edges[it].from }
        val to = IntArray(snapshot.edges.size) { snapshot.edges[it].to }
        val positions = ForceLayout.layout(
            count,
            from,
            to,
            WORLD_SIZE,
            WORLD_SIZE,
            ForceLayout.suggestedIterations(count),
            LAYOUT_SEED,
        )
        return PositionedGraph(snapshot, positions, WORLD_SIZE)
    }

    companion object {
        const val ARG_FOCUS_POST_ID = "focusPostId"

        /** Arbitrary square the layout runs in; the view scales it to fit. */
        private const val WORLD_SIZE = 1200f

        /** Fixed so the same library always draws the same shape between launches. */
        private const val LAYOUT_SEED = 20260905L
    }
}
