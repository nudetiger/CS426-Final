package com.cs426.learningmocha.ui.dictionary

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
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.databinding.FragmentNamedListBinding
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.ui.common.RowAdapter
import com.cs426.learningmocha.ui.common.RowItem
import com.cs426.learningmocha.viewmodel.DictionaryScope
import com.cs426.learningmocha.viewmodel.DictionaryViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class DictionaryFragment : Fragment() {

    private var binding: FragmentNamedListBinding? = null
    private val viewModel: DictionaryViewModel by viewModels()
    private val adapter = RowAdapter(::onEntry)

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
        b.namedTitle.setText(R.string.dictionary_title)
        b.namedBack.setOnClickListener { findNavController().popBackStack() }
        b.namedSearch.isVisible = true
        b.namedChips.isVisible = true
        b.namedList.layoutManager = LinearLayoutManager(requireContext())
        b.namedList.adapter = adapter
        b.namedSearch.doAfterTextChanged { viewModel.query.value = it?.toString().orEmpty() }

        b.namedChipAll.setOnClickListener { selectScope(DictionaryScope.ALL) }
        b.namedChipGlobal.setOnClickListener { selectScope(DictionaryScope.GLOBAL) }
        b.namedChipPost.setOnClickListener { selectScope(DictionaryScope.POST) }

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
                        emptyText = getString(R.string.dictionary_empty),
                        errorText = state.errorMessage,
                        offlineText = getString(R.string.state_offline),
                        onRetry = { },
                    )
                    adapter.submitList(
                        state.entries.map { entry ->
                            RowItem(
                                entry.id,
                                entry.term,
                                entry.definition,
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

    private fun selectScope(scope: DictionaryScope) {
        val b = binding ?: return
        viewModel.scope.value = scope
        b.namedChipAll.isChecked = scope == DictionaryScope.ALL
        b.namedChipGlobal.isChecked = scope == DictionaryScope.GLOBAL
        b.namedChipPost.isChecked = scope == DictionaryScope.POST
    }

    private fun onEntry(item: RowItem) {
        val entry = viewModel.uiState.value.entries.firstOrNull { it.id == item.key } ?: return
        showTerm(entry)
    }

    private fun showTerm(entry: DictionaryEntry) {
        val body = buildString {
            append(entry.definition)
            if (entry.meaningVi.isNotBlank()) {
                append("\n\n")
                append(entry.meaningVi)
            }
        }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(entry.term)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
        val postId = entry.postId
        if (postId != null) {
            builder.setNeutralButton(R.string.dictionary_open_post) { _, _ ->
                findNavController().navigate(
                    R.id.action_global_open_post,
                    bundleOf("postId" to postId),
                )
            }
        }
        builder.show()
    }
}
