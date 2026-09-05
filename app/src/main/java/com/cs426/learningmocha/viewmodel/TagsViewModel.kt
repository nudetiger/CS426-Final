package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TagCount(
    val id: Long,
    val name: String,
    val postCount: Int,
)

data class TagsUiState(
    val listState: ListState = ListState.LOADING,
    val tags: List<TagCount> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Alphabetical tag index with a post count per tag.
 *
 * Neither `tags` nor `post_tags` exposes a Flow, so the list is read as a snapshot and re-read
 * through [refresh] instead of observed.
 */
class TagsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp

    val query = MutableStateFlow("")

    private val reload = MutableStateFlow(0)

    private val tags: Flow<List<TagCount>> = reload.map { loadTags() }

    val uiState: StateFlow<TagsUiState> = combine(tags, query) { all, q ->
        val needle = q.trim()
        val filtered = if (needle.isEmpty()) {
            all
        } else {
            all.filter { it.name.contains(needle, ignoreCase = true) }
        }
        TagsUiState(
            listState = if (filtered.isEmpty()) ListState.EMPTY else ListState.CONTENT,
            tags = filtered,
        )
    }.catch { error ->
        emit(TagsUiState(listState = ListState.ERROR, errorMessage = error.message))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TagsUiState(),
    )

    fun refresh() {
        reload.value = reload.value + 1
    }

    private suspend fun loadTags(): List<TagCount> = app.postRepository.tagCounts().map { row ->
        TagCount(row.id, row.name, row.postCount)
    }
}
