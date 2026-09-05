package com.cs426.learningmocha.ai.chat

import com.cs426.learningmocha.ai.engine.ActionExecutor
import com.cs426.learningmocha.ai.engine.ContextTools
import com.cs426.learningmocha.ai.engine.KbIndex
import com.cs426.learningmocha.ai.engine.UndoSnapshot
import com.cs426.learningmocha.ai.protocol.ActionParser
import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.local.SeedData
import com.cs426.learningmocha.data.local.entity.ChatMessage
import com.cs426.learningmocha.data.local.entity.ChatSession
import com.cs426.learningmocha.data.repo.PostRepository
import com.cs426.learningmocha.data.repo.SearchRepository
import com.cs426.learningmocha.net.ApiError
import com.cs426.learningmocha.net.ChatMessageDto
import com.cs426.learningmocha.net.ChatRequest
import com.cs426.learningmocha.net.ChatResponse
import com.cs426.learningmocha.net.HealthResponse
import com.cs426.learningmocha.net.MochaApi
import com.cs426.learningmocha.net.SseChatClient
import com.cs426.learningmocha.net.StreamFrame
import com.cs426.learningmocha.util.StreamingAnswerExtractor
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class SendResult {
    data object Answered : SendResult()
    data class NeedsReview(val messageId: Long, val suggestedMode: String? = null) : SendResult()
    data class SuggestMode(val suggested: String) : SendResult()
    data class Failed(val message: String, val retryable: Boolean) : SendResult()
}

/**
 * The reply currently arriving over SSE. Transient by design: it is never written
 * to `chat_messages`, so a stream that is cancelled or dies mid-flight leaves no
 * half-finished row behind.
 *
 * [working] means there is no user-visible prose yet — the envelope is still
 * opening, or it turned out to be an action batch / context request, which the
 * user never sees as raw JSON.
 */
data class StreamingBubble(
    val sessionId: Long,
    val text: String,
    val working: Boolean,
)

