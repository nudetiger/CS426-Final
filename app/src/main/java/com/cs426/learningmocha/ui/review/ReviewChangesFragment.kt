package com.cs426.learningmocha.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.FragmentReviewChangesBinding
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.viewmodel.ReviewChangesViewModel
import com.cs426.learningmocha.viewmodel.ReviewRow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch

class ReviewChangesFragment : Fragment() {

    private var binding: FragmentReviewChangesBinding? = null
    private val viewModel: ReviewChangesViewModel by viewModels()
    private var adapter: ReviewActionAdapter? = null
    private var markwon: Markwon? = null
    private var handledDone = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentReviewChangesBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        markwon = Markwon.builder(requireContext())
            .usePlugin(LinkifyPlugin.create())
            .build()
        val rows = ReviewActionAdapter(
            indentPx = resources.getDimensionPixelSize(R.dimen.space_m),
            onToggle = viewModel::toggle,
            onOpen = ::showPreview,
        )
        adapter = rows
        b.reviewList.layoutManager = LinearLayoutManager(requireContext())
        b.reviewList.adapter = rows
        b.reviewBack.setOnClickListener { findNavController().popBackStack() }
        b.reviewApply.setOnClickListener { viewModel.apply() }
        b.reviewDiscard.setOnClickListener { viewModel.discard() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    b.reviewSummary.text = state.summary
                    b.reviewErrors.isVisible = state.errors.isNotEmpty()
                    b.reviewErrors.text = state.errors.joinToString("\n")
                    b.reviewApply.isEnabled = !state.applying && state.errors.isEmpty() && state.rows.isNotEmpty()
                    b.reviewDiscard.isEnabled = !state.applying
                    ListStateBinder.bind(
                        overlay = b.listState.root,
                        progress = b.listState.listStateProgress,
                        message = b.listState.listStateMessage,
                        retry = b.listState.listStateRetry,
                        content = b.reviewList,
                        state = if (state.rows.isEmpty()) ListState.EMPTY else ListState.CONTENT,
                        emptyText = getString(R.string.review_empty),
                        errorText = null,
                        offlineText = getString(R.string.chat_offline),
                        onRetry = { },
                    )
                    rows.submitList(state.rows)
                    val done = state.doneMessage
                    if (state.done && done != null && !handledDone) {
                        handledDone = true
                        val bar = Snackbar.make(b.root, done, Snackbar.LENGTH_LONG)
                        if (done.startsWith("Applied")) {
                            bar.setAction(R.string.review_undone) { viewModel.undo() }
                        }
                        bar.addCallback(object : Snackbar.Callback() {
                            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                                if (isAdded) findNavController().popBackStack()
                            }
                        })
                        bar.show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
        markwon = null
        binding = null
    }

    private fun showPreview(row: ReviewRow) {
        if (row.preview.isBlank()) return
        val text = TextView(requireContext()).apply {
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_m),
                resources.getDimensionPixelSize(R.dimen.space_m),
                resources.getDimensionPixelSize(R.dimen.space_m),
                resources.getDimensionPixelSize(R.dimen.space_m),
            )
            maxWidth = resources.getDimensionPixelSize(R.dimen.reader_max_width)
        }
        markwon?.setMarkdown(text, row.preview)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(row.title)
            .setView(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
