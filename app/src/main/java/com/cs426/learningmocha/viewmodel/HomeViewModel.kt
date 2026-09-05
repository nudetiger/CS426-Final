package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.SubtreeStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val listState: ListState = ListState.LOADING,
    /** The whole library rolled up by learning status, for the meter under the tiles. */
    val progress: SubtreeStats = SubtreeStats(),
    val continueReading: List<Node> = emptyList(),
    val recents: List<Node> = emptyList(),
    val favorites: List<Node> = emptyList(),
    val branches: List<Node> = emptyList(),
    val errorMessage: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp

    val uiState: StateFlow<HomeUiState> = combine(
        app.postRepository.observeContinueReading(8),
        app.postRepository.observeRecentPosts(12),
        app.postRepository.observeFavorites(),
        app.treeRepository.observeRootBranches(),
        // Any post write bumps the count, which is what re-triggers the roll-up below.
        app.postRepository.observePostCount(),
    ) { continueReading, recents, favorites, branches, _ ->
        val empty = continueReading.isEmpty() && recents.isEmpty() &&
            favorites.isEmpty() && branches.isEmpty()
        HomeUiState(
            listState = if (empty) ListState.EMPTY else ListState.CONTENT,
            progress = SubtreeStats.index(app.treeRepository.allNodes())[SubtreeStats.ROOT]
                ?: SubtreeStats(),
            continueReading = continueReading,
            recents = recents,
            favorites = favorites,
            branches = branches,
        )
    }.catch { error ->
        emit(HomeUiState(listState = ListState.ERROR, errorMessage = error.message))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(),
    )
}
