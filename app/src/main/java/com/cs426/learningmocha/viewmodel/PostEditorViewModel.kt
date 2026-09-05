package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.ResourceType
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One reference attached to the post being edited. [storedId] is 0 until the row exists in Room;
 * [pending] marks the ones this draft still has to insert on save. A row with neither
 * (a YouTube link derived from the markdown) is shown but cannot be detached here.
 */
data class EditorResource(
    val key: Long,
    val storedId: Long,
    val type: ResourceType,
    val title: String,
    val url: String,
    val pending: Boolean,
) {
    val removable: Boolean get() = pending || storedId != 0L
}

/**
 * A completed save. [storedTitle] can differ from what was typed: a new post whose title the
 * library already holds is stored numbered ("Raft (2)") rather than refused, and the editor
 * has to say so instead of letting the user believe their title was kept.
 */
data class EditorSaved(
    val postId: Long,
    val storedTitle: String,
    val renamed: Boolean,
    /**
     * Prerequisites the save dropped because they would have closed a loop. Reported on the
     * event rather than in [EditorUiState]: saving navigates straight back to the reader, so
     * a message left on the editor's own state is destroyed before anyone reads it.
     */
    val refusedPrerequisites: Int = 0,
)

data class EditorUiState(
    val listState: ListState = ListState.LOADING,
    val title: String = "",
    val content: String = "",
    val tags: String = "",
    val status: LearningStatus = LearningStatus.READING,
    val isNew: Boolean = true,
    val resources: List<EditorResource> = emptyList(),
    val pendingTerms: List<DictionaryEntry> = emptyList(),
    val errorMessage: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val nextPostId: Long? = null,
    val nextTitle: String = "",
    /** Posts this one should be read after. Order is the picker's, resolved on save. */
    val prerequisiteIds: List<Long> = emptyList(),
    val prerequisiteTitles: List<String> = emptyList(),
)

/**
 * Owns the in-progress draft. The fragment never keeps editor state of its own: it pushes every
 * keystroke in here and renders back from [uiState], so a configuration change cannot lose text
 * (the ViewModel outlives the fragment) and the stored copy loaded from Room never overwrites a
 * draft that is already in memory. Title/content/tags/status also go through [SavedStateHandle],
 * which carries them across process death.
 */
class PostEditorViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    private val postId: Long = savedStateHandle.get<Long>(ARG_POST_ID) ?: 0L
    private val parentId: Long? = savedStateHandle.get<Long>(ARG_PARENT_ID)
        ?.takeUnless { it == ROOT }

    /** Title Browse already asked for, so a new post opens with it filled in. */
    private val suggestedTitle: String = savedStateHandle.get<String>(ARG_TITLE).orEmpty()

    private var initialTitle = ""
    private var initialContent = ""
    private var initialTags = ""
    private var initialStatus = LearningStatus.READING
    private var initialIcon: String? = null
    private var initialColor: String? = null
    private var initialNext: Long? = null
    private var initialPrereqs: List<Long> = emptyList()
    private var draftTouched = false
    private var nextPendingKey = -1L
    private val removedResourceIds = LinkedHashSet<Long>()

    private val _uiState = MutableStateFlow(EditorUiState(isNew = postId == 0L))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val saved = Channel<EditorSaved>(Channel.BUFFERED)
    val savedFlow = saved.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onTitleChanged(value: String) {
        if (_uiState.value.title == value) return
        draftTouched = true
        savedStateHandle[KEY_DRAFT_TITLE] = value
        _uiState.update { it.copy(title = value) }
    }

    fun onContentChanged(value: String) {
        if (_uiState.value.content == value) return
        draftTouched = true
        savedStateHandle[KEY_DRAFT_CONTENT] = value
        _uiState.update { it.copy(content = value) }
    }

    fun onTagsChanged(value: String) {
        if (_uiState.value.tags == value) return
        draftTouched = true
        savedStateHandle[KEY_DRAFT_TAGS] = value
        _uiState.update { it.copy(tags = value) }
    }

    fun onStatusChanged(status: LearningStatus) {
        if (_uiState.value.status == status) return
        draftTouched = true
        savedStateHandle[KEY_DRAFT_STATUS] = status.name
        _uiState.update { it.copy(status = status) }
    }

    fun onMarkChanged(icon: String?, color: String?) {
        if (_uiState.value.icon == icon && _uiState.value.color == color) return
        draftTouched = true
        _uiState.update { it.copy(icon = icon, color = color) }
    }

    fun onPrerequisitesChanged(ids: List<Long>, titles: List<String>) {
        if (_uiState.value.prerequisiteIds == ids) return
        draftTouched = true
        _uiState.update { it.copy(prerequisiteIds = ids, prerequisiteTitles = titles) }
    }

    fun onNextChanged(id: Long?, title: String) {
        if (_uiState.value.nextPostId == id) return
        draftTouched = true
        _uiState.update { it.copy(nextPostId = id, nextTitle = title) }
    }

    fun addTerm(term: String, definition: String, meaningVi: String) {
        val entry = DictionaryEntry(term = term, definition = definition, meaningVi = meaningVi)
        _uiState.update { it.copy(pendingTerms = it.pendingTerms + entry) }
    }

    fun addResource(type: ResourceType, title: String, url: String) {
        val draft = EditorResource(
            key = nextPendingKey--,
            storedId = 0L,
            type = type,
            title = title.trim(),
            url = url.trim(),
            pending = true,
        )
        _uiState.update { it.copy(resources = it.resources + draft) }
    }

    fun removeResource(key: Long) {
        val current = _uiState.value.resources
        val target = current.firstOrNull { it.key == key } ?: return
        if (!target.removable) return
        if (target.storedId != 0L) removedResourceIds.add(target.storedId)
        _uiState.update { it.copy(resources = current.filterNot { item -> item.key == key }) }
    }

    fun consumeError() {
        if (_uiState.value.errorMessage == null) return
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun isDirty(): Boolean {
        val state = _uiState.value
        if (state.listState != ListState.CONTENT) return false
        return state.title != initialTitle ||
            state.content != initialContent ||
            state.tags != initialTags ||
            state.status != initialStatus ||
            state.icon != initialIcon ||
            state.color != initialColor ||
            state.nextPostId != initialNext ||
            state.prerequisiteIds != initialPrereqs ||
            state.pendingTerms.isNotEmpty() ||
            removedResourceIds.isNotEmpty() ||
            state.resources.any { it.pending }
    }

    suspend fun postTitles(): List<String> = app.postRepository.postTitles()

    suspend fun titleToId(): Map<String, Long> = app.postRepository.titleToId()

    fun save() {
        val state = _uiState.value
        // The Save button lives in the header, outside the list-state overlay, so it is
        // reachable before the stored copy has loaded — writing then would save a blank draft.
        if (state.listState != ListState.CONTENT) return
        viewModelScope.launch {
            val names = state.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            runCatching {
                val id = if (postId == 0L) {
                    app.postRepository.createPost(
                        parentId,
                        state.title,
                        state.content,
                        state.status,
                        names,
                        state.pendingTerms,
                        state.icon,
                        state.color,
                        state.nextPostId,
                    )
                } else {
                    app.postRepository.savePost(
                        postId,
                        state.title,
                        state.content,
                        state.status,
                        names,
                        state.pendingTerms,
                        state.icon,
                        state.color,
                        state.nextPostId,
                    )
                    postId
                }
                // After the post write, so a reindex can never drop a reference added here.
                removedResourceIds.forEach { app.postRepository.removeResource(it) }
                // A new post has no id until the line above, so its prerequisites can only be
                // written now. Refusals come back rather than throwing: one impossible pick
                // must not cost the user the other four, or the article they just typed.
                val refusedPrereqs = app.postRepository
                    .setPrerequisites(id, state.prerequisiteIds)
                    .size
                state.resources.filter { it.pending }.forEach { item ->
                    app.postRepository.addResource(id, item.type, item.title, item.url)
                }
                // Read the title back rather than trusting the draft: createPost numbers a
                // duplicate, and the editor has to show what was actually written.
                val storedTitle = app.treeRepository.getNode(id)?.title ?: state.title
                EditorSaved(
                    id,
                    storedTitle,
                    renamed = storedTitle != state.title.trim(),
                    refusedPrerequisites = refusedPrereqs,
                )
            }.onSuccess { result ->
                removedResourceIds.clear()
                initialTitle = result.storedTitle
                initialContent = state.content
                initialTags = state.tags
                initialStatus = state.status
                initialIcon = state.icon
                initialColor = state.color
                initialNext = state.nextPostId
                initialPrereqs = state.prerequisiteIds
                _uiState.update {
                    it.copy(
                        title = result.storedTitle,
                        pendingTerms = emptyList(),
                        resources = it.resources.map { item -> item.copy(pending = false) },
                    )
                }
                saved.send(result)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(listState = ListState.CONTENT, errorMessage = error.message)
                }
            }
        }
    }

    private suspend fun load() {
        if (postId == 0L) {
            publish(suggestedTitle, "", "", LearningStatus.READING, emptyList(), isNew = true)
            return
        }
        val detail = app.postRepository.observeDetail(postId).first()
        if (detail == null) {
            _uiState.value = EditorUiState(
                listState = ListState.ERROR,
                errorMessage = getApplication<Application>()
                    .getString(com.cs426.learningmocha.R.string.reader_not_a_post),
            )
            return
        }
        publish(
            title = detail.post.title,
            content = detail.post.content.orEmpty(),
            tags = detail.tags.joinToString(", ") { it.name },
            status = detail.post.status,
            resources = detail.resources.map { it.toDraft() },
            isNew = false,
            icon = detail.post.icon,
            color = detail.post.color,
            nextPostId = detail.post.nextPostId,
            nextTitle = detail.nextPost?.title.orEmpty(),
            prerequisites = detail.prerequisites,
        )
    }

    /**
     * Seeds the dirty baseline from storage and shows it — but only where the draft is still
     * untouched, so a restored view or a saved-state draft always wins over the stored copy.
     */
    private fun publish(
        title: String,
        content: String,
        tags: String,
        status: LearningStatus,
        resources: List<EditorResource>,
        isNew: Boolean,
        icon: String? = null,
        color: String? = null,
        nextPostId: Long? = null,
        nextTitle: String = "",
        prerequisites: List<com.cs426.learningmocha.data.local.entity.Node> = emptyList(),
    ) {
        initialTitle = if (isNew) "" else title
        initialContent = content
        initialTags = tags
        initialStatus = status
        initialIcon = icon
        initialColor = color
        initialNext = nextPostId
        initialPrereqs = prerequisites.map { it.id }
        val current = _uiState.value
        val stored = resources.filterNot { it.storedId in removedResourceIds }
        val savedStatus = savedStateHandle.get<String>(KEY_DRAFT_STATUS)
            ?.let { name -> LearningStatus.entries.firstOrNull { it.name == name } }
        _uiState.value = current.copy(
            listState = ListState.CONTENT,
            isNew = isNew,
            title = if (draftTouched) {
                current.title
            } else {
                savedStateHandle.get<String>(KEY_DRAFT_TITLE) ?: title
            },
            content = if (draftTouched) {
                current.content
            } else {
                savedStateHandle.get<String>(KEY_DRAFT_CONTENT) ?: content
            },
            tags = if (draftTouched) {
                current.tags
            } else {
                savedStateHandle.get<String>(KEY_DRAFT_TAGS) ?: tags
            },
            status = if (draftTouched) current.status else savedStatus ?: status,
            resources = stored + current.resources.filter { it.pending },
            errorMessage = null,
            icon = if (draftTouched) current.icon else icon,
            color = if (draftTouched) current.color else color,
            nextPostId = if (draftTouched) current.nextPostId else nextPostId,
            nextTitle = if (draftTouched) current.nextTitle else nextTitle,
            prerequisiteIds = if (draftTouched) {
                current.prerequisiteIds
            } else {
                prerequisites.map { it.id }
            },
            prerequisiteTitles = if (draftTouched) {
                current.prerequisiteTitles
            } else {
                prerequisites.map { it.title }
            },
        )
    }

    private fun ResourceItem.toDraft() = EditorResource(
        key = id,
        storedId = id,
        type = type,
        title = title,
        url = url,
        pending = false,
    )

    companion object {
        const val ARG_POST_ID = "postId"
        const val ARG_PARENT_ID = "parentId"
        const val ARG_TITLE = "title"
        const val ROOT = -1L
        private const val KEY_DRAFT_TITLE = "draftTitle"
        private const val KEY_DRAFT_CONTENT = "draftContent"
        private const val KEY_DRAFT_TAGS = "draftTags"
        private const val KEY_DRAFT_STATUS = "draftStatus"
    }
}
