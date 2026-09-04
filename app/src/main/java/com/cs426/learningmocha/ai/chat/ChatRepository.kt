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
import com.cs426.learningmocha.net.MochaApi
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

sealed class SendResult {
    data object Answered : SendResult()
    data class NeedsReview(val messageId: Long) : SendResult()
    data class Failed(val message: String, val retryable: Boolean) : SendResult()
}

class ChatRepository(
    private val db: AppDatabase,
    private val api: MochaApi,
    search: SearchRepository,
    posts: PostRepository,
    val executor: ActionExecutor,
) {
    private val chat = db.chatDao()
    private val tools = ContextTools(db, search, posts)
    private val gson = Gson()

    @Volatile
    var lastUndo: UndoSnapshot? = null

    fun observeSessions(): Flow<List<ChatSession>> = chat.observeSessions()

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> = chat.observeMessages(sessionId)

    suspend fun getMessage(id: Long): ChatMessage? = chat.getMessage(id)

    suspend fun getSession(id: Long): ChatSession? = chat.getSession(id)

    suspend fun createSession(): Long =
        chat.insertSession(ChatSession(title = "New chat"))

    suspend fun deleteSession(id: Long) = chat.deleteSession(id)

    suspend fun ping(): Boolean = try {
        api.health().ok
    } catch (_: Exception) {
        false
    }

    suspend fun send(sessionId: Long, mode: String, userText: String): SendResult {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return SendResult.Failed("Empty message", false)
        chat.insertMessage(
            ChatMessage(sessionId = sessionId, role = ChatMessage.ROLE_USER, text = trimmed),
        )
        maybeTitle(sessionId, trimmed)
        return complete(sessionId, mode)
    }

    suspend fun retry(sessionId: Long, mode: String): SendResult {
        val messages = chat.getMessages(sessionId)
        val last = messages.lastOrNull() ?: return SendResult.Failed("Nothing to retry", false)
        if (last.status == ChatMessage.STATUS_ERROR) {
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
        if (!ping()) {
            return persistError(sessionId, "AI unavailable — your library still works", true)
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
        return try {
            repeat(4) { round ->
                val reply = postChat(mode, roundMessages, kbIndex, toolResults)
                val envelope = ActionParser.parseOrAnswer(reply)
                when (envelope.type) {
                    "context_request" -> {
                        if (round >= 3) {
                            return persistError(sessionId, "Could not gather enough context", true)
                        }
                        toolResults = tools.run(envelope.queries.orEmpty())
                        roundMessages.add(ChatMessageDto(ChatMessage.ROLE_ASSISTANT, reply))
                    }
                    "actions" -> {
                        val id = chat.insertMessage(
                            ChatMessage(
                                sessionId = sessionId,
                                role = ChatMessage.ROLE_ASSISTANT,
                                text = envelope.summary ?: "Proposed changes",
                                actionsJson = reply,
                                status = ChatMessage.STATUS_PENDING,
                            ),
                        )
                        return SendResult.NeedsReview(id)
                    }
                    else -> {
                        chat.insertMessage(
                            ChatMessage(
                                sessionId = sessionId,
                                role = ChatMessage.ROLE_ASSISTANT,
                                text = envelope.text ?: reply,
                                status = ChatMessage.STATUS_OK,
                            ),
                        )
                        return SendResult.Answered
                    }
                }
            }
            persistError(sessionId, "Could not gather enough context", true)
        } catch (error: ApiError) {
            persistError(sessionId, error.message ?: "Request failed", error.retryable)
        } catch (error: Exception) {
            persistError(sessionId, error.message ?: "Request failed", true)
        }
    }

    private suspend fun persistError(sessionId: Long, text: String, retryable: Boolean): SendResult {
        chat.insertMessage(
            ChatMessage(
                sessionId = sessionId,
                role = ChatMessage.ROLE_ASSISTANT,
                text = text,
                status = ChatMessage.STATUS_ERROR,
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
        val response = api.chat(ChatRequest(mode, messages, kbIndex, toolResults))
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
            parsed?.retryable == true || response.code() >= 500 || response.code() == 429,
        )
    }
}
