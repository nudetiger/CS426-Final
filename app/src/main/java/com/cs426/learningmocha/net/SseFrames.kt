package com.cs426.learningmocha.net

import com.google.gson.Gson
import com.google.gson.JsonParseException

/** One decoded `data:` line of the gateway's `POST /v1/chat/stream` response. */
sealed class StreamFrame {
    /** A text fragment of the reply, in arrival order. */
    data class Delta(val text: String) : StreamFrame()

    /** The terminal success frame; [reply] is the whole reply, authoritative for parsing. */
    data class Done(val reply: String) : StreamFrame()

    /** The terminal failure frame, already normalized by the gateway. */
    data class Failure(val message: String, val retryable: Boolean) : StreamFrame()
}

/**
 * Line-oriented decoder for the gateway's Server-Sent Events.
 *
 * Framework-free on purpose: the transport lives in [SseChatClient], so the wire
 * format itself can be covered by JVM unit tests.
 */
object SseFrames {

    private val gson = Gson()

    /**
     * Decodes one raw line of the stream.
     *
     * @return the frame it carries, or `null` for a blank line, an SSE comment
     *         (the gateway sends `: open` to flush headers early), the OpenAI
     *         `[DONE]` sentinel, or a payload that is not a JSON object.
     */
    fun parse(line: String): StreamFrame? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return null
        val payload = if (trimmed.startsWith(DATA_PREFIX)) {
            trimmed.removePrefix(DATA_PREFIX).trim()
        } else {
            trimmed
        }
        if (payload.isEmpty() || payload == DONE_SENTINEL) return null
        val frame = try {
            gson.fromJson(payload, Payload::class.java)
        } catch (_: JsonParseException) {
            null
        } ?: return null
        return when {
            !frame.error.isNullOrBlank() -> StreamFrame.Failure(frame.error, frame.retryable == true)
            frame.done == true -> StreamFrame.Done(frame.reply.orEmpty())
            frame.delta != null -> StreamFrame.Delta(frame.delta)
            else -> null
        }
    }

    private data class Payload(
        val delta: String? = null,
        val done: Boolean? = null,
        val reply: String? = null,
        val error: String? = null,
        val retryable: Boolean? = null,
    )

    private const val DATA_PREFIX = "data:"
    private const val DONE_SENTINEL = "[DONE]"
}
