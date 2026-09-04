package com.cs426.learningmocha.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import com.cs426.learningmocha.ui.common.RowAdapter
import com.cs426.learningmocha.ui.common.RowItem
import com.cs426.learningmocha.viewmodel.TagsViewModel
import kotlinx.coroutines.launch

/** Tag index: every tag in the library, alphabetical, with how many posts carry it. */
class TagsFragment : Fragment() {

    private var binding: FragmentNamedListBinding? = null
    private val viewModel: TagsViewModel by viewModels()
    private val adapter = RowAdapter(::onTag)

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
        b.namedTitle.setText(R.string.tags_title)
        b.namedBack.setOnClickListener { findNavController().popBackStack() }
        b.namedSearch.isVisible = true
        b.namedSearch.setHint(R.string.tags_search_hint)
        b.namedList.layoutManager = LinearLayoutManager(requireContext())
        b.namedList.adapter = adapter
        b.namedSearch.doAfterTextChanged { viewModel.query.value = it?.toString().orEmpty() }

        // Tags added elsewhere (editor, AI actions) do not push into a snapshot list,
        // so re-read them on every entry.
        viewModel.refresh()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    ListStateBinder.bind(
                        overlay = b.listState.root,
                        progress = b.listState.listStateProgress,
                        message = b.listState.listStateMessage,
                        retry = b.listState.listStateRetry,
                        content = b.namedList,
                        state = state.listState,
                        emptyText = getString(R.string.tags_empty),
                        errorText = state.errorMessage,
                        offlineText = getString(R.string.state_offline),
                        onRetry = { viewModel.refresh() },
                    )
                    adapter.submitList(
                        state.tags.map { tag ->
                            RowItem(
                                tag.id,
                                tag.name,
                                resources.getQuantityString(
                                    R.plurals.tags_post_count,
                                    tag.postCount,
                                    tag.postCount,
                                ),
                            )
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

    private fun onTag(item: RowItem) {
        findNavController().navigate(
            R.id.action_global_tag,
            bundleOf("tagId" to item.key),
        )
    }
}
