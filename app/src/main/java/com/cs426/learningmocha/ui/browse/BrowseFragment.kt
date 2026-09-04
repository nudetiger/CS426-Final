package com.cs426.learningmocha.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.databinding.DialogCreateNodeBinding
import com.cs426.learningmocha.databinding.DialogNodeTitleBinding
import com.cs426.learningmocha.databinding.FragmentBrowseBinding
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.viewmodel.BrowseLocatorViewModel
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
                NodeType.POST -> openPost(node.id)
                NodeType.BRANCH, NodeType.FOLDER -> viewModel.open(node.id)
            }
        },
        onMenu = { node, anchor -> showNodeMenu(node, anchor) },
    )

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

        locator.consume()?.let { viewModel.open(it) }

        b.browseList.layoutManager = LinearLayoutManager(requireContext())
        b.browseList.adapter = adapter
        ItemTouchHelper(touchCallback).attachToRecyclerView(b.browseList)

        b.browseFab.setOnClickListener { showCreateDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        b.browseTitle.text = state.title
                        backCallback.isEnabled = state.parentId != null
                        renderBreadcrumbs(state.breadcrumbs)
                        adapter.submitList(state.children)
                        ListStateBinder.bind(
                            overlay = b.listState.root,
                            progress = b.listState.listStateProgress,
                            message = b.listState.listStateMessage,
                            retry = b.listState.listStateRetry,
                            content = b.browseList,
                            state = state.listState,
                            emptyText = getString(R.string.browse_empty),
                            errorText = state.errorMessage,
                            offlineText = getString(R.string.state_offline),
                            onRetry = { },
                        )
                    }
                }
                launch {
                    viewModel.messagesFlow.collect { message ->
                        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
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

    private fun showNodeMenu(node: Node, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.menu_node, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_rename -> {
                        showRenameDialog(node)
                        true
                    }
                    R.id.action_move -> {
                        showMoveDialog(node)
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
                        bundleOf("postId" to 0L, "parentId" to parentId),
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
            labels.addAll(parents.map { it.title })
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

    private fun openPost(postId: Long) {
        findNavController().navigate(
            R.id.action_global_open_post,
            bundleOf("postId" to postId),
        )
    }

    private val touchCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.START,
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            val node = adapter.currentList.getOrNull(position) ?: return
            confirmDelete(node, onCancel = { adapter.notifyItemChanged(position) })
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewModel.persistOrder(adapter.currentIds())
        }
    }
}
