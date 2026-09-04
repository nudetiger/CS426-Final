package com.cs426.learningmocha.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.ChatMessage
import com.cs426.learningmocha.databinding.FragmentChatConversationBinding
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.viewmodel.ChatConversationUiState
import com.cs426.learningmocha.viewmodel.ChatConversationViewModel
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch

class ChatConversationFragment : Fragment() {

    private var binding: FragmentChatConversationBinding? = null
    private val viewModel: ChatConversationViewModel by viewModels()
    private var adapter: ChatMessageAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentChatConversationBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        val markwon = Markwon.builder(requireContext())
            .usePlugin(LinkifyPlugin.create())
            .build()
        val messages = ChatMessageAdapter(markwon, ::openReview, viewModel::retry)
        adapter = messages
        b.conversationList.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        b.conversationList.adapter = messages
        // The streaming bubble is re-bound every few milliseconds as tokens arrive. The default
        // change animation cross-fades the old and new view, so those repaints stack up and the
        // growing text visibly draws over itself. Insert/remove animations stay on.
        (b.conversationList.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        b.conversationBack.setOnClickListener { findNavController().popBackStack() }
        b.conversationSend.setOnClickListener { send() }
        b.conversationBannerRetry.setOnClickListener { viewModel.ping() }

        b.conversationModes.setOnCheckedStateChangeListener { _, _ ->
            viewModel.setMode(
                when (b.conversationModes.checkedChipId) {
                    R.id.chip_suggest -> "suggest"
                    R.id.chip_modify -> "modify"
                    R.id.chip_organize -> "organize"
                    else -> "answer"
                },
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            b.conversationTitle.text = viewModel.sessionTitle()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ping()
                launch {
                    viewModel.reviewNav.collect { messageId ->
                        findNavController().navigate(
                            R.id.action_global_review_changes,
                            bundleOf("messageId" to messageId),
                        )
                    }
                }
                viewModel.uiState.collect { state ->
                    b.conversationBanner.isVisible = !state.online
                    b.conversationSending.isVisible = state.sending
                    b.conversationSend.isEnabled = !state.sending && state.online
                    b.conversationInput.isEnabled = !state.sending
                    if (state.title.isNotBlank()) b.conversationTitle.text = state.title
                    ListStateBinder.bind(
                        overlay = b.listState.root,
                        progress = b.listState.listStateProgress,
                        message = b.listState.listStateMessage,
                        retry = b.listState.listStateRetry,
                        content = b.conversationList,
                        state = state.listState,
                        emptyText = getString(R.string.chat_conversation_empty),
                        errorText = state.errorMessage,
                        offlineText = getString(R.string.chat_offline),
                        onRetry = { viewModel.ping() },
                    )
                    val rows = buildRows(state)
                    // Only follow the tail when the user is already there, so reading
                    // back through the conversation is not yanked forward by new tokens.
                    val atBottom = !b.conversationList.canScrollVertically(1)
                    messages.submitList(rows) {
                        if (atBottom && rows.isNotEmpty()) {
                            b.conversationList.scrollToPosition(rows.lastIndex)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
        binding = null
    }

    private fun send() {
        val b = binding ?: return
        val text = b.conversationInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        b.conversationInput.text = null
        viewModel.send(text)
        viewLifecycleOwner.lifecycleScope.launch {
            b.conversationTitle.text = viewModel.sessionTitle()
        }
    }

    private fun buildRows(state: ChatConversationUiState): List<ChatRow> {
        val rows = state.messages.map { message ->
            ChatRow.Message(message, state.sharedContext[message.id] ?: 0)
        }
        val bubble = state.streaming ?: return rows
        return rows + ChatRow.Streaming(bubble.text, bubble.working)
    }

    private fun openReview(message: ChatMessage) {
        findNavController().navigate(
            R.id.action_global_review_changes,
            bundleOf("messageId" to message.id),
        )
    }
}
