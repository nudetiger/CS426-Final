package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.Tag
import com.cs426.learningmocha.data.repo.PostDetail
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderUiState(
    val listState: ListState = ListState.LOADING,
    val post: Node? = null,
    val breadcrumbs: List<Node> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val children: List<Node> = emptyList(),
    val backlinks: List<Node> = emptyList(),
    val related: List<Node> = emptyList(),
    val resources: List<ResourceItem> = emptyList(),
    val terms: List<DictionaryEntry> = emptyList(),
    val titleToId: Map<String, Long> = emptyMap(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PostReaderViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    val postId: Long = savedStateHandle.get<Long>(ARG_POST_ID) ?: 0L

    /** Bumped by [retry] so the error state's Retry button re-subscribes to the post. */
    private val attempts = MutableStateFlow(0)

    val uiState: StateFlow<ReaderUiState> = attempts
        .flatMapLatest { app.postRepository.observeDetail(postId) }
        .map { detail ->
            if (detail == null) {
                ReaderUiState(
                    listState = ListState.ERROR,
                    errorMessage = getApplication<Application>()
                        .getString(com.cs426.learningmocha.R.string.reader_missing),
                )
            } else {
                detail.toUi()
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

    fun toggleFavorite() {
        val post = uiState.value.post ?: return
        viewModelScope.launch { app.postRepository.setFavorite(post.id, !post.favorite) }
    }

    fun setStatus(status: LearningStatus) {
        viewModelScope.launch { app.postRepository.setStatus(postId, status) }
    }

    fun retry() {
        attempts.value += 1
    }

    private suspend fun PostDetail.toUi() = ReaderUiState(
        listState = ListState.CONTENT,
        post = post,
        breadcrumbs = app.treeRepository.breadcrumbs(post.parentId),
        tags = tags,
        children = children,
        backlinks = backlinks,
        related = related,
        resources = resources,
        terms = terms,
        titleToId = titleToId,
    )

    companion object {
        const val ARG_POST_ID = "postId"
    }
}
