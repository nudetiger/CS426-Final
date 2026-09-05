package com.cs426.learningmocha.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val text: String,
    val actionsJson: String? = null,
    val status: String = STATUS_OK,
    /**
     * The chat mode this turn was sent in ("answer", "modify", "modify+organize", …).
     * Stored rather than derived so scrolling back through a conversation still shows what
     * each exchange was asking for, long after the mode chips have moved on.
     */
    val mode: String = MODE_ANSWER,
) {
    companion object {
        const val MODE_ANSWER = "answer"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val STATUS_OK = "ok"
        const val STATUS_ERROR = "error"
        const val STATUS_PENDING = "pending_review"
        const val STATUS_APPLIED = "applied"
        const val STATUS_DISCARDED = "discarded"
    }
}
