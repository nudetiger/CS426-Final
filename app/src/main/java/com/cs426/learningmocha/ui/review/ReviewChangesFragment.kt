package com.cs426.learningmocha.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.DialogReviewEditBinding
import com.cs426.learningmocha.databinding.DialogReviewLocationBinding
import com.cs426.learningmocha.databinding.FragmentReviewChangesBinding
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.viewmodel.ReviewChangesViewModel
import com.cs426.learningmocha.viewmodel.ReviewLocation
import com.cs426.learningmocha.viewmodel.ReviewOutcome
import com.cs426.learningmocha.viewmodel.ReviewRow
import com.cs426.learningmocha.viewmodel.ReviewUiState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch

/**
 * The gate every AI-authored write passes through: the batch is reviewable, editable and
 * relocatable here, and nothing reaches Room until Apply.
 */
class ReviewChangesFragment : Fragment() {

    private var binding: FragmentReviewChangesBinding? = null
    private val viewModel: ReviewChangesViewModel by viewModels()
    private var adapter: ReviewActionAdapter? = null
    private var markwon: Markwon? = null
    private var shownOutcome = ReviewOutcome.NONE

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
            onMenu = ::showRowMenu,
        )
        adapter = rows
        b.reviewList.layoutManager = LinearLayoutManager(requireContext())
        b.reviewList.adapter = rows
        b.reviewBack.setOnClickListener { findNavController().popBackStack() }
        b.reviewApply.setOnClickListener { confirmApply() }
        b.reviewDiscard.setOnClickListener { viewModel.discard() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    b.reviewSummary.text = state.summary
                    b.reviewSummary.isVisible = state.summary.isNotBlank()
                    // Quantity follows the batch size, which is what the sentence pluralises.
                    b.reviewCounts.text = resources.getQuantityString(
                        R.plurals.review_selected_count,
                        state.rows.size,
                        state.selectedCount,
                        state.rows.size,
                    )
                    b.reviewCounts.isVisible = state.rows.isNotEmpty()
                    b.reviewWarning.isVisible = state.destructiveSelected > 0
                    b.reviewErrors.isVisible = state.errors.isNotEmpty()
                    b.reviewErrors.text = state.errors.joinToString("\n")
                    b.reviewApply.isEnabled =
                        !state.applying && state.errors.isEmpty() && state.selectedCount > 0
                    b.reviewDiscard.isEnabled = !state.applying
                    ListStateBinder.bind(
                        overlay = b.listState.root,
                        progress = b.listState.listStateProgress,
                        message = b.listState.listStateMessage,
                        retry = b.listState.listStateRetry,
                        content = b.reviewList,
                        state = state.listState,
                        emptyText = getString(R.string.review_empty),
                        errorText = getString(R.string.review_load_failed),
                        offlineText = getString(R.string.chat_offline),
                        onRetry = { viewModel.reload() },
                    )
                    rows.submitList(state.rows)
                    if (state.outcome != ReviewOutcome.NONE && state.outcome != shownOutcome) {
                        shownOutcome = state.outcome
                        showOutcome(state)
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

    /** Deleting cascades and cannot be undone, so it needs a named, explicit confirmation. */
    private fun confirmApply() {
        val state = viewModel.uiState.value
        if (state.destructiveSelected == 0) {
            viewModel.apply()
            return
        }
        val doomed = state.rows
            .filter { it.checked && it.destructive }
            .joinToString("\n") { getString(R.string.review_delete_bullet, it.target) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                resources.getQuantityString(
                    R.plurals.review_delete_confirm_title,
                    state.destructiveSelected,
                    state.destructiveSelected,
                ),
            )
            .setMessage(getString(R.string.review_delete_confirm_message, doomed))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.review_delete_apply) { _, _ -> viewModel.apply() }
            .show()
    }

    private fun showOutcome(state: ReviewUiState) {
        val b = binding ?: return
        val text = when (state.outcome) {
            ReviewOutcome.APPLIED -> {
                val applied = resources.getQuantityString(
                    R.plurals.review_applied,
                    state.appliedCount,
                    state.appliedCount,
                )
                if (state.deletedCount > 0) {
                    "$applied ${getString(R.string.review_applied_deleted)}"
                } else {
                    applied
                }
            }
            ReviewOutcome.DISCARDED -> getString(R.string.review_discarded_done)
            ReviewOutcome.UNDONE -> if (state.deletedCount > 0) {
                getString(R.string.review_undo_kept)
            } else {
                getString(R.string.review_undo_done)
            }
            ReviewOutcome.NONE -> return
        }
        val bar = Snackbar.make(b.root, text, Snackbar.LENGTH_LONG)
        if (state.outcome == ReviewOutcome.APPLIED) {
            bar.setAction(R.string.review_undo) { viewModel.undo() }
        }
        bar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                // Undo runs in the ViewModel's scope, so leaving on the action tap would
                // cancel it; the second snackbar takes over the exit.
                if (event == BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_ACTION) return
                if (isAdded) findNavController().popBackStack()
            }
        })
        bar.show()
    }

    private fun showRowMenu(row: ReviewRow, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            if (row.preview.isNotBlank()) menu.add(0, MENU_PREVIEW, 0, R.string.review_row_preview)
            if (row.editable) menu.add(0, MENU_EDIT, 1, R.string.review_row_edit)
            if (row.relocatable) menu.add(0, MENU_LOCATION, 2, R.string.review_row_location)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_PREVIEW -> {
                        showPreview(row)
                        true
                    }
                    MENU_EDIT -> {
                        showEdit(row)
                        true
                    }
                    MENU_LOCATION -> {
                        showLocation(row)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showPreview(row: ReviewRow) {
        if (row.preview.isBlank()) return
        val gutter = resources.getDimensionPixelSize(R.dimen.space_m)
        val text = TextView(requireContext()).apply {
            setPadding(gutter, gutter, gutter, gutter)
            maxWidth = resources.getDimensionPixelSize(R.dimen.reader_max_width)
        }
        markwon?.setMarkdown(text, row.preview)
        val scroll = ScrollView(requireContext()).apply { addView(text) }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(row.title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
        if (row.editable) {
            builder.setNeutralButton(R.string.review_row_edit) { _, _ -> showEdit(row) }
        }
        builder.show()
    }

    private fun showEdit(row: ReviewRow) {
        val dialogBinding = DialogReviewEditBinding.inflate(layoutInflater)
        dialogBinding.reviewEditTitle.setText(row.target)
        dialogBinding.reviewEditContent.setText(row.content)
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.review_edit_save) { _, _ ->
                viewModel.edit(
                    index = row.index,
                    title = dialogBinding.reviewEditTitle.text?.toString().orEmpty(),
                    content = dialogBinding.reviewEditContent.text?.toString().orEmpty(),
                )
            }
            .show()
    }

    private fun showLocation(row: ReviewRow) {
        val b = binding ?: return
        val containers = viewModel.uiState.value.containers
        val options: List<ReviewLocation?> = if (row.allowRoot) {
            listOf<ReviewLocation?>(null) + containers
        } else {
            containers
        }
        if (options.isEmpty()) {
            Snackbar.make(b.root, R.string.review_location_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        val dialogBinding = DialogReviewLocationBinding.inflate(layoutInflater)
        val current = row.parentTitle?.trim()?.lowercase().orEmpty()
        val ids = ArrayList<Int>(options.size)
        for (option in options) {
            val button = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = option?.path ?: getString(R.string.browse_move_root)
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_target)
                isChecked = option?.title?.trim()?.lowercase().orEmpty() == current
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Mocha_Body)
            }
            ids.add(button.id)
            dialogBinding.reviewLocationGroup.addView(
                button,
                RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.review_location_save) { _, _ ->
                val position = ids.indexOf(dialogBinding.reviewLocationGroup.checkedRadioButtonId)
                if (position >= 0) viewModel.relocate(row.index, options[position])
            }
            .show()
    }

    private companion object {
        const val MENU_PREVIEW = 1
        const val MENU_EDIT = 2
        const val MENU_LOCATION = 3
    }
}
