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
import androidx.recyclerview.widget.LinearLayoutManager
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.FragmentChatBinding
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.ui.common.RowAdapter
import com.cs426.learningmocha.ui.common.RowItem
import com.cs426.learningmocha.ui.common.SwipeToDelete
import com.cs426.learningmocha.viewmodel.ChatListViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ChatFragment : Fragment() {

    private var binding: FragmentChatBinding? = null
    private val viewModel: ChatListViewModel by viewModels()
    private val adapter = RowAdapter(::openSession) { item -> confirmDelete(item) }
    private val dateFormat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentChatBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.chatList.layoutManager = LinearLayoutManager(requireContext())
        b.chatList.adapter = adapter
        // Long-press still deletes too. The swipe is faster once it is known about, but nothing
        // on a row advertises it, so the menu stays as the discoverable path.
        SwipeToDelete.attach(b.chatList) { position, restore ->
            val item = adapter.currentList.getOrNull(position)
            if (item == null) restore() else confirmDelete(item, restore)
        }
        b.chatFab.setOnClickListener {
            viewModel.createSession { id -> openSessionId(id) }
        }
        b.chatBannerRetry.setOnClickListener { viewModel.ping() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ping()
                viewModel.uiState.collect { state ->
                    b.chatBanner.isVisible = !state.online
                    ListStateBinder.bind(
                        overlay = b.listState.root,
                        progress = b.listState.listStateProgress,
                        message = b.listState.listStateMessage,
                        retry = b.listState.listStateRetry,
                        content = b.chatList,
                        state = state.listState,
                        emptyText = getString(R.string.chat_empty),
                        errorText = state.errorMessage,
                        offlineText = getString(R.string.chat_offline),
                        onRetry = { viewModel.ping() },
                    )
                    adapter.submitList(
                        state.sessions.map { session ->
                            RowItem(session.id, session.title, dateFormat.format(Date(session.createdAt)))
                        },
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun openSession(item: RowItem) = openSessionId(item.key)

    private fun openSessionId(id: Long) {
        findNavController().navigate(
            R.id.action_global_chat_conversation,
            bundleOf("sessionId" to id),
        )
    }

    /**
     * @param onCancel puts a swiped row back. Wired to dismiss as well as cancel, because a
     *   tap outside the dialog would otherwise leave the chat swiped off screen but undeleted.
     */
    private fun confirmDelete(item: RowItem, onCancel: (() -> Unit)? = null): Boolean {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_delete_title)
            .setMessage(R.string.chat_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteSession(item.key) }
            .setNegativeButton(R.string.action_cancel) { _, _ -> onCancel?.invoke() }
            .setOnCancelListener { onCancel?.invoke() }
            .show()
        return true
    }
}
