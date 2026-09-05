package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.ai.chat.SendResult
import com.cs426.learningmocha.ai.chat.StreamingBubble
import com.cs426.learningmocha.data.local.entity.ChatMessage
import com.cs426.learningmocha.ui.chat.ChatModes
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
    /** Wire mode for this conversation: "answer", or the action modes joined by "+". */
    val mode: String = ChatModes.ANSWER,
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

    private val mode = MutableStateFlow(ChatModes.ANSWER)
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

    val reviewNav = MutableSharedFlow<SendResult.NeedsReview>(extraBufferCapacity = 1)
    val modeOffer = MutableSharedFlow<String>(extraBufferCapacity = 1)

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

    /**
     * What the message about to be sent looks like it needs, or null when the picked mode
     * already fits. The screen asks before sending; nothing here changes the mode by itself.
     */
    fun suggestedModeFor(text: String): String? {
        if (!app.settings.suggestChatMode) return null
        return com.cs426.learningmocha.ui.chat.ChatModeHint.suggest(mode.value, text)
    }

    /** Stops the mode prompt for good, from the "do not ask again" box on that dialog. */
    fun stopSuggestingModes() {
        app.settings.suggestChatMode = false
    }

    fun ping() {
        viewModelScope.launch { online.value = app.chatRepository.ping() }
    }

    suspend fun sessionTitle(): String =
        app.chatRepository.getSession(sessionId)?.title.orEmpty()

    /**
     * @param overrideMode set when the user accepted a suggested switch, so the message goes
     *   under the mode they just agreed to rather than the one the chips still showed
     */
    fun send(text: String, overrideMode: String? = null) {
        if (app.chatRepository.inFlight(sessionId) != null) return
        overrideMode?.let { mode.value = it }
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
            if (result is SendResult.NeedsReview) reviewNav.emit(result)
            if (result is SendResult.SuggestMode) modeOffer.emit(result.suggested)
        }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}
