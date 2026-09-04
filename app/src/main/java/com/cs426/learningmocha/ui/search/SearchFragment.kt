package com.cs426.learningmocha.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.data.repo.SearchHit
import com.cs426.learningmocha.databinding.FragmentSearchBinding
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.ui.common.RowAdapter
import com.cs426.learningmocha.ui.common.RowItem
import com.cs426.learningmocha.viewmodel.BrowseLocatorViewModel
import com.cs426.learningmocha.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var binding: FragmentSearchBinding? = null
    private val viewModel: SearchViewModel by viewModels()
    private val locator: BrowseLocatorViewModel by activityViewModels()
    private val adapter = RowAdapter(::onHit)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentSearchBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.searchList.layoutManager = LinearLayoutManager(requireContext())
        b.searchList.adapter = adapter
        b.searchInput.doAfterTextChanged { viewModel.query.value = it?.toString().orEmpty() }

        b.searchChipPosts.setOnCheckedChangeListener { _, _ ->
            if (b.searchChipPosts.isChecked) b.searchChipBranches.isChecked = false
            syncType()
        }
        b.searchChipBranches.setOnCheckedChangeListener { _, _ ->
            if (b.searchChipBranches.isChecked) b.searchChipPosts.isChecked = false
            syncType()
        }
        b.searchChipFavorites.setOnCheckedChangeListener { _, checked ->
            viewModel.favoritesOnly.value = checked
        }
        b.searchChipReading.setOnCheckedChangeListener { _, _ ->
            if (b.searchChipReading.isChecked) b.searchChipFinished.isChecked = false
            syncStatus()
        }
        b.searchChipFinished.setOnCheckedChangeListener { _, _ ->
            if (b.searchChipFinished.isChecked) b.searchChipReading.isChecked = false
            syncStatus()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val emptyFilter = viewModel.query.value.isBlank() &&
                        !viewModel.favoritesOnly.value &&
                        viewModel.status.value == null &&
                        viewModel.type.value == null
                    ListStateBinder.bind(
                        overlay = b.listState.root,
                        progress = b.listState.listStateProgress,
                        message = b.listState.listStateMessage,
                        retry = b.listState.listStateRetry,
                        content = b.searchList,
                        state = state.listState,
                        emptyText = getString(
                            if (emptyFilter) R.string.search_prompt else R.string.search_empty,
                        ),
                        errorText = state.errorMessage,
                        offlineText = getString(R.string.state_offline),
                        onRetry = { },
                    )
                    adapter.submitList(
                        state.results.map { hit ->
                            RowItem(hit.id + hit.kind.ordinal * 1_000_000_000L, hit.title, caption(hit))
                        },
                    )
                }
            }
        }
    }

    private fun syncType() {
        val b = binding ?: return
        viewModel.type.value = when {
            b.searchChipPosts.isChecked -> NodeType.POST
            b.searchChipBranches.isChecked -> NodeType.BRANCH
            else -> null
        }
    }

    private fun syncStatus() {
        val b = binding ?: return
        viewModel.status.value = when {
            b.searchChipReading.isChecked -> LearningStatus.READING
            b.searchChipFinished.isChecked -> LearningStatus.FINISHED
            else -> null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun caption(hit: SearchHit): String = when (hit.kind) {
        SearchHit.Kind.POST -> getString(R.string.browse_type_post)
        SearchHit.Kind.BRANCH -> getString(R.string.browse_type_branch)
        SearchHit.Kind.DICTIONARY -> hit.caption
    }

    private fun onHit(item: RowItem) {
        val state = viewModel.uiState.value
        val hit = state.results.firstOrNull {
            it.id + it.kind.ordinal * 1_000_000_000L == item.key
        } ?: return
        when (hit.kind) {
            SearchHit.Kind.POST -> findNavController().navigate(
                R.id.action_global_open_post,
                bundleOf("postId" to hit.id),
            )
            SearchHit.Kind.BRANCH -> {
                locator.requestOpen(hit.id)
                findNavController().navigate(R.id.browseFragment)
            }
            SearchHit.Kind.DICTIONARY -> {
                val postId = hit.postId
                if (postId != null) {
                    findNavController().navigate(
                        R.id.action_global_open_post,
                        bundleOf("postId" to postId),
                    )
                } else {
                    findNavController().navigate(R.id.action_global_dictionary)
                }
            }
        }
    }
}
