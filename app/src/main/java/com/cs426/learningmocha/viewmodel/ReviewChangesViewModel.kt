package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.ai.protocol.ActionLabels
import com.cs426.learningmocha.ai.protocol.ActionParser
import com.cs426.learningmocha.ai.protocol.ActionValidator
import com.cs426.learningmocha.ai.protocol.Envelope
import com.cs426.learningmocha.ai.protocol.KbAction
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.util.TextDiff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewRow(
    val index: Int,
    val title: String,
    val caption: String,
    val indent: Int,
    val checked: Boolean,
    val preview: String,
)

data class ReviewUiState(
    val summary: String = "",
    val rows: List<ReviewRow> = emptyList(),
    val errors: List<String> = emptyList(),
    val applying: Boolean = false,
    val done: Boolean = false,
    val doneMessage: String? = null,
)

class ReviewChangesViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    private val messageId: Long = savedStateHandle.get<Long>(ARG_MESSAGE_ID) ?: 0L

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState

    private var actions: List<KbAction> = emptyList()
    private var selected = BooleanArray(0)

    init {
        viewModelScope.launch { load() }
    }

    fun toggle(index: Int) {
        if (index !in selected.indices) return
        selected[index] = !selected[index]
        publish()
    }

    fun apply() {
        if (_uiState.value.applying) return
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true, errors = emptyList()) }
            val chosen = actions.filterIndexed { i, _ -> selected.getOrElse(i) { false } }
            val nodes = app.treeRepository.allNodes()
            val errors = ActionValidator.validate(chosen, nodes)
            if (errors.isNotEmpty()) {
                _uiState.update { it.copy(applying = false, errors = errors) }
                return@launch
            }
            try {
                app.chatRepository.lastUndo = app.actionExecutor.apply(chosen)
                app.chatRepository.markApplied(messageId)
                _uiState.update {
                    it.copy(applying = false, done = true, doneMessage = "Applied ${chosen.size} changes")
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(applying = false, errors = listOf(error.message ?: "Apply failed"))
                }
            }
        }
    }

    fun discard() {
        viewModelScope.launch {
            app.chatRepository.markDiscarded(messageId)
            _uiState.update { it.copy(done = true, doneMessage = "Discarded") }
        }
    }

    fun undo() {
        viewModelScope.launch {
            app.chatRepository.undoLast()
            app.chatRepository.markDiscarded(messageId)
            _uiState.update { it.copy(done = true, doneMessage = "Undone") }
        }
    }

    private suspend fun load() {
        val message = app.chatRepository.getMessage(messageId) ?: return
        val envelope = try {
            ActionParser.parse(message.actionsJson.orEmpty())
        } catch (_: Exception) {
            Envelope(type = "actions", summary = message.text, actions = emptyList())
        }
        actions = envelope.actions.orEmpty()
        selected = BooleanArray(actions.size) { true }
        val nodes = app.treeRepository.allNodes()
        _uiState.value = ReviewUiState(
            summary = envelope.summary ?: message.text,
            rows = rows(nodes),
            errors = ActionValidator.validate(actions, nodes),
        )
    }

    private fun publish() {
        _uiState.update { it.copy(rows = rows(emptyList()), errors = emptyList()) }
        viewModelScope.launch {
            val nodes = app.treeRepository.allNodes()
            val chosen = actions.filterIndexed { i, _ -> selected.getOrElse(i) { false } }
            _uiState.update {
                it.copy(rows = rows(nodes), errors = ActionValidator.validate(chosen, nodes))
            }
        }
    }

    private fun rows(nodes: List<Node>): List<ReviewRow> {
        val byTitle = nodes.associateBy { it.title.lowercase() }
        return actions.mapIndexed { index, action ->
            val parent = action.parentTitle ?: action.parentRef
            val preview = when (action.op) {
                "update_post" -> {
                    val existing = byTitle[(action.postTitle ?: action.title)?.lowercase().orEmpty()]
                    TextDiff.preview(existing?.content.orEmpty(), action.content.orEmpty())
                }
                "create_post" -> action.content.orEmpty()
                else -> ""
            }
            ReviewRow(
                index = index,
                title = ActionLabels.describe(action),
                caption = parent?.let { "under $it" }.orEmpty(),
                indent = ActionLabels.indent(action, actions),
                checked = selected.getOrElse(index) { true },
                preview = preview,
            )
        }
    }

    companion object {
        const val ARG_MESSAGE_ID = "messageId"
    }
}
