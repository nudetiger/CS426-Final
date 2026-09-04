package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DictionaryScope { ALL, GLOBAL, POST }

/** A dictionary entry plus the title of the post it belongs to, for the scope caption. */
data class DictionaryRow(
    val entry: DictionaryEntry,
    val postTitle: String?,
)

data class DictionaryUiState(
    val listState: ListState = ListState.LOADING,
    val rows: List<DictionaryRow> = emptyList(),
    val filtered: Boolean = false,
    val errorMessage: String? = null,
)

class DictionaryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp

    val query = MutableStateFlow("")
    val scope = MutableStateFlow(DictionaryScope.ALL)

    private val errors = Channel<String>(Channel.BUFFERED)
    val errorFlow = errors.receiveAsFlow()

    /** Last entry deleted from this screen, kept only to back the undo snackbar. */
    private var lastDeleted: DictionaryEntry? = null

    private val rows = app.postRepository.observeDictionary().map { entries ->
        val titles = app.treeRepository.allNodes().associate { it.id to it.title }
        entries.map { entry -> DictionaryRow(entry, entry.postId?.let { titles[it] }) }
    }

    val uiState: StateFlow<DictionaryUiState> = combine(
        rows,
        query,
        scope,
    ) { all, q, sc ->
        val needle = q.trim()
        val filtered = all.filter { row ->
            val scopeOk = when (sc) {
                DictionaryScope.ALL -> true
                DictionaryScope.GLOBAL -> row.entry.postId == null
                DictionaryScope.POST -> row.entry.postId != null
            }
            val textOk = needle.isEmpty() ||
                row.entry.term.contains(needle, true) ||
                row.entry.definition.contains(needle, true) ||
                row.entry.meaningVi.contains(needle, true)
            scopeOk && textOk
        }
        DictionaryUiState(
            listState = if (filtered.isEmpty()) ListState.EMPTY else ListState.CONTENT,
            rows = filtered,
            filtered = needle.isNotEmpty() || sc != DictionaryScope.ALL,
        )
    }.catch { error ->
        emit(DictionaryUiState(listState = ListState.ERROR, errorMessage = error.message))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DictionaryUiState(),
    )

    /** Terms created on this screen are global by definition — they belong to no single post. */
    fun addTerm(term: String, definition: String, meaningVi: String) {
        report { app.postRepository.addTerm(null, term, definition, meaningVi) }
    }

    fun updateTerm(entry: DictionaryEntry, term: String, definition: String, meaningVi: String) {
        report {
            app.postRepository.updateTerm(
                entry.copy(
                    term = term.trim(),
                    definition = definition.trim(),
                    meaningVi = meaningVi.trim(),
                ),
            )
        }
    }

    fun deleteTerm(entry: DictionaryEntry) {
        lastDeleted = entry
        report { app.postRepository.removeTerm(entry.id) }
    }

    fun undoDelete() {
        val entry = lastDeleted ?: return
        lastDeleted = null
        report {
            app.postRepository.addTerm(
                entry.postId,
                entry.term,
                entry.definition,
                entry.meaningVi,
            )
        }
    }

    private fun report(block: suspend () -> Unit) {
        viewModelScope.launch {
            val message = runCatching { block() }.exceptionOrNull()?.message
            if (message != null) errors.send(message)
        }
    }
}
