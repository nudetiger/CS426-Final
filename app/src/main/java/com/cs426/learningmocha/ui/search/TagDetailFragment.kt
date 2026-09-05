package com.cs426.learningmocha.ui.search

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.FragmentNamedListBinding
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.ui.home.RecentPostAdapter
import com.cs426.learningmocha.viewmodel.TagDetailViewModel
import kotlinx.coroutines.launch

class TagDetailFragment : Fragment() {

    private var binding: FragmentNamedListBinding? = null
    private val viewModel: TagDetailViewModel by viewModels()
    private val adapter = RecentPostAdapter { openPost(it.id) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentNamedListBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.namedBack.setOnClickListener { findNavController().popBackStack() }
        b.namedList.layoutManager = LinearLayoutManager(requireContext())
        b.namedList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    b.namedTitle.text = state.title.ifBlank { getString(R.string.tag_title) }
                    ListStateBinder.bind(
                        overlay = b.listState.root,
                        progress = b.listState.listStateProgress,
                        message = b.listState.listStateMessage,
                        retry = b.listState.listStateRetry,
                        content = b.namedList,
                        state = state.listState,
                        emptyText = getString(R.string.tag_empty),
                        errorText = state.errorMessage,
                        offlineText = getString(R.string.state_offline),
                        onRetry = { viewModel.retry() },
                    )
                    adapter.submitList(state.posts)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun openPost(postId: Long) {
        findNavController().navigate(
            R.id.action_global_open_post,
            bundleOf("postId" to postId),
        )
    }
}
