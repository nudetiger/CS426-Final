package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TagDetailUiState(
    val listState: ListState = ListState.LOADING,
    val title: String = "",
    val posts: List<Node> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TagDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    private val tagId: Long = savedStateHandle.get<Long>(ARG_TAG_ID) ?: 0L

    /** Bumped by [retry] so the error state's Retry button re-subscribes to the tag query. */
    private val attempts = MutableStateFlow(0)

    // catch sits inside flatMapLatest: it ends the failing query but not the trigger, which is
    // what leaves the collector alive to pick up the retry.
    val uiState: StateFlow<TagDetailUiState> = attempts.flatMapLatest {
        flow<TagDetailUiState> {
            val tag = app.postRepository.getTag(tagId)
            emitAll(
                app.postRepository.observePostsWithTag(tagId).map { posts ->
                    TagDetailUiState(
                        listState = if (posts.isEmpty()) ListState.EMPTY else ListState.CONTENT,
                        title = tag?.name.orEmpty(),
                        posts = posts,
                    )
                },
            )
        }.catch { error ->
            emit(TagDetailUiState(listState = ListState.ERROR, errorMessage = error.message))
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TagDetailUiState(),
    )

    fun retry() {
        attempts.value += 1
    }

    companion object {
        const val ARG_TAG_ID = "tagId"
    }
}
