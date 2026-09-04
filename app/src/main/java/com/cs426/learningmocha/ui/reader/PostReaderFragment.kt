package com.cs426.learningmocha.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.FragmentPostReaderBinding
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.viewmodel.PostReaderViewModel
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch

class PostReaderFragment : Fragment() {

    private var binding: FragmentPostReaderBinding? = null
    private val viewModel: PostReaderViewModel by viewModels()
    private var markwon: Markwon? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentPostReaderBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        markwon = Markwon.builder(requireContext())
            .usePlugin(LinkifyPlugin.create())
            .build()

        b.readerBack.setOnClickListener { findNavController().popBackStack() }
        b.readerEdit.setOnClickListener {
            findNavController().navigate(
                R.id.action_reader_to_editor,
                bundleOf(
                    "postId" to viewModel.postId,
                    "parentId" to -1L,
                ),
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    ListStateBinder.bind(
                        overlay = b.listState.root,
                        progress = b.listState.listStateProgress,
                        message = b.listState.listStateMessage,
                        retry = b.listState.listStateRetry,
                        content = b.readerContent,
                        state = state.listState,
                        emptyText = getString(R.string.reader_missing),
                        errorText = state.errorMessage,
                        offlineText = getString(R.string.state_offline),
                        onRetry = { },
                    )
                    val post = state.post
                    if (state.listState == ListState.CONTENT && post != null) {
                        b.readerTitle.text = post.title
                        markwon?.setMarkdown(b.readerBody, post.content.orEmpty())
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        markwon = null
    }
}
