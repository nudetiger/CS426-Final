package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.R
import com.cs426.learningmocha.ai.protocol.ActionLabels
import com.cs426.learningmocha.ai.protocol.ActionParser
import com.cs426.learningmocha.ai.protocol.ActionValidator
import com.cs426.learningmocha.ai.protocol.Envelope
import com.cs426.learningmocha.ai.protocol.KbAction
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.util.TextDiff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A container a proposed item can be filed under, labelled with its whole path. */
data class ReviewLocation(
    val title: String,
    val path: String,
)

enum class ReviewOutcome {
    NONE,
    APPLIED,
    DISCARDED,
    UNDONE,
}

data class ReviewRow(
    val index: Int,
    val op: String,
    val title: String,
    val caption: String,
    val target: String,
    val parentTitle: String?,
    val indent: Int,
    val checked: Boolean,
    val preview: String,
    val content: String,
    val error: String?,
    val destructive: Boolean,
    val editable: Boolean,
    val relocatable: Boolean,
    val allowRoot: Boolean,
)

data class ReviewUiState(
    val summary: String = "",
    val rows: List<ReviewRow> = emptyList(),
    val errors: List<String> = emptyList(),
    val containers: List<ReviewLocation> = emptyList(),
    val selectedCount: Int = 0,
    val destructiveSelected: Int = 0,
    val listState: ListState = ListState.LOADING,
    val applying: Boolean = false,
    val outcome: ReviewOutcome = ReviewOutcome.NONE,
    val appliedCount: Int = 0,
    val deletedCount: Int = 0,
)

