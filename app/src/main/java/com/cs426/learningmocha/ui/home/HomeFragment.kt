package com.cs426.learningmocha.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.FragmentHomeBinding
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.viewmodel.BrowseLocatorViewModel
import com.cs426.learningmocha.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var binding: FragmentHomeBinding? = null
    private val viewModel: HomeViewModel by viewModels()
    private val locator: BrowseLocatorViewModel by activityViewModels()

    private val continueAdapter = ContinueAdapter { openPost(it.id) }
    private val recentsAdapter = RecentPostAdapter { openPost(it.id) }
    private val branchesAdapter = BranchShortcutAdapter { openBranch(it.id) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentHomeBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.homeContinueList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.homeContinueList.adapter = continueAdapter
        b.homeRecentsList.layoutManager = LinearLayoutManager(requireContext())
        b.homeRecentsList.adapter = recentsAdapter
        b.homeBranchesList.layoutManager = LinearLayoutManager(requireContext())
        b.homeBranchesList.adapter = branchesAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    b.homeGreeting.setText(state.greetingRes)
                    b.homeLibraryCount.text = getString(R.string.home_library_count, state.postCount)
                    ListStateBinder.bind(
                        overlay = b.listState.root,
                        progress = b.listState.listStateProgress,
                        message = b.listState.listStateMessage,
                        retry = b.listState.listStateRetry,
                        content = b.homeContent,
                        state = state.listState,
                        emptyText = getString(R.string.home_empty),
                        errorText = state.errorMessage,
                        offlineText = getString(R.string.state_offline),
                        onRetry = { /* Flow retries on its own when DB recovers */ },
                    )
                    val showSections = state.listState == ListState.CONTENT
                    b.homeContinueHeader.isVisible = showSections
                    b.homeRecentsHeader.isVisible = showSections
                    b.homeBranchesHeader.isVisible = showSections
                    continueAdapter.submitList(state.continueReading)
                    recentsAdapter.submitList(state.recents)
                    branchesAdapter.submitList(state.branches)
                    b.homeContinueEmpty.isVisible =
                        showSections && state.continueReading.isEmpty()
                    b.homeContinueList.isVisible =
                        showSections && state.continueReading.isNotEmpty()
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

    private fun openBranch(branchId: Long) {
        locator.requestOpen(branchId)
        findNavController().navigate(R.id.browseFragment)
    }
}
