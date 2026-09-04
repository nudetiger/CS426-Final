package com.cs426.learningmocha.ui.graph

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
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.FragmentGraphBinding
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.viewmodel.GraphUiState
import com.cs426.learningmocha.viewmodel.GraphViewModel
import kotlinx.coroutines.launch

/**
 * Force-directed view of the library: posts as dots, `[[wiki-links]]` (and optionally shared
 * tags) as edges. Reached from Search, or from a post with `focusPostId` set to draw only that
 * post's neighbourhood.
 */
class GraphFragment : Fragment() {

    private var binding: FragmentGraphBinding? = null
    private val viewModel: GraphViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentGraphBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.graphBack.setOnClickListener { findNavController().popBackStack() }
        b.graphChipLinks.setOnClickListener { viewModel.setIncludeTags(false) }
        b.graphChipTags.setOnClickListener { viewModel.setIncludeTags(true) }
        b.graphChipAll.setOnClickListener { viewModel.widenToLibrary() }
        b.graphOpen.setOnClickListener {
            viewModel.uiState.value.selectedId?.let { openPost(it) }
        }

        b.graphView.onNodeTap = { viewModel.select(it) }
        b.graphView.onNodeOpen = { openPost(it) }
        b.graphView.onBackgroundTap = { viewModel.select(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(b, state) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(b: FragmentGraphBinding, state: GraphUiState) {
        val checked = if (state.includeTags) b.graphChipTags else b.graphChipLinks
        if (!checked.isChecked) checked.isChecked = true
        b.graphChipAll.isVisible = state.canWiden && state.focused
        b.graphCaption.text = caption(state)

        state.graph?.let { graph ->
            b.graphView.setGraph(
                nodes = graph.snapshot.nodes,
                edges = graph.snapshot.edges,
                positions = graph.positions,
                worldSize = graph.worldSize,
                focusIndex = graph.snapshot.focusIndex,
            )
        }
        b.graphView.setSelectedId(state.selectedId)

        val selected = state.graph?.snapshot?.nodes?.firstOrNull { it.id == state.selectedId }
        b.graphSelection.isVisible = selected != null && state.listState == ListState.CONTENT
        if (selected != null) {
            b.graphSelectedTitle.text = selected.title
            b.graphSelectedCaption.text = if (selected.degree == 0) {
                getString(R.string.graph_selected_caption_alone)
            } else {
                getString(R.string.graph_selected_caption, selected.degree)
            }
        }

        ListStateBinder.bind(
            overlay = b.listState.root,
            progress = b.listState.listStateProgress,
            message = b.listState.listStateMessage,
            retry = b.listState.listStateRetry,
            content = b.graphView,
            state = state.listState,
            emptyText = getString(
                if (state.focused) R.string.graph_empty_focused else R.string.graph_empty,
            ),
            errorText = state.errorMessage ?: getString(R.string.graph_error),
            offlineText = getString(R.string.state_offline),
            onRetry = { viewModel.retry() },
        )
    }

    private fun caption(state: GraphUiState): String {
        val snapshot = state.graph?.snapshot ?: return ""
        val nodeCount = snapshot.nodes.size
        val edgeCount = snapshot.edges.size
        val focus = snapshot.nodes.getOrNull(snapshot.focusIndex)
        return when {
            snapshot.truncated -> getString(
                R.string.graph_caption_capped,
                nodeCount,
                snapshot.candidateCount,
                edgeCount,
            )
            focus != null -> getString(
                R.string.graph_caption_focused,
                focus.title,
                nodeCount,
                edgeCount,
            )
            else -> getString(R.string.graph_caption, nodeCount, edgeCount)
        }
    }

    private fun openPost(postId: Long) {
        findNavController().navigate(
            R.id.action_global_open_post,
            bundleOf("postId" to postId),
        )
    }
}
