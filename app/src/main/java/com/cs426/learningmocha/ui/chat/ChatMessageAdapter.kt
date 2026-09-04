package com.cs426.learningmocha.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.ChatMessage
import com.cs426.learningmocha.databinding.ItemChatAssistantBinding
import com.cs426.learningmocha.databinding.ItemChatUserBinding
import io.noties.markwon.Markwon

/** A row of the conversation: a stored message, or the reply currently streaming in. */
sealed class ChatRow {
    abstract val key: Long

    data class Message(val message: ChatMessage, val sharedNotes: Int = 0) : ChatRow() {
        override val key: Long get() = message.id
    }

    data class Streaming(val text: String, val working: Boolean) : ChatRow() {
        override val key: Long get() = STREAMING_KEY
    }

    companion object {
        /** Row ids are Room ids (always positive), so the live bubble can own a fixed one. */
        const val STREAMING_KEY = -1L
    }
}

class ChatMessageAdapter(
    private val markwon: Markwon,
    private val onReview: (ChatMessage) -> Unit,
    private val onRetry: () -> Unit,
) : ListAdapter<ChatRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (val row = getItem(position)) {
        is ChatRow.Streaming -> TYPE_STREAMING
        is ChatRow.Message ->
            if (row.message.role == ChatMessage.ROLE_USER) TYPE_USER else TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserHolder(ItemChatUserBinding.inflate(inflater, parent, false))
            TYPE_STREAMING -> StreamingHolder(ItemChatAssistantBinding.inflate(inflater, parent, false))
            else -> AssistantHolder(ItemChatAssistantBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ChatRow.Message -> when (holder) {
                is UserHolder -> holder.bind(row.message)
                is AssistantHolder -> holder.bind(row, markwon, onReview, onRetry)
                else -> Unit
            }
            is ChatRow.Streaming -> (holder as? StreamingHolder)?.bind(row, markwon)
        }
    }

    class UserHolder(private val binding: ItemChatUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatMessage) {
            binding.bubbleText.text = item.text
        }
    }

    class AssistantHolder(
        private val binding: ItemChatAssistantBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            row: ChatRow.Message,
            markwon: Markwon,
            onReview: (ChatMessage) -> Unit,
            onRetry: () -> Unit,
        ) {
            val item = row.message
            markwon.setMarkdown(binding.bubbleText, item.text)
            if (row.sharedNotes > 0) {
                binding.bubbleContext.isVisible = true
                binding.bubbleContext.text = binding.root.resources.getQuantityString(
                    R.plurals.chat_shared_with_ai,
                    row.sharedNotes,
                    row.sharedNotes,
                )
            } else {
                binding.bubbleContext.isVisible = false
            }
            when (item.status) {
                ChatMessage.STATUS_PENDING -> {
                    binding.bubbleAction.isVisible = true
                    binding.bubbleAction.setText(R.string.chat_review_action)
                    binding.bubbleAction.setOnClickListener { onReview(item) }
                }
                ChatMessage.STATUS_APPLIED -> {
                    binding.bubbleAction.isVisible = true
                    binding.bubbleAction.setText(R.string.chat_applied)
                    binding.bubbleAction.setOnClickListener(null)
                }
                ChatMessage.STATUS_DISCARDED -> {
                    binding.bubbleAction.isVisible = true
                    binding.bubbleAction.setText(R.string.chat_discarded)
                    binding.bubbleAction.setOnClickListener(null)
                }
                ChatMessage.STATUS_ERROR -> {
                    binding.bubbleAction.isVisible = true
                    binding.bubbleAction.setText(R.string.chat_retry_action)
                    binding.bubbleAction.setOnClickListener { onRetry() }
                }
                else -> {
                    binding.bubbleAction.isVisible = false
                    binding.bubbleAction.setOnClickListener(null)
                }
            }
        }
    }

    /** The live reply. Its own view type, so it never recycles into a stored bubble. */
    class StreamingHolder(
        private val binding: ItemChatAssistantBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ChatRow.Streaming, markwon: Markwon) {
            binding.bubbleText.isVisible = row.text.isNotBlank()
            if (row.text.isNotBlank()) markwon.setMarkdown(binding.bubbleText, row.text)
            binding.bubbleStatus.isVisible = true
            binding.bubbleStatus.setText(
                if (row.working) R.string.chat_stream_working else R.string.chat_stream_typing,
            )
            binding.bubbleContext.isVisible = false
            binding.bubbleAction.isVisible = false
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatRow>() {
        override fun areItemsTheSame(oldItem: ChatRow, newItem: ChatRow): Boolean =
            oldItem.key == newItem.key && oldItem.javaClass == newItem.javaClass

        override fun areContentsTheSame(oldItem: ChatRow, newItem: ChatRow): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_STREAMING = 2
    }
}
