package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.ai.chat.SendResult
import com.cs426.learningmocha.ai.chat.StreamingBubble
import com.cs426.learningmocha.data.local.entity.ChatMessage
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatConversationUiState(
    val listState: ListState = ListState.LOADING,
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val sharedContext: Map<Long, Int> = emptyMap(),
    val streaming: StreamingBubble? = null,
    val mode: String = "answer",
    val sending: Boolean = false,
    val online: Boolean = true,
    val errorMessage: String? = null,
)

class ChatConversationViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    val sessionId: Long = savedStateHandle.get<Long>(ARG_SESSION_ID) ?: 0L

    private val mode = MutableStateFlow("answer")
    private val sending = MutableStateFlow(false)
    private val online = MutableStateFlow(true)

    /**
     * The reply currently arriving over SSE, or null. Filtered to this session so
     * a stream started elsewhere never shows up here. Leaving the screen clears
     * the ViewModel scope, which cancels [sendJob] and with it the stream — the
     * partial text is dropped and nothing was persisted.
     */
    val streaming: StateFlow<StreamingBubble?> = app.chatRepository.streaming
        .map { bubble -> bubble?.takeIf { it.sessionId == sessionId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reviewNav = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    private var sendJob: Job? = null

    private val history = app.chatRepository.observeMessages(sessionId)
        .combine(app.chatRepository.sharedContext) { messages, shared -> messages to shared }

    val uiState: StateFlow<ChatConversationUiState> = combine(
        history,
        streaming,
        mode,
        sending,
        online,
    ) { stored, bubble, currentMode, busy, reachable ->
        val (messages, shared) = stored
        ChatConversationUiState(
            listState = if (messages.isEmpty() && bubble == null) ListState.EMPTY else ListState.CONTENT,
            title = "",
            messages = messages,
            sharedContext = shared,
            streaming = bubble,
            mode = currentMode,
            sending = busy,
            online = reachable,
        )
    }.catch { error ->
        emit(ChatConversationUiState(listState = ListState.ERROR, errorMessage = error.message, online = false))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChatConversationUiState(),
    )

    init {
        ping()
    }

    fun setMode(value: String) {
        mode.value = value
    }

    fun ping() {
        viewModelScope.launch { online.value = app.chatRepository.ping() }
    }

    suspend fun sessionTitle(): String =
        app.chatRepository.getSession(sessionId)?.title.orEmpty()

    fun send(text: String) {
        if (sendJob?.isActive == true) return
        sendJob = launchSend { app.chatRepository.send(sessionId, mode.value, text) }
    }

    fun retry() {
        if (sendJob?.isActive == true) return
        sendJob = launchSend { app.chatRepository.retry(sessionId, mode.value) }
    }

    private fun launchSend(request: suspend () -> SendResult): Job = viewModelScope.launch {
        sending.value = true
        try {
            when (val result = request()) {
                is SendResult.NeedsReview -> reviewNav.emit(result.messageId)
                else -> Unit
            }
        } finally {
            sending.value = false
        }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}
