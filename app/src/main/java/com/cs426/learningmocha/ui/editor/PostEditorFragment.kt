package com.cs426.learningmocha.ui.editor

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.databinding.DialogDictionaryTermBinding
import com.cs426.learningmocha.databinding.FragmentPostEditorBinding
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.ui.common.WikiMarkdown
import com.cs426.learningmocha.viewmodel.PostEditorViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch

class PostEditorFragment : Fragment() {

    private var binding: FragmentPostEditorBinding? = null
    private val viewModel: PostEditorViewModel by viewModels()
    private var markwon: Markwon? = null
    private var hydrated = false
    private var initialTitle = ""
    private var initialContent = ""
    private var initialTags = ""
    private var initialStatus = LearningStatus.READING

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isDirty()) confirmDiscard() else findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentPostEditorBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        markwon = Markwon.builder(requireContext())
            .usePlugin(LinkifyPlugin.create())
            .build()

        b.editorBack.setOnClickListener { backCallback.handleOnBackPressed() }
        b.editorSave.setOnClickListener { save() }
        b.editorBold.setOnClickListener { wrapSelection("**", "**") }
        b.editorItalic.setOnClickListener { wrapSelection("*", "*") }
        b.editorFormatHeading.setOnClickListener { insertLinePrefix("## ") }
        b.editorList.setOnClickListener { insertLinePrefix("- ") }
        b.editorLink.setOnClickListener { wrapSelection("[", "](https://)") }
        b.editorWikilink.setOnClickListener { insertWikiLink() }
        b.editorTerm.setOnClickListener { addTerm() }

        b.editorTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val preview = tab.position == 1
                b.editorBody.isVisible = !preview
                b.editorPreviewScroll.isVisible = preview
                if (preview) renderPreview()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        b.editorHeading.setText(
                            if (state.isNew) R.string.editor_new_title else R.string.editor_title,
                        )
                        ListStateBinder.bind(
                            overlay = b.listState.root,
                            progress = b.listState.listStateProgress,
                            message = b.listState.listStateMessage,
                            retry = b.listState.listStateRetry,
                            content = b.editorContent,
                            state = state.listState,
                            emptyText = getString(R.string.reader_missing),
                            errorText = state.errorMessage,
                            offlineText = getString(R.string.state_offline),
                            onRetry = { },
                        )
                        if (state.listState == ListState.CONTENT && !hydrated) {
                            b.editorTitleInput.setText(state.title)
                            b.editorBody.setText(state.content)
                            b.editorTagsInput.setText(state.tags)
                            checkStatus(state.status)
                            initialTitle = state.title
                            initialContent = state.content
                            initialTags = state.tags
                            initialStatus = state.status
                            hydrated = true
                        }
                        if (state.errorMessage != null && state.listState == ListState.CONTENT) {
                            Snackbar.make(b.root, state.errorMessage, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
                launch {
                    viewModel.savedFlow.collect { postId ->
                        Snackbar.make(b.root, R.string.editor_saved, Snackbar.LENGTH_SHORT).show()
                        val nav = findNavController()
                        if (nav.previousBackStackEntry?.destination?.id == R.id.postReaderFragment) {
                            nav.popBackStack()
                        } else {
                            nav.navigate(
                                R.id.action_global_open_post,
                                bundleOf("postId" to postId),
                                NavOptions.Builder()
                                    .setPopUpTo(R.id.postEditorFragment, true)
                                    .build(),
                            )
                        }
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

    private fun save() {
        val b = binding ?: return
        viewModel.save(
            b.editorTitleInput.text?.toString().orEmpty(),
            b.editorBody.text?.toString().orEmpty(),
            selectedStatus(),
            b.editorTagsInput.text?.toString().orEmpty(),
        )
    }

    private fun isDirty(): Boolean {
        val b = binding ?: return false
        if (!hydrated) return false
        return b.editorTitleInput.text?.toString() != initialTitle ||
            b.editorBody.text?.toString() != initialContent ||
            b.editorTagsInput.text?.toString() != initialTags ||
            selectedStatus() != initialStatus
    }

    private fun confirmDiscard() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.editor_discard_title)
            .setMessage(R.string.editor_discard_message)
            .setNegativeButton(R.string.editor_keep_editing, null)
            .setPositiveButton(R.string.editor_discard) { _, _ ->
                findNavController().popBackStack()
            }
            .show()
    }

    private fun renderPreview() {
        val b = binding ?: return
        val markdown = b.editorBody.text?.toString().orEmpty()
        if (markdown.isBlank()) {
            b.editorPreview.setText(R.string.editor_preview_empty)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val rewritten = WikiMarkdown.rewrite(markdown, viewModel.titleToId())
            markwon?.setMarkdown(b.editorPreview, rewritten)
        }
    }

    private fun insertWikiLink() {
        val edit = binding?.editorBody ?: return
        if (edit.selectionStart != edit.selectionEnd) {
            wrapSelection("[[", "]]")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val titles = viewModel.postTitles()
            if (titles.isEmpty()) {
                wrapSelection("[[", "]]")
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editor_pick_wikilink)
                .setItems(titles.toTypedArray()) { _, which ->
                    val editable = edit.editable ?: return@setItems
                    val start = edit.selectionStart.coerceAtLeast(0)
                    val token = "[[${titles[which]}]]"
                    editable.insert(start, token)
                    edit.setSelection(start + token.length)
                }
                .show()
        }
    }

    private fun addTerm() {
        val fields = DialogDictionaryTermBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setView(fields.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val term = fields.termInput.text?.toString().orEmpty()
                if (term.isBlank()) return@setPositiveButton
                viewModel.addTerm(
                    term,
                    fields.termDefinition.text?.toString().orEmpty(),
                    fields.termVi.text?.toString().orEmpty(),
                )
                Snackbar.make(binding?.root ?: return@setPositiveButton, R.string.editor_term_added, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun selectedStatus(): LearningStatus {
        val b = binding ?: return LearningStatus.READING
        return when (b.editorStatus.checkedChipId) {
            R.id.editor_status_none -> LearningStatus.NONE
            R.id.editor_status_progress -> LearningStatus.IN_PROGRESS
            R.id.editor_status_finished -> LearningStatus.FINISHED
            else -> LearningStatus.READING
        }
    }

    private fun checkStatus(status: LearningStatus) {
        val b = binding ?: return
        b.editorStatus.check(
            when (status) {
                LearningStatus.NONE -> R.id.editor_status_none
                LearningStatus.IN_PROGRESS -> R.id.editor_status_progress
                LearningStatus.FINISHED -> R.id.editor_status_finished
                LearningStatus.READING -> R.id.editor_status_reading
            },
        )
    }

    private fun wrapSelection(prefix: String, suffix: String) {
        val edit = binding?.editorBody ?: return
        val editable = edit.editable ?: return
        val start = edit.selectionStart.coerceAtLeast(0)
        val end = edit.selectionEnd.coerceAtLeast(start)
        editable.insert(end, suffix)
        editable.insert(start, prefix)
        edit.setSelection(start + prefix.length, end + prefix.length)
    }

    private fun insertLinePrefix(prefix: String) {
        val edit = binding?.editorBody ?: return
        val editable = edit.editable ?: return
        val start = edit.selectionStart.coerceAtLeast(0)
        val text = editable.toString()
        val lineStart = if (start <= 0) {
            0
        } else {
            val newline = text.lastIndexOf('\n', start - 1)
            if (newline < 0) 0 else newline + 1
        }
        editable.insert(lineStart, prefix)
    }

    private val EditText.editable: Editable?
        get() = text
}
