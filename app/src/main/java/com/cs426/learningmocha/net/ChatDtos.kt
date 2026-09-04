package com.cs426.learningmocha.net

data class ChatMessageDto(
    val role: String,
    val content: String,
)

data class ChatRequest(
    val mode: String,
    val messages: List<ChatMessageDto>,
    val kbIndex: String,
    val toolResults: String? = null,
)

data class ChatResponse(
    val reply: String? = null,
    val error: String? = null,
    val retryable: Boolean? = null,
)

data class HealthResponse(
    val ok: Boolean = false,
    val model: String? = null,
)
