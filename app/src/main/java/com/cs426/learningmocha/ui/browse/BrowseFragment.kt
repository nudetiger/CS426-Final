package com.cs426.learningmocha.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
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
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.databinding.DialogBrowseFilterBinding
import com.cs426.learningmocha.databinding.DialogCreateNodeBinding
import com.cs426.learningmocha.databinding.DialogNodeTitleBinding
import com.cs426.learningmocha.databinding.FragmentBrowseBinding
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.ui.common.StatusMeterBinder
import com.cs426.learningmocha.ui.common.SwipeToDelete
import com.cs426.learningmocha.ui.common.labelRes
import com.cs426.learningmocha.viewmodel.BrowseLocatorViewModel
import com.cs426.learningmocha.viewmodel.BrowseUiState
import com.cs426.learningmocha.viewmodel.BrowseViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class BrowseFragment : Fragment() {

    private var binding: FragmentBrowseBinding? = null
    private val viewModel: BrowseViewModel by viewModels()
    private val locator: BrowseLocatorViewModel by activityViewModels()

    private val adapter = BrowseAdapter(
        onClick = { node ->
            when (node.type) {
                // A post opens to be read even when it has sub-posts; the chevron on the row
                // is what descends into them.
                NodeType.POST -> openPost(node.id)
                NodeType.BRANCH, NodeType.FOLDER -> viewModel.open(node.id)
            }
        },
        onOpenChildren = { node -> viewModel.open(node.id) },
        onToggleFavorite = { node -> viewModel.toggleFavorite(node) },
        onMenu = { node, anchor -> showNodeMenu(node, anchor) },
    )

    /**
     * Cached rather than read per row. Refreshed in [onResume] because Settings is a round
     * trip away, and the list has to come back in the colours the user just chose.
     */
    private var colorfulLists = true

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.goUp()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentBrowseBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        colorfulLists = (requireActivity().application as com.cs426.learningmocha.LearningMochaApp)
            .settings.colorfulLists

        locator.consume()?.let { viewModel.open(it) }

        b.browseList.layoutManager = LinearLayoutManager(requireContext())
        b.browseList.adapter = adapter
        // Swipe-to-delete only. Drag reordering is gone: it described one folder at a time,
        // could not be undone, and sorting says something the order of a drag never did.
        SwipeToDelete.attach(b.browseList) { position, restore ->
            val node = adapter.currentList.getOrNull(position)?.node
            if (node == null) restore() else confirmDelete(node, onCancel = restore)
        }

        b.browseFab.setOnClickListener { showCreateDialog() }
        b.browseSort.setOnClickListener { showSortMenu(it) }
        b.browseFilter.setOnClickListener { showFilterDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> render(state) }
                }
                launch {
                    viewModel.messagesFlow.collect { message ->
                        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val current = (requireActivity().application as com.cs426.learningmocha.LearningMochaApp)
            .settings.colorfulLists
        if (current == colorfulLists) return
        colorfulLists = current
        render(viewModel.uiState.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: BrowseUiState) {
        val b = binding ?: return
        b.browseTitle.text = state.title
        backCallback.isEnabled = state.parentId != null
        renderBreadcrumbs(state.breadcrumbs)

        StatusMeterBinder.bind(b.browseMeter, state.currentStats) { status ->
            // Tapping a share of the meter is the shortest path to "show me only those".
            val current = state.filter
            val next = if (current.statuses == setOf(status)) {
                current.copy(statuses = emptySet())
            } else {
                current.copy(statuses = setOf(status))
            }
            viewModel.setFilter(next)
        }

        b.browseFilter.text = if (state.filter.isActive) {
            getString(R.string.browse_filter_badge, state.filter.activeCount)
        } else {
            getString(R.string.browse_filter)
        }
        val hidden = state.unfilteredCount - state.children.size
        b.browseFilterNote.isVisible = hidden > 0
        if (hidden > 0) {
            b.browseFilterNote.text =
                resources.getQuantityString(R.plurals.browse_hidden_by_filter, hidden, hidden)
        }

        adapter.submitList(rowsOf(state))
        ListStateBinder.bind(
            overlay = b.listState.root,
            progress = b.listState.listStateProgress,
            message = b.listState.listStateMessage,
            retry = b.listState.listStateRetry,
            content = b.browseList,
            state = state.listState,
            emptyText = getString(
                if (state.filter.isActive) R.string.browse_empty_filtered else R.string.browse_empty,
            ),
            errorText = state.errorMessage,
            offlineText = getString(R.string.state_offline),
            onRetry = { },
        )
    }

    private fun rowsOf(state: BrowseUiState): List<BrowseRow> =
        state.children.mapIndexed { index, node ->
            BrowseRow(
                node = node,
                // A post counts itself in its own subtree, so what is "inside" it is one less.
                postsInside = (state.stats[node.id]?.total ?: 0)
                    .let { if (node.type == NodeType.POST) it - 1 else it },
                directChildren = state.childCounts[node.id] ?: 0,
                stripe = index % 2 == 1,
                colorful = colorfulLists,
            )
        }

    private fun renderBreadcrumbs(crumbs: List<Node>) {
        val row = binding?.browseBreadcrumbs ?: return
        row.removeAllViews()
        addCrumb(getString(R.string.browse_library)) { viewModel.open(null) }
        crumbs.forEach { node ->
            addSeparator()
            addCrumb(node.title) { viewModel.open(node.id) }
        }
    }

    private fun addCrumb(label: String, onClick: () -> Unit) {
        val row = binding?.browseBreadcrumbs ?: return
        val padH = resources.getDimensionPixelSize(R.dimen.space_m)
        val padV = resources.getDimensionPixelSize(R.dimen.space_xs)
        val crumb = TextView(requireContext()).apply {
            text = label
            background = androidx.core.content.ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_chip,
            )
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Mocha_Caption)
            setPadding(padH, padV, padH, padV)
            setOnClickListener { onClick() }
        }
        row.addView(crumb)
    }

    private fun addSeparator() {
        val row = binding?.browseBreadcrumbs ?: return
        val gap = resources.getDimensionPixelSize(R.dimen.space_xs)
        val slash = TextView(requireContext()).apply {
            text = " / "
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Mocha_Caption)
            setPadding(gap, 0, gap, 0)
        }
        row.addView(slash)
    }

    private fun showSortMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            BrowseSort.entries.forEachIndexed { index, sort ->
                menu.add(SORT_GROUP, index, index, getString(sort.labelRes))
            }
            menu.setGroupCheckable(SORT_GROUP, true, true)
            menu.findItem(viewModel.uiState.value.sort.ordinal)?.isChecked = true
            setOnMenuItemClickListener { item ->
                BrowseSort.entries.getOrNull(item.itemId)?.let { viewModel.setSort(it) }
                true
            }
            show()
        }
    }

    private fun showFilterDialog() {
        val fields = DialogBrowseFilterBinding.inflate(layoutInflater)
        val current = viewModel.uiState.value.filter
        fields.filterTypeBranch.isChecked = NodeType.BRANCH in current.types
        fields.filterTypeFolder.isChecked = NodeType.FOLDER in current.types
        fields.filterTypePost.isChecked = NodeType.POST in current.types
        fields.filterStatusNone.isChecked = LearningStatus.NONE in current.statuses
        fields.filterStatusReading.isChecked = LearningStatus.READING in current.statuses
        fields.filterStatusProgress.isChecked = LearningStatus.IN_PROGRESS in current.statuses
        fields.filterStatusFinished.isChecked = LearningStatus.FINISHED in current.statuses
        fields.filterFavorites.isChecked = current.favoritesOnly
        fields.filterReady.isChecked = current.readyOnly

        MaterialAlertDialogBuilder(requireContext())
            .setView(fields.root)
            .setNeutralButton(R.string.browse_filter_clear) { _, _ -> viewModel.clearFilter() }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.browse_filter_apply) { _, _ ->
                val types = buildSet {
                    if (fields.filterTypeBranch.isChecked) add(NodeType.BRANCH)
                    if (fields.filterTypeFolder.isChecked) add(NodeType.FOLDER)
                    if (fields.filterTypePost.isChecked) add(NodeType.POST)
                }
                val statuses = buildSet {
                    if (fields.filterStatusNone.isChecked) add(LearningStatus.NONE)
                    if (fields.filterStatusReading.isChecked) add(LearningStatus.READING)
                    if (fields.filterStatusProgress.isChecked) add(LearningStatus.IN_PROGRESS)
                    if (fields.filterStatusFinished.isChecked) add(LearningStatus.FINISHED)
                }
                viewModel.setFilter(
                    BrowseFilter(
                        types,
                        statuses,
                        fields.filterFavorites.isChecked,
                        fields.filterReady.isChecked,
                    ),
                )
            }
            .show()
    }

    private fun showNodeMenu(node: Node, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.menu_node, menu)
            menu.findItem(R.id.action_set_status)?.isVisible = node.type == NodeType.POST
            // Only containers: reading "a branch" from a post would mean the post plus its
            // sub-posts, which is what tapping the post already does.
            menu.findItem(R.id.action_read_branch)?.isVisible = node.type != NodeType.POST
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_read_branch -> {
                        readBranch(node)
                        true
                    }
                    R.id.action_rename -> {
                        showRenameDialog(node)
                        true
                    }
                    R.id.action_move -> {
                        showMoveDialog(node)
                        true
                    }
                    R.id.action_set_status -> {
                        pickStatus(node)
                        true
                    }
                    R.id.action_delete -> {
                        confirmDelete(node)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun pickStatus(node: Node) {
        val options = LearningStatus.entries
        val labels = options.map { getString(it.labelRes()) }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.browse_set_status)
            .setSingleChoiceItems(labels, options.indexOf(node.status)) { dialog, which ->
                viewModel.setStatus(node, options[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showCreateDialog() {
        val dialogBinding = DialogCreateNodeBinding.inflate(layoutInflater)
        val atRoot = viewModel.uiState.value.parentId == null
        dialogBinding.createTypeBranch.isChecked = atRoot
        dialogBinding.createTypePost.isChecked = !atRoot
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_create) { _, _ ->
                val title = dialogBinding.createTitleInput.text?.toString().orEmpty()
                val type = when (dialogBinding.createTypeGroup.checkedRadioButtonId) {
                    R.id.create_type_branch -> NodeType.BRANCH
                    R.id.create_type_folder -> NodeType.FOLDER
                    else -> NodeType.POST
                }
                if (type == NodeType.POST) {
                    val parentId = viewModel.uiState.value.parentId ?: -1L
                    findNavController().navigate(
                        R.id.action_global_edit_post,
                        bundleOf("postId" to 0L, "parentId" to parentId, "title" to title),
                    )
                } else {
                    viewModel.create(type, title)
                }
            }
            .show()
    }

    private fun showRenameDialog(node: Node) {
        val dialogBinding = DialogNodeTitleBinding.inflate(layoutInflater)
        dialogBinding.titleDialogHeading.setText(R.string.browse_rename)
        dialogBinding.titleDialogInput.setText(node.title)
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_rename) { _, _ ->
                viewModel.rename(node.id, dialogBinding.titleDialogInput.text?.toString().orEmpty())
            }
            .show()
    }

    private fun showMoveDialog(node: Node) {
        viewLifecycleOwner.lifecycleScope.launch {
            val parents = viewModel.possibleParents(node.id)
            val labels = mutableListOf(getString(R.string.browse_move_root))
            // Posts are offered too: moving a post under a post is how a sub-post is made.
            labels.addAll(
                parents.map { parent ->
                    val kind = getString(
                        com.cs426.learningmocha.ui.common.NodePalette.typeLabelRes(parent.type),
                    )
                    "${parent.title}  ·  $kind"
                },
            )
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.browse_move_title, node.title))
                .setItems(labels.toTypedArray()) { _, which ->
                    val newParent = if (which == 0) null else parents[which - 1].id
                    viewModel.move(node.id, newParent)
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun confirmDelete(node: Node, onCancel: (() -> Unit)? = null) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.browse_delete_confirm_title, node.title))
            .setMessage(R.string.browse_delete_confirm_message)
            .setNegativeButton(R.string.action_cancel) { _, _ -> onCancel?.invoke() }
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(node.id) }
            .setOnCancelListener { onCancel?.invoke() }
            .show()
    }

    /**
     * Jumps to the first post of [node]'s subtree and stays inside it: the reader gets a strip
     * with prev/next and the branch's structure, so a whole branch can be read without coming
     * back here between posts.
     */
    private fun readBranch(node: Node) {
        viewLifecycleOwner.lifecycleScope.launch {
            val first = viewModel.firstPostOf(node.id)
            val root = binding?.root ?: return@launch
            if (first == null) {
                Snackbar.make(root, R.string.browse_read_branch_empty, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            findNavController().navigate(
                R.id.action_global_open_post,
                bundleOf("postId" to first, "branchId" to node.id),
            )
        }
    }

    private fun openPost(postId: Long) {
        findNavController().navigate(
            R.id.action_global_open_post,
            bundleOf("postId" to postId),
        )
    }

    private companion object {
        /** Menu group for the sort choices, so they can be made mutually exclusive. */
        const val SORT_GROUP = 1
    }
}