class ChatRepository(
    private val db: AppDatabase,
    private val api: MochaApi,
    private val sse: SseChatClient,
    search: SearchRepository,
    posts: PostRepository,
    val executor: ActionExecutor,
    private val userProfile: () -> String? = { null },
) {
    private val chat = db.chatDao()
    private val tools = ContextTools(db, search, posts)
    private val gson = Gson()

    /**
     * Sends run here, not on the caller's scope. A reply takes 10–20 s and the conversation
     * screen is often gone long before it lands; on a ViewModel scope that cancelled the
     * call and left the user's message sitting there with no answer and no way to ask
     * again. This repository lives as long as the process, so nothing cancels this scope.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The send in flight per session. Guarded by its own monitor; touched from any thread. */
    private val running = HashMap<Long, Deferred<SendResult>>()

    private val _busySessions = MutableStateFlow<Set<Long>>(emptySet())

    /** Sessions still generating a reply, so a screen re-opened mid-reply still looks busy. */
    val busySessions: StateFlow<Set<Long>> = _busySessions.asStateFlow()

    private val _streaming = MutableStateFlow<StreamingBubble?>(null)
    val streaming: StateFlow<StreamingBubble?> = _streaming.asStateFlow()

    private val _sharedContext = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /**
     * How many notes the assistant read before answering, per message id (plan §22).
     * Kept in memory rather than in Room: persisting it would mean a schema
     * migration, and chat history is deliberately never exported anyway.
     */
    val sharedContext: StateFlow<Map<Long, Int>> = _sharedContext.asStateFlow()

    @Volatile
    var lastUndo: UndoSnapshot? = null

    fun observeSessions(): Flow<List<ChatSession>> = chat.observeSessions()

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> = chat.observeMessages(sessionId)

    suspend fun getMessage(id: Long): ChatMessage? = chat.getMessage(id)

    suspend fun getSession(id: Long): ChatSession? = chat.getSession(id)

    suspend fun createSession(): Long =
        chat.insertSession(ChatSession(title = "New chat"))

    suspend fun deleteSession(id: Long) = chat.deleteSession(id)

    suspend fun health(): HealthResponse? = try {
        api.health()
    } catch (_: Exception) {
        null
    }

    suspend fun ping(): Boolean = health()?.ok == true

    /**
     * Starts a send on this repository's scope and hands back the running job. The caller
     * awaits it only to learn where to navigate — dropping that wait never cancels the
     * generation, so leaving the screen mid-reply still ends with a persisted answer.
     * A session already generating keeps the job it has instead of starting a second one.
     */
    fun sendAsync(sessionId: Long, mode: String, userText: String): Deferred<SendResult> =
        startExclusive(sessionId) { send(sessionId, mode, userText) }

    fun retryAsync(sessionId: Long, mode: String): Deferred<SendResult> =
        startExclusive(sessionId) { retry(sessionId, mode) }

    /** The send still running for [sessionId], for a screen that wants to await it again. */
    fun inFlight(sessionId: Long): Deferred<SendResult>? =
        synchronized(running) { running[sessionId]?.takeIf { it.isActive } }

    private fun startExclusive(
        sessionId: Long,
        block: suspend () -> SendResult,
    ): Deferred<SendResult> = synchronized(running) {
        val current = running[sessionId]
        if (current != null && current.isActive) {
            current
        } else {
            val job = scope.async { block() }
            running[sessionId] = job
            _busySessions.update { it + sessionId }
            job.invokeOnCompletion { _ ->
                synchronized(running) { if (running[sessionId] === job) running.remove(sessionId) }
                _busySessions.update { it - sessionId }
            }
            job
        }
    }

    suspend fun send(sessionId: Long, mode: String, userText: String): SendResult {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return SendResult.Failed("Empty message", false)
        chat.insertMessage(
            ChatMessage(
                sessionId = sessionId,
                role = ChatMessage.ROLE_USER,
                text = trimmed,
                mode = mode,
            ),
        )
        maybeTitle(sessionId, trimmed)
        return complete(sessionId, mode)
    }

    suspend fun retry(sessionId: Long, mode: String): SendResult {
        val messages = chat.getMessages(sessionId)
        val last = messages.lastOrNull() ?: return SendResult.Failed("Nothing to retry", false)
        if (last.role == ChatMessage.ROLE_ASSISTANT && last.status != ChatMessage.STATUS_APPLIED) {
            chat.deleteMessage(last.id)
        }
        if (messages.none { it.role == ChatMessage.ROLE_USER }) {
            return SendResult.Failed("Nothing to retry", false)
        }
        return complete(sessionId, mode)
    }

    suspend fun markApplied(messageId: Long) {
        val message = chat.getMessage(messageId) ?: return
        chat.updateMessage(message.copy(status = ChatMessage.STATUS_APPLIED))
    }

    suspend fun markDiscarded(messageId: Long) {
        val message = chat.getMessage(messageId) ?: return
        chat.updateMessage(message.copy(status = ChatMessage.STATUS_DISCARDED))
    }

    suspend fun undoLast() {
        val snap = lastUndo ?: return
        lastUndo = null
        executor.undo(snap)
    }

    private suspend fun complete(sessionId: Long, mode: String): SendResult {
        val health = health()
        if (health == null || !health.ok) {
            return persistError(sessionId, mode, "AI unavailable — your library still works", true)
        }
        SeedData.ensureSeeded(db)
        val history = chat.getMessages(sessionId)
            .filter { it.role == ChatMessage.ROLE_USER || it.status == ChatMessage.STATUS_OK }
            .map { ChatMessageDto(it.role, it.text) }
        if (history.none { it.role == ChatMessage.ROLE_USER }) {
            return SendResult.Failed("Nothing to send", false)
        }
        val kbIndex = KbIndex.build(db.nodeDao().getAll())
        val roundMessages = history.toMutableList()
        var toolResults: String? = null
        var sharedNotes = 0
        return try {
            repeat(4) { round ->
                val reply = roundReply(
                    sessionId = sessionId,
                    mode = mode,
                    messages = roundMessages,
                    kbIndex = kbIndex,
                    toolResults = toolResults,
                    streaming = health.streaming,
                )
                val envelope = ActionParser.parseOrAnswer(reply)
                when (envelope.type) {
                    "context_request" -> {
                        if (round >= 3) {
                            return persistError(sessionId, mode, "Could not gather enough context", true)
                        }
                        val queries = envelope.queries.orEmpty()
                        toolResults = tools.run(queries)
                        sharedNotes += minOf(queries.size, CONTEXT_QUERY_CAP)
                        roundMessages.add(ChatMessageDto(ChatMessage.ROLE_ASSISTANT, reply))
                    }
                    "actions" -> {
                        val suggested = envelope.suggestMode?.trim()?.takeIf { it.isNotEmpty() }
                            ?: if (mode == MODE_ANSWER) "modify" else null
                        val id = chat.insertMessage(
                            ChatMessage(
                                sessionId = sessionId,
                                role = ChatMessage.ROLE_ASSISTANT,
                                text = envelope.summary ?: "Proposed changes",
                                actionsJson = reply,
                                status = ChatMessage.STATUS_PENDING,
                                mode = mode,
                            ),
                        )
                        recordShared(id, sharedNotes)
                        return SendResult.NeedsReview(
                            id,
                            suggestedMode = suggested.takeIf { mode == MODE_ANSWER },
                        )
                    }
                    else -> {
                        // Two ways a reply can carry nothing worth showing: an envelope of an
                        // unknown type (whose raw JSON must never reach a bubble), and a
                        // degenerate answer whose text is blank — DeepSeek occasionally emits a
                        // long run of spaces instead of prose. Both become a retryable error
                        // rather than an empty bubble the user cannot act on.
                        val prose = envelope.text ?: reply
                        if (prose.isBlank()) {
                            return persistError(
                                sessionId,
                                mode,
                                "The assistant replied with nothing usable — try again",
                                true,
                            )
                        }
                        val id = chat.insertMessage(
                            ChatMessage(
                                sessionId = sessionId,
                                role = ChatMessage.ROLE_ASSISTANT,
                                text = prose,
                                status = ChatMessage.STATUS_OK,
                                mode = mode,
                            ),
                        )
                        recordShared(id, sharedNotes)
                        val suggested = envelope.suggestMode?.trim()?.takeIf { it.isNotEmpty() }
                        if (suggested != null && mode == MODE_ANSWER) {
                            return SendResult.SuggestMode(suggested)
                        }
                        return SendResult.Answered
                    }
                }
            }
            persistError(sessionId, mode, "Could not gather enough context", true)
        } catch (cancelled: CancellationException) {
            // Not a failure and no longer something a departing screen causes, so it stays
            // distinct from the branches below: no error row, nothing to retry.
            throw cancelled
        } catch (error: ApiError) {
            persistError(sessionId, mode, error.message ?: "Request failed", error.retryable)
        } catch (error: Exception) {
            persistError(sessionId, mode, error.message ?: "Request failed", true)
        } finally {
            clearStreaming(sessionId)
        }
    }

    /**
     * One request/reply round. Streaming is preferred when the gateway advertises
     * it, but a stream that dies before the first token falls back to the buffered
     * route once — after tokens have landed a silent retry would duplicate a
     * half-rendered bubble, so the failure is surfaced instead.
     */
    private suspend fun roundReply(
        sessionId: Long,
        mode: String,
        messages: List<ChatMessageDto>,
        kbIndex: String,
        toolResults: String?,
        streaming: Boolean,
    ): String {
        if (!streaming) return postChat(mode, messages, kbIndex, toolResults)
        val buffer = StringBuilder()
        return try {
            streamChat(sessionId, buffer, ChatRequest(mode, messages, kbIndex, toolResults, userProfile()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (buffer.isNotEmpty()) throw error
            postChat(mode, messages, kbIndex, toolResults)
        }
    }

    private suspend fun streamChat(
        sessionId: Long,
        buffer: StringBuilder,
        request: ChatRequest,
    ): String {
        publishStreaming(sessionId, "")
        var fullReply: String? = null
        sse.stream(request).collect { frame ->
            when (frame) {
                is StreamFrame.Delta -> {
                    buffer.append(frame.text)
                    publishStreaming(sessionId, buffer.toString())
                }
                is StreamFrame.Done -> fullReply = frame.reply
                is StreamFrame.Failure -> throw ApiError(frame.message, frame.retryable)
            }
        }
        // No `done` frame means the connection dropped mid-generation.
        return fullReply ?: throw ApiError("The reply stopped early — try again", true)
    }

    /**
     * Publishes the growing reply. The envelope is still JSON on the wire, so the
     * visible prose is decoded out of the partial `text` field rather than shown raw.
     */
    private fun publishStreaming(sessionId: Long, buffer: String) {
        val kind = StreamingAnswerExtractor.kind(buffer)
        val prose = kind == StreamingAnswerExtractor.KIND_ANSWER ||
            kind == StreamingAnswerExtractor.KIND_PROSE
        val text = StreamingAnswerExtractor.partialAnswerText(buffer)
        _streaming.value = StreamingBubble(sessionId, text, !prose || text.isBlank())
    }

    private fun clearStreaming(sessionId: Long) {
        if (_streaming.value?.sessionId == sessionId) _streaming.value = null
    }

    private fun recordShared(messageId: Long, notes: Int) {
        if (notes <= 0) return
        _sharedContext.value = _sharedContext.value + (messageId to notes)
    }

    private suspend fun persistError(
        sessionId: Long,
        mode: String,
        text: String,
        retryable: Boolean,
    ): SendResult {
        chat.insertMessage(
            ChatMessage(
                sessionId = sessionId,
                role = ChatMessage.ROLE_ASSISTANT,
                text = text,
                status = ChatMessage.STATUS_ERROR,
                mode = mode,
            ),
        )
        return SendResult.Failed(text, retryable)
    }

    private suspend fun maybeTitle(sessionId: Long, userText: String) {
        val session = chat.getSession(sessionId) ?: return
        if (session.title != "New chat") return
        val title = userText.lineSequence().first().trim().take(40)
        if (title.isNotEmpty()) chat.renameSession(sessionId, title)
    }

    private suspend fun postChat(
        mode: String,
        messages: List<ChatMessageDto>,
        kbIndex: String,
        toolResults: String?,
    ): String {
        val response = api.chat(ChatRequest(mode, messages, kbIndex, toolResults, userProfile()))
        if (response.isSuccessful) {
            val body = response.body() ?: throw ApiError("Empty reply", true)
            if (!body.error.isNullOrBlank()) {
                throw ApiError(body.error, body.retryable == true)
            }
            return body.reply.orEmpty()
        }
        val parsed = try {
            gson.fromJson(response.errorBody()?.string(), ChatResponse::class.java)
        } catch (_: Exception) {
            null
        }
        throw ApiError(
            parsed?.error ?: "HTTP ${response.code()}",
            // The gateway already normalized retryability; only guess when the
            // body is not its JSON (a proxy's HTML error page, say).
            parsed?.retryable ?: (response.code() >= 500 || response.code() == 429),
        )
    }

    private companion object {
        /** Mirrors the cap in [ContextTools.run], so the shared-notes count matches reality. */
        const val CONTEXT_QUERY_CAP = 8

        /**
         * The one mode that may not propose changes, and the one that cannot be combined
         * with the others (see `backend/prompts.js`).
         */
        const val MODE_ANSWER = "answer"
    }
}
