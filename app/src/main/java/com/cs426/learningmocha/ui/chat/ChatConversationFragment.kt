package com.cs426.learningmocha.ui.chat

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.cs426.learningmocha.databinding.DialogModeSwitchBinding
import com.cs426.learningmocha.databinding.FragmentChatConversationBinding
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.ui.common.NodePalette
import com.cs426.learningmocha.ui.common.WikiMarkdown
import com.cs426.learningmocha.ui.common.themeColor
import com.cs426.learningmocha.viewmodel.ChatConversationUiState
import com.cs426.learningmocha.viewmodel.ChatConversationViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch

class ChatConversationFragment : Fragment() {

    private var binding: FragmentChatConversationBinding? = null
    private val viewModel: ChatConversationViewModel by viewModels()
    private var adapter: ChatMessageAdapter? = null

    /** The three combinable action chips; Answer is handled apart because it is exclusive. */
    private var modeChips: Map<Chip, String> = emptyMap()

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
        val messages = ChatMessageAdapter(buildMarkwon(), ::openReview, viewModel::retry)
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

        modeChips = mapOf(
            b.chipSuggest to ChatModes.SUGGEST,
            b.chipModify to ChatModes.MODIFY,
            b.chipOrganize to ChatModes.ORGANIZE,
        )
        b.chipAnswer.setOnClickListener { pickMode(ChatModes.ANSWER, checked = true) }
        modeChips.forEach { (chip, mode) ->
            chip.setOnClickListener { pickMode(mode, chip.isChecked) }
        }
        paintModeChip(b.chipAnswer, ChatModes.ANSWER)
        modeChips.forEach { (chip, mode) -> paintModeChip(chip, mode) }
        applyModeChips(ChatModes.ANSWER)

        viewLifecycleOwner.lifecycleScope.launch {
            b.conversationTitle.text = viewModel.sessionTitle()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ping()
                viewModel.refreshTitles()
                launch {
                    viewModel.reviewNav.collect { offer ->
                        if (offer.suggestedMode != null) {
                            askAboutReview(offer.suggestedMode, offer.messageId)
                        } else {
                            openReviewId(offer.messageId)
                        }
                    }
                }
                launch {
                    viewModel.modeOffer.collect { suggested ->
                        askAboutRetry(suggested)
                    }
                }
                viewModel.uiState.collect { state ->
                    b.conversationBanner.isVisible = !state.online
                    b.conversationSending.isVisible = state.sending
                    // Deliberately not gated on `state.online`. The health check is a hint, not
                    // a fact: one slow or dropped ping used to leave Send greyed out with no way
                    // back except the banner's Retry. Sending while the gateway is really down
                    // costs one error bubble, which already carries its own Retry.
                    b.conversationSend.isEnabled = !state.sending
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
        modeChips = emptyMap()
        binding = null
    }

    private fun send() {
        val b = binding ?: return
        val text = b.conversationInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        // Asked before the message is cleared, so declining leaves the screen exactly as it
        // was and the user can still edit what they wrote.
        val suggested = viewModel.suggestedModeFor(text)
        if (suggested != null) {
            askAboutMode(suggested, text)
            return
        }
        dispatch(text, null)
    }

    /**
     * Offers the mode the message looks like it needs. Both buttons send: the choice is which
     * mode to send under, never whether to send at all, so declining costs nothing.
     */
    private fun askAboutMode(suggested: String, text: String) {
        val fields = DialogModeSwitchBinding.inflate(layoutInflater)
        fields.modeSwitchMessage.text = getString(
            R.string.chat_mode_switch_message,
            modeLabel(viewModel.uiState.value.mode),
            modeLabel(suggested),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_mode_switch_title)
            .setView(fields.root)
            .setNegativeButton(R.string.chat_mode_switch_keep) { _, _ ->
                if (fields.modeSwitchRemember.isChecked) viewModel.stopSuggestingModes()
                dispatch(text, null)
            }
            .setPositiveButton(R.string.chat_mode_switch_accept) { _, _ ->
                if (fields.modeSwitchRemember.isChecked) viewModel.stopSuggestingModes()
                applyModeChips(suggested)
                dispatch(text, suggested)
            }
            .show()
    }

    /** Clears the box only once the message is really on its way; see [ChatConversationViewModel.send]. */
    private fun dispatch(text: String, overrideMode: String?) {
        val b = binding ?: return
        if (!viewModel.send(text, overrideMode)) {
            Toast.makeText(requireContext(), R.string.chat_still_replying, Toast.LENGTH_SHORT)
                .show()
            return
        }
        b.conversationInput.text = null
        viewLifecycleOwner.lifecycleScope.launch {
            b.conversationTitle.text = viewModel.sessionTitle()
        }
    }

    /**
     * Applies the exclusivity a ChipGroup cannot express: the three action modes combine with
     * each other, Answer clears them, and unchecking the last action chip falls back to Answer
     * rather than leaving the conversation with no mode at all.
     */
    private fun pickMode(mode: String, checked: Boolean) {
        val current = ChatModes.parse(viewModel.uiState.value.mode).toMutableSet()
        if (mode == ChatModes.ANSWER) {
            current.clear()
        } else {
            current.remove(ChatModes.ANSWER)
            if (checked) current.add(mode) else current.remove(mode)
        }
        val next = ChatModes.join(current)
        viewModel.setMode(next)
        applyModeChips(next)
    }

    /**
     * Gives each mode chip the colour its replies are already drawn in — green Answer, amber
     * Suggest, blue Modify, violet Organize — taken from the same [NodePalette] the bubbles and
     * their mode pills use. The row was four identical brown chips before, which made the one
     * control that changes what the assistant is allowed to do the least visible thing on the
     * screen, and left the bubble colours looking arbitrary.
     *
     * The ink stays on the outline and the label whether or not the chip is checked, so the row
     * reads as a colour key at rest; checking one fills it with the matching wash instead of
     * inventing a selected colour of its own.
     */
    private fun paintModeChip(chip: Chip, mode: String) {
        val context = chip.context
        val ink = context.themeColor(NodePalette.modeInk(mode))
        val wash = context.themeColor(NodePalette.modeWash(mode))
        val surface = context.themeColor(R.attr.mochaSurface)
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        )
        chip.chipBackgroundColor = ColorStateList(states, intArrayOf(wash, surface))
        chip.chipStrokeColor = ColorStateList.valueOf(ink)
        chip.setTextColor(ink)
        chip.checkedIconTint = ColorStateList.valueOf(ink)
    }

