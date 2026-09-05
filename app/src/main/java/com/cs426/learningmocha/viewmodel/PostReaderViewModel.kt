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
import com.cs426.learningmocha.ui.browse.BranchReading
import com.cs426.learningmocha.ui.browse.OutlineRow
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.Readiness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Where this post sits in a branch the user is reading end to end. Null unless the post was
 * opened through "Read this branch", and null again if it somehow falls outside that branch's
 * order — a post moved out from under the branch while it was open, say.
 */
data class BranchSession(
    val title: String,
    /** 1-based, for "3 of 12". */
    val position: Int,
    val total: Int,
    val previousId: Long?,
    val nextId: Long?,
)

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
    val nextPost: Node? = null,
    /** How far through this post's prerequisites the reader is; empty when it has none. */
    val readiness: Readiness = Readiness(emptyList()),
    val branch: BranchSession? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PostReaderViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    val postId: Long = savedStateHandle.get<Long>(ARG_POST_ID) ?: 0L

    /** 0 when the post was opened normally rather than as part of a branch read. */
    val branchId: Long = savedStateHandle.get<Long>(ARG_BRANCH_ID) ?: 0L

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
        nextPost = nextPost,
        readiness = Readiness(prerequisites),
        branch = branchSession(),
    )

    /**
     * Recomputed per emission rather than cached: applying an AI batch or editing a post can
     * change the branch's order underneath the reader, and a stale "4 of 9" is worse than the
     * cost of one walk over a library this size.
     */
    private suspend fun branchSession(): BranchSession? {
        if (branchId == 0L) return null
        val root = app.treeRepository.getNode(branchId) ?: return null
        val order = BranchReading.order(
            branchId,
            app.treeRepository.allNodes(),
            app.postRepository.prerequisiteEdges(),
        )
        val index = order.indexOfFirst { it.id == postId }
        if (index < 0) return null
        return BranchSession(
            title = root.title,
            position = index + 1,
            total = order.size,
            previousId = order.getOrNull(index - 1)?.id,
            nextId = order.getOrNull(index + 1)?.id,
        )
    }

    /** The branch's tree, for the structure sheet. Empty when this is not a branch read. */
    suspend fun outline(): List<OutlineRow> {
        if (branchId == 0L) return emptyList()
        return BranchReading.outline(branchId, app.treeRepository.allNodes())
    }

    companion object {
        const val ARG_POST_ID = "postId"
        const val ARG_BRANCH_ID = "branchId"
    }
}