/**
 * Holds the proposed batch while the user reshapes it. Nothing here touches Room until
 * [apply], which is the single point where an AI-authored change becomes real.
 */
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
    private var nodes: List<Node> = emptyList()

    init {
        reload()
    }

    fun reload() {
        _uiState.value = ReviewUiState()
        viewModelScope.launch { load() }
    }

    fun toggle(index: Int) {
        if (index !in selected.indices) return
        selected[index] = !selected[index]
        publish()
    }

    /** Refiles a proposed item under [parent] — null means the top level of the library. */
    fun relocate(index: Int, parent: ReviewLocation?) {
        val action = actions.getOrNull(index) ?: return
        val moved = if (action.op == OP_MOVE) {
            action.copy(newParentTitle = parent?.title)
        } else {
            action.copy(parentTitle = parent?.title, parentRef = null)
        }
        actions = actions.toMutableList().also { it[index] = moved }
        publish()
    }

    /**
     * Rewrites a generated title/body before anything is written. A retitled item also
     * rewrites how the rest of the batch addresses it, so the edit cannot orphan the
     * tags, links and resources the model attached to the old title.
     */
    fun edit(index: Int, title: String, content: String) {
        val action = actions.getOrNull(index) ?: return
        val trimmed = title.trim()
        val renamed = when {
            trimmed.isEmpty() -> action.title
            trimmed.equals(action.postTitle, ignoreCase = true) -> action.title
            else -> trimmed
        }
        val previous = (action.title ?: action.postTitle)?.trim().orEmpty()
        val target = renamed?.trim().orEmpty()
        val next = actions.toMutableList()
        next[index] = action.copy(title = renamed, content = content)
        if (target.isNotEmpty() && previous.isNotEmpty() && !previous.equals(target, ignoreCase = true)) {
            retarget(next, index, previous, target)
        }
        actions = next
        publish()
    }

    fun apply() {
        if (_uiState.value.applying) return
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true) }
            nodes = app.treeRepository.allNodes()
            publish()
            if (_uiState.value.errors.isNotEmpty()) {
                _uiState.update { it.copy(applying = false) }
                return@launch
            }
            val chosen = actions.filterIndexed { index, _ -> selected.getOrElse(index) { false } }
            try {
                app.chatRepository.lastUndo = app.actionExecutor.apply(chosen)
                app.chatRepository.markApplied(messageId)
                _uiState.update {
                    it.copy(
                        applying = false,
                        outcome = ReviewOutcome.APPLIED,
                        appliedCount = chosen.size,
                        deletedCount = chosen.count { action -> action.op == OP_DELETE },
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        applying = false,
                        errors = listOf(error.message ?: app.getString(R.string.review_apply_failed)),
                    )
                }
            }
        }
    }

    fun discard() {
        viewModelScope.launch {
            app.chatRepository.markDiscarded(messageId)
            _uiState.update { it.copy(outcome = ReviewOutcome.DISCARDED) }
        }
    }

    fun undo() {
        viewModelScope.launch {
            app.chatRepository.undoLast()
            app.chatRepository.markDiscarded(messageId)
            _uiState.update { it.copy(outcome = ReviewOutcome.UNDONE) }
        }
    }

    private suspend fun load() {
        val message = app.chatRepository.getMessage(messageId)
        if (message == null) {
            _uiState.update { it.copy(listState = ListState.ERROR) }
            return
        }
        val envelope = try {
            ActionParser.parse(message.actionsJson.orEmpty())
        } catch (_: Exception) {
            Envelope(type = "actions", summary = message.text, actions = emptyList())
        }
        actions = envelope.actions.orEmpty()
        // Destructive rows are opt-in: deletion cascades and undo cannot bring posts back.
        selected = BooleanArray(actions.size) { actions[it].op != OP_DELETE }
        nodes = app.treeRepository.allNodes()
        val containers = app.treeRepository.possibleParents(0L)
        _uiState.update {
            it.copy(
                summary = envelope.summary ?: message.text,
                containers = locations(containers),
            )
        }
        publish()
    }

    /** Re-validates the currently ticked subset and rebuilds every row from cached nodes. */
    private fun publish() {
        val chosen = actions.indices.filter { selected.getOrElse(it) { false } }
        val raw = if (chosen.isEmpty()) {
            listOf(app.getString(R.string.review_none_selected))
        } else {
            ActionValidator.validate(actions, nodes, selected)
        }
        val attributed = attribute(raw)
        val list = rows(attributed.byIndex)
        _uiState.update {
            it.copy(
                rows = list,
                errors = if (list.isEmpty()) emptyList() else attributed.messages,
                selectedCount = chosen.size,
                destructiveSelected = list.count { row -> row.checked && row.destructive },
                listState = if (list.isEmpty()) ListState.EMPTY else ListState.CONTENT,
            )
        }
    }

    private fun rows(errors: Map<Int, String>): List<ReviewRow> {
        val byTitle = nodes.associateBy { it.title.lowercase() }
        val byRef = actions.filter { !it.ref.isNullOrBlank() }.associateBy { it.ref!!.trim() }
        return actions.mapIndexed { index, action ->
            val op = action.op.orEmpty()
            val proposed = action.content.orEmpty()
            val parentTitle = if (op == OP_MOVE) action.newParentTitle else action.parentTitle
            val parentLabel = parentTitle
                ?: action.parentRef?.trim()?.let { byRef[it]?.title }
            val preview = when (op) {
                "create_post" -> proposed
                "update_post" -> if (proposed.isBlank()) {
                    ""
                } else {
                    val existing = byTitle[(action.postTitle ?: action.title)?.trim()?.lowercase().orEmpty()]
                    TextDiff.preview(existing?.content.orEmpty(), proposed)
                }
                else -> ""
            }
            ReviewRow(
                index = index,
                op = op,
                title = ActionLabels.describe(action),
                caption = when {
                    op !in LOCATED_OPS -> ""
                    parentLabel.isNullOrBlank() -> app.getString(R.string.review_row_root)
                    else -> app.getString(R.string.review_row_under, parentLabel)
                },
                target = (action.postTitle ?: action.title ?: action.term).orEmpty(),
                parentTitle = parentTitle,
                indent = ActionLabels.indent(action, actions),
                checked = selected.getOrElse(index) { false },
                preview = preview,
                content = proposed,
                error = errors[index],
                destructive = op == OP_DELETE,
                editable = op == "create_post" || (op == "update_post" && proposed.isNotBlank()),
                relocatable = op in RELOCATABLE_OPS,
                allowRoot = op != OP_MOVE,
            )
        }
    }

    /**
     * Re-labels the validator's "Action N" numbering with the change it belongs to, and
     * hangs the same text under its row, so an error points at something the user can see
     * instead of at a number they have to count out.
     */
    private fun attribute(raw: List<String>): Attributed {
        val messages = ArrayList<String>(raw.size)
        val byIndex = HashMap<Int, String>()
        for (error in raw) {
            val match = ACTION_PREFIX.matchEntire(error)
            val index = match?.groupValues?.get(1)?.toIntOrNull()
                ?.minus(1)
                ?.takeIf { it in actions.indices }
            if (match == null || index == null) {
                messages.add(error)
                continue
            }
            val detail = match.groupValues[2]
            messages.add(
                app.getString(R.string.review_error_row, ActionLabels.describe(actions[index]), detail),
            )
            byIndex[index] = byIndex[index]?.let { "$it\n$detail" } ?: detail
        }
        return Attributed(messages, byIndex)
    }

    private fun locations(containers: List<Node>): List<ReviewLocation> {
        val byId = nodes.associateBy { it.id }
        return containers
            .map { node ->
                val chain = ArrayList<String>()
                var cursor: Node? = node
                val seen = HashSet<Long>()
                while (cursor != null && seen.add(cursor.id)) {
                    chain.add(cursor.title)
                    cursor = cursor.parentId?.let { byId[it] }
                }
                ReviewLocation(title = node.title, path = chain.asReversed().joinToString(" / "))
            }
            .sortedBy { it.path.lowercase() }
    }

    private fun retarget(list: MutableList<KbAction>, skip: Int, from: String, to: String) {
        for (index in list.indices) {
            if (index == skip) continue
            val action = list[index]
            list[index] = action.copy(
                parentTitle = action.parentTitle.swap(from, to),
                postTitle = action.postTitle.swap(from, to),
                newParentTitle = action.newParentTitle.swap(from, to),
                fromTitle = action.fromTitle.swap(from, to),
                toTitle = action.toTitle.swap(from, to),
            )
        }
    }

    private fun String?.swap(from: String, to: String): String? =
        if (this != null && this.trim().equals(from, ignoreCase = true)) to else this

    private class Attributed(
        val messages: List<String>,
        val byIndex: Map<Int, String>,
    )

    companion object {
        const val ARG_MESSAGE_ID = "messageId"

        private const val OP_DELETE = "delete_post"
        private const val OP_MOVE = "move_post"
        private val LOCATED_OPS = setOf("create_branch", "create_folder", "create_post", OP_MOVE)
        private val RELOCATABLE_OPS = setOf("create_folder", "create_post", OP_MOVE)
        private val ACTION_PREFIX = Regex("Action (\\d+): (.*)")
    }
}