    private fun applyModeChips(mode: String) {
        val b = binding ?: return
        val parts = ChatModes.parse(mode)
        b.chipAnswer.isChecked = ChatModes.ANSWER in parts
        modeChips.forEach { (chip, value) -> chip.isChecked = value in parts }
    }

    private fun modeLabel(mode: String): String =
        ChatModes.parse(mode).joinToString(" + ") {
            getString(NodePalette.modeLabelRes(it))
        }

    private fun buildRows(state: ChatConversationUiState): List<ChatRow> {
        val rows = state.messages.map { message ->
            // Only the assistant writes [[wiki-links]]; a user bubble is drawn as plain text
            // anyway, so rewriting it would be work nothing reads.
            val markdown = if (message.role == ChatMessage.ROLE_USER) {
                message.text
            } else {
                WikiMarkdown.rewrite(message.text, state.titleToId)
            }
            ChatRow.Message(message, markdown, state.sharedContext[message.id] ?: 0)
        }
        val bubble = state.streaming ?: return rows
        // The live bubble has no stored row yet, so it borrows the mode it was sent under.
        return rows + ChatRow.Streaming(
            WikiMarkdown.rewrite(bubble.text, state.titleToId),
            bubble.working,
            state.mode,
        )
    }

    /**
     * The same link handling the reader uses, so a `[[Post Title]]` the assistant writes opens
     * that post from the conversation instead of sitting there as bracketed text. A link to a
     * post that does not exist yet says so rather than doing nothing.
     */
    private fun buildMarkwon(): Markwon {
        val defaults = LinkResolverDef()
        return Markwon.builder(requireContext())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { view, link ->
                        when {
                            link.startsWith(WikiMarkdown.POST_PREFIX) -> {
                                val id = link.removePrefix(WikiMarkdown.POST_PREFIX).toLongOrNull()
                                if (id != null) {
                                    findNavController().navigate(
                                        R.id.action_global_open_post,
                                        bundleOf("postId" to id),
                                    )
                                }
                            }
                            link.startsWith(WikiMarkdown.MISSING_PREFIX) -> {
                                val title = Uri.decode(
                                    link.removePrefix(WikiMarkdown.MISSING_PREFIX),
                                )
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.reader_link_missing, title),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            else -> defaults.resolve(view, link)
                        }
                    }
                }
            })
            .build()
    }

    private fun openReview(message: ChatMessage) {
        openReviewId(message.id)
    }

    private fun openReviewId(messageId: Long) {
        findNavController().navigate(
            R.id.action_global_review_changes,
            bundleOf("messageId" to messageId),
        )
    }

    private fun actionModeOrNull(raw: String): String? {
        val joined = ChatModes.join(ChatModes.parse(raw))
        return joined.takeIf { ChatModes.proposesChanges(it) }
    }

    private fun askAboutReview(suggested: String, messageId: Long) {
        val mode = actionModeOrNull(suggested) ?: ChatModes.MODIFY
        val fields = DialogModeSwitchBinding.inflate(layoutInflater)
        fields.modeSwitchMessage.text = getString(
            R.string.chat_mode_switch_review_message,
            modeLabel(mode),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_mode_switch_title)
            .setView(fields.root)
            .setNegativeButton(R.string.chat_mode_switch_keep) { _, _ ->
                if (fields.modeSwitchRemember.isChecked) viewModel.stopSuggestingModes()
            }
            .setPositiveButton(R.string.chat_mode_switch_accept_review) { _, _ ->
                if (fields.modeSwitchRemember.isChecked) viewModel.stopSuggestingModes()
                applyModeChips(mode)
                viewModel.setMode(mode)
                openReviewId(messageId)
            }
            .show()
    }

    private fun askAboutRetry(suggested: String) {
        val mode = actionModeOrNull(suggested) ?: return
        val fields = DialogModeSwitchBinding.inflate(layoutInflater)
        fields.modeSwitchMessage.text = getString(
            R.string.chat_mode_switch_after_message,
            modeLabel(mode),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_mode_switch_after_title)
            .setView(fields.root)
            .setNegativeButton(R.string.chat_mode_switch_keep) { _, _ ->
                if (fields.modeSwitchRemember.isChecked) viewModel.stopSuggestingModes()
            }
            .setPositiveButton(R.string.chat_mode_switch_retry) { _, _ ->
                if (fields.modeSwitchRemember.isChecked) viewModel.stopSuggestingModes()
                applyModeChips(mode)
                viewModel.setMode(mode)
                viewModel.retry()
            }
            .show()
    }
}
