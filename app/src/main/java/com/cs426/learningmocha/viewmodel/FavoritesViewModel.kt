package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class FavoritesUiState(
    val listState: ListState = ListState.LOADING,
    val posts: List<Node> = emptyList(),
    val errorMessage: String? = null,
)

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp

    val uiState: StateFlow<FavoritesUiState> = app.postRepository.observeFavorites()
        .map { posts ->
            FavoritesUiState(
                listState = if (posts.isEmpty()) ListState.EMPTY else ListState.CONTENT,
                posts = posts,
            )
        }
        .catch { error ->
            emit(FavoritesUiState(listState = ListState.ERROR, errorMessage = error.message))
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FavoritesUiState(),
        )
}
