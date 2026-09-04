package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderUiState(
    val listState: ListState = ListState.LOADING,
    val post: Node? = null,
    val errorMessage: String? = null,
)

class PostReaderViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    val postId: Long = savedStateHandle.get<Long>(ARG_POST_ID) ?: 0L

    val uiState: StateFlow<ReaderUiState> = app.postRepository.observePost(postId)
        .map { node ->
            when {
                node == null -> ReaderUiState(
                    listState = ListState.ERROR,
                    errorMessage = getApplication<Application>()
                        .getString(com.cs426.learningmocha.R.string.reader_missing),
                )
                node.type != NodeType.POST -> ReaderUiState(
                    listState = ListState.ERROR,
                    errorMessage = getApplication<Application>()
                        .getString(com.cs426.learningmocha.R.string.reader_not_a_post),
                )
                else -> ReaderUiState(listState = ListState.CONTENT, post = node)
            }
        }
        .catch { error ->
            emit(ReaderUiState(listState = ListState.ERROR, errorMessage = error.message))
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ReaderUiState(),
        )

    init {
        viewModelScope.launch { app.postRepository.touch(postId) }
    }

    companion object {
        const val ARG_POST_ID = "postId"
    }
}
