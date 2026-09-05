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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
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
    private val online = MutableStateFlow(true)

    /** Owned by the repository, so it stays true across a trip away from this screen. */
    private val sending = app.chatRepository.busySessions.map { sessionId in it }

    /**
     * The reply currently arriving over SSE, or null. Filtered to this session so
     * a stream started elsewhere never shows up here. The stream belongs to the
     * repository, so leaving and returning mid-reply picks it up where it is.
     */
    val streaming: StateFlow<StreamingBubble?> = app.chatRepository.streaming
        .map { bubble -> bubble?.takeIf { it.sessionId == sessionId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reviewNav = MutableSharedFlow<Long>(extraBufferCapacity = 1)

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
        // A send started before this screen was left is still generating: re-attach so its
        // proposed batch still opens the review screen when it lands.
        app.chatRepository.inFlight(sessionId)?.let(::observe)
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
        if (app.chatRepository.inFlight(sessionId) != null) return
        observe(app.chatRepository.sendAsync(sessionId, mode.value, text))
    }

    fun retry() {
        if (app.chatRepository.inFlight(sessionId) != null) return
        observe(app.chatRepository.retryAsync(sessionId, mode.value))
    }

    /**
     * Waits on a send that outlives this ViewModel, purely to know whether to open the
     * review screen. Losing this wait — the user navigates away — leaves the send running;
     * the reply is persisted either way and this screen just stops listening.
     */
    private fun observe(pending: Deferred<SendResult>) {
        viewModelScope.launch {
            val result = try {
                pending.await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Anything the repository could not turn into a message row is already an
                // error bubble with Retry on it; there is nothing here to navigate to.
                return@launch
            }
            if (result is SendResult.NeedsReview) reviewNav.emit(result.messageId)
        }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}
