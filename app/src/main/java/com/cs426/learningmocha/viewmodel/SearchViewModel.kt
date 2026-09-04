package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.data.repo.SearchFilter
import com.cs426.learningmocha.data.repo.SearchHit
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val listState: ListState = ListState.EMPTY,
    val results: List<SearchHit> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp

    val query = MutableStateFlow("")
    val type = MutableStateFlow<NodeType?>(null)
    val status = MutableStateFlow<LearningStatus?>(null)
    val favoritesOnly = MutableStateFlow(false)

    val uiState: StateFlow<SearchUiState> = combine(
        query.debounce(300),
        type,
        status,
        favoritesOnly,
    ) { q, t, s, fav ->
        SearchFilter(query = q, type = t, status = s, favoritesOnly = fav)
    }.mapLatest { filter ->
        val results = app.searchRepository.search(filter)
        val emptyFilter = filter.query.isBlank() &&
            !filter.favoritesOnly &&
            filter.status == null &&
            filter.type == null
        SearchUiState(
            listState = when {
                emptyFilter -> ListState.EMPTY
                results.isEmpty() -> ListState.EMPTY
                else -> ListState.CONTENT
            },
            results = results,
        )
    }.catch { error ->
        emit(SearchUiState(listState = ListState.ERROR, errorMessage = error.message))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SearchUiState(),
    )
}
