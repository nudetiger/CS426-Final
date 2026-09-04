package com.cs426.learningmocha.ui.editor

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.ResourceType
import com.cs426.learningmocha.databinding.DialogDictionaryTermBinding
import com.cs426.learningmocha.databinding.DialogResourceBinding
import com.cs426.learningmocha.databinding.FragmentPostEditorBinding
import com.cs426.learningmocha.ui.common.ListState
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.ui.common.WikiMarkdown
import com.cs426.learningmocha.viewmodel.EditorResource
import com.cs426.learningmocha.viewmodel.PostEditorViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PostEditorFragment : Fragment() {

    private var binding: FragmentPostEditorBinding? = null
    private val viewModel: PostEditorViewModel by viewModels()
    private var markwon: Markwon? = null

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (viewModel.isDirty()) confirmDiscard() else findNavController().popBackStack()
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
        b.editorSave.setOnClickListener { viewModel.save() }
        b.editorBold.setOnClickListener { wrapSelection("**", "**") }
        b.editorItalic.setOnClickListener { wrapSelection("*", "*") }
        b.editorFormatHeading.setOnClickListener { insertLinePrefix("## ") }
        b.editorList.setOnClickListener { insertLinePrefix("- ") }
        b.editorLink.setOnClickListener { wrapSelection("[", "](https://)") }
        b.editorWikilink.setOnClickListener { insertWikiLink() }
        b.editorTerm.setOnClickListener { addTerm() }
        b.editorAddResource.setOnClickListener { addResource() }

        // Every edit goes to the ViewModel first; the collector below renders back from state.
        b.editorTitleInput.doAfterTextChanged { viewModel.onTitleChanged(it?.toString().orEmpty()) }
        b.editorBody.doAfterTextChanged { viewModel.onContentChanged(it?.toString().orEmpty()) }
        b.editorTagsInput.doAfterTextChanged { viewModel.onTagsChanged(it?.toString().orEmpty()) }
        b.editorStatus.setOnCheckedStateChangeListener { _, _ ->
            viewModel.onStatusChanged(selectedStatus())
        }

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
                        if (state.listState == ListState.CONTENT) {
                            setIfChanged(b.editorTitleInput, state.title)
                            setIfChanged(b.editorBody, state.content)
                            setIfChanged(b.editorTagsInput, state.tags)
                            checkStatus(state.status)
                        }
                        val error = state.errorMessage
                        if (error != null && state.listState == ListState.CONTENT) {
                            Snackbar.make(b.root, error, Snackbar.LENGTH_LONG).show()
                            viewModel.consumeError()
                        }
                    }
                }
                launch {
                    viewModel.uiState
                        .map { it.resources }
                        .distinctUntilChanged()
                        .collect { renderResources(it) }
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

    /** Writing the same text back would move the cursor and fight the user's typing. */
    private fun setIfChanged(field: EditText, value: String) {
        if (field.text?.toString() == value) return
        field.setText(value)
        field.setSelection(field.text?.length ?: 0)
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

    private fun renderResources(items: List<EditorResource>) {
        val b = binding ?: return
        b.editorResources.removeAllViews()
        b.editorResources.isVisible = items.isNotEmpty()
        b.editorResourcesEmpty.isVisible = items.isEmpty()
        items.forEach { item -> b.editorResources.addView(resourceChip(item)) }
    }

    private fun resourceChip(item: EditorResource): Chip {
        val chip = Chip(requireContext())
        val kind = getString(item.type.labelRes())
        chip.text = item.title.ifBlank { kind }
        chip.contentDescription = getString(R.string.cd_resource, kind, item.url)
        chip.chipIcon = ContextCompat.getDrawable(
            requireContext(),
            if (item.type == ResourceType.YOUTUBE) R.drawable.ic_play else R.drawable.ic_post,
        )
        // Set explicitly: the theme-level chip style decides icon visibility and the close
        // drawable, and this chip needs both regardless of which style is in force.
        chip.isChipIconVisible = true
        chip.isCheckable = false
        chip.closeIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
        chip.closeIconContentDescription = getString(R.string.cd_resource_remove)
        chip.isCloseIconVisible = item.removable
        // The snackbars anchor on the fragment root, not on the chip: removing a reference
        // re-renders the group, so the chip itself may already be detached by then.
        chip.setOnClickListener {
            val root = binding?.root ?: return@setOnClickListener
            Snackbar.make(root, item.url, Snackbar.LENGTH_LONG).show()
        }
        chip.setOnCloseIconClickListener {
            val root = binding?.root ?: return@setOnCloseIconClickListener
            viewModel.removeResource(item.key)
            Snackbar.make(root, R.string.editor_resource_removed, Snackbar.LENGTH_SHORT).show()
        }
        return chip
    }

    private fun addResource() {
        val fields = DialogResourceBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setView(fields.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val root = binding?.root ?: return@setPositiveButton
                val url = fields.resourceUrlInput.text?.toString().orEmpty().trim()
                if (url.isEmpty()) {
                    Snackbar.make(root, R.string.editor_resource_url_required, Snackbar.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }
                val type = when (fields.resourceKindGroup.checkedRadioButtonId) {
                    R.id.resource_kind_youtube -> ResourceType.YOUTUBE
                    R.id.resource_kind_book -> ResourceType.BOOK
                    R.id.resource_kind_other -> ResourceType.OTHER
                    else -> ResourceType.ARTICLE
                }
                viewModel.addResource(
                    type,
                    fields.resourceNameInput.text?.toString().orEmpty(),
                    url,
                )
                Snackbar.make(root, R.string.editor_resource_added, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun renderPreview() {
        val b = binding ?: return
        val markdown = viewModel.uiState.value.content
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
            val own = viewModel.uiState.value.title
            val titles = viewModel.postTitles().filter { !it.equals(own, ignoreCase = true) }
            if (titles.isEmpty()) {
                Snackbar.make(edit, R.string.editor_no_link_targets, Snackbar.LENGTH_SHORT).show()
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
                val root = binding?.root ?: return@setPositiveButton
                val term = fields.termInput.text?.toString().orEmpty()
                if (term.isBlank()) {
                    Snackbar.make(root, R.string.editor_term_required, Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.addTerm(
                    term,
                    fields.termDefinition.text?.toString().orEmpty(),
                    fields.termVi.text?.toString().orEmpty(),
                )
                Snackbar.make(root, R.string.editor_term_added, Snackbar.LENGTH_SHORT).show()
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
        val target = when (status) {
            LearningStatus.NONE -> R.id.editor_status_none
            LearningStatus.IN_PROGRESS -> R.id.editor_status_progress
            LearningStatus.FINISHED -> R.id.editor_status_finished
            LearningStatus.READING -> R.id.editor_status_reading
        }
        if (b.editorStatus.checkedChipId != target) b.editorStatus.check(target)
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

    private fun ResourceType.labelRes(): Int = when (this) {
        ResourceType.YOUTUBE -> R.string.resource_kind_youtube
        ResourceType.ARTICLE -> R.string.resource_kind_article
        ResourceType.BOOK -> R.string.resource_kind_book
        ResourceType.OTHER -> R.string.resource_kind_other
    }

    private val EditText.editable: Editable?
        get() = text
}
