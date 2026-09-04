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

class ChatMessageAdapter(
    private val markwon: Markwon,
    private val onReview: (ChatMessage) -> Unit,
    private val onRetry: () -> Unit,
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).role == ChatMessage.ROLE_USER) TYPE_USER else TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(ItemChatUserBinding.inflate(inflater, parent, false))
        } else {
            AssistantHolder(ItemChatAssistantBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is UserHolder -> holder.bind(item)
            is AssistantHolder -> holder.bind(item, markwon, onReview, onRetry)
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
            item: ChatMessage,
            markwon: Markwon,
            onReview: (ChatMessage) -> Unit,
            onRetry: () -> Unit,
        ) {
            markwon.setMarkdown(binding.bubbleText, item.text)
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

    private object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
    }
}
