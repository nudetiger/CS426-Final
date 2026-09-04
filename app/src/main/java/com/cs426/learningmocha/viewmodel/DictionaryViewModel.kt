package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class DictionaryScope { ALL, GLOBAL, POST }

data class DictionaryUiState(
    val listState: ListState = ListState.LOADING,
    val entries: List<DictionaryEntry> = emptyList(),
    val errorMessage: String? = null,
)

class DictionaryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp

    val query = MutableStateFlow("")
    val scope = MutableStateFlow(DictionaryScope.ALL)

    val uiState: StateFlow<DictionaryUiState> = combine(
        app.postRepository.observeDictionary(),
        query,
        scope,
    ) { entries, q, sc ->
        val needle = q.trim()
        val filtered = entries.filter { entry ->
            val scopeOk = when (sc) {
                DictionaryScope.ALL -> true
                DictionaryScope.GLOBAL -> entry.postId == null
                DictionaryScope.POST -> entry.postId != null
            }
            val textOk = needle.isEmpty() ||
                entry.term.contains(needle, true) ||
                entry.definition.contains(needle, true) ||
                entry.meaningVi.contains(needle, true)
            scopeOk && textOk
        }
        DictionaryUiState(
            listState = if (filtered.isEmpty()) ListState.EMPTY else ListState.CONTENT,
            entries = filtered,
        )
    }.catch { error ->
        emit(DictionaryUiState(listState = ListState.ERROR, errorMessage = error.message))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DictionaryUiState(),
    )
}
