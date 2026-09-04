package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val listState: ListState = ListState.LOADING,
    val title: String = "",
    val content: String = "",
    val tags: String = "",
    val status: LearningStatus = LearningStatus.READING,
    val isNew: Boolean = true,
    val errorMessage: String? = null,
)

class PostEditorViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    private val postId: Long = savedStateHandle.get<Long>(ARG_POST_ID) ?: 0L
    private val parentId: Long? = savedStateHandle.get<Long>(ARG_PARENT_ID)
        ?.takeUnless { it == ROOT }

    private val pendingTerms = ArrayList<DictionaryEntry>()

    private val _uiState = MutableStateFlow(
        EditorUiState(isNew = postId == 0L),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val saved = Channel<Long>(Channel.BUFFERED)
    val savedFlow = saved.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun addTerm(term: String, definition: String, meaningVi: String) {
        pendingTerms.add(
            DictionaryEntry(term = term, definition = definition, meaningVi = meaningVi),
        )
    }

    suspend fun postTitles(): List<String> = app.postRepository.postTitles()

    suspend fun titleToId(): Map<String, Long> = app.postRepository.titleToId()

    fun save(title: String, content: String, status: LearningStatus, tags: String) {
        viewModelScope.launch {
            val names = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val terms = pendingTerms.toList()
            runCatching {
                if (postId == 0L) {
                    app.postRepository.createPost(parentId, title, content, status, names, terms)
                } else {
                    app.postRepository.savePost(postId, title, content, status, names, terms)
                    postId
                }
            }.onSuccess { id ->
                pendingTerms.clear()
                saved.send(id)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        listState = ListState.CONTENT,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    private suspend fun load() {
        if (postId == 0L) {
            _uiState.value = EditorUiState(
                listState = ListState.CONTENT,
                isNew = true,
            )
            return
        }
        val node = app.treeRepository.getNode(postId)
        if (node == null || node.type != NodeType.POST) {
            _uiState.value = EditorUiState(
                listState = ListState.ERROR,
                errorMessage = getApplication<Application>()
                    .getString(com.cs426.learningmocha.R.string.reader_not_a_post),
            )
            return
        }
        val tags = app.postRepository.tagsForPost(postId).joinToString(", ") { it.name }
        _uiState.value = EditorUiState(
            listState = ListState.CONTENT,
            title = node.title,
            content = node.content.orEmpty(),
            tags = tags,
            status = node.status,
            isNew = false,
        )
    }

    companion object {
        const val ARG_POST_ID = "postId"
        const val ARG_PARENT_ID = "parentId"
        const val ROOT = -1L
    }
}
