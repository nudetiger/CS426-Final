package com.cs426.learningmocha.ui.dictionary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.databinding.DialogDictionaryTermBinding
import com.cs426.learningmocha.databinding.FragmentDictionaryBinding
import com.cs426.learningmocha.databinding.ItemDictionaryBinding
import com.cs426.learningmocha.ui.common.ListStateBinder
import com.cs426.learningmocha.viewmodel.DictionaryRow
import com.cs426.learningmocha.viewmodel.DictionaryScope
import com.cs426.learningmocha.viewmodel.DictionaryViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Global glossary with full CRUD: the FAB creates a global term, a row opens it for editing and
 * the row menu deletes it with an undo. Writes go through the ViewModel, never straight to Room.
 */
class DictionaryFragment : Fragment() {

    private var binding: FragmentDictionaryBinding? = null
    private val viewModel: DictionaryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentDictionaryBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        val rows = DictionaryAdapter(::editTerm, ::showRowMenu)
        b.dictionaryBack.setOnClickListener { findNavController().popBackStack() }
        b.dictionaryList.layoutManager = LinearLayoutManager(requireContext())
        b.dictionaryList.adapter = rows
        b.dictionaryFab.setOnClickListener { addTerm() }

        b.dictionarySearch.doAfterTextChanged { viewModel.query.value = it?.toString().orEmpty() }
        b.dictionarySearch.setOnEditorActionListener { field, _, _ ->
            field.clearFocus()
            WindowCompat.getInsetsController(requireActivity().window, field)
                .hide(WindowInsetsCompat.Type.ime())
            true
        }
        b.dictionaryChips.setOnCheckedStateChangeListener { _, checked ->
            viewModel.scope.value = when (checked.firstOrNull()) {
                R.id.dictionary_chip_global -> DictionaryScope.GLOBAL
                R.id.dictionary_chip_post -> DictionaryScope.POST
                else -> DictionaryScope.ALL
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val empty = if (state.filtered) {
                            R.string.dict_empty_filtered
                        } else {
                            R.string.dict_empty
                        }
                        ListStateBinder.bind(
                            overlay = b.listState.root,
                            progress = b.listState.listStateProgress,
                            message = b.listState.listStateMessage,
                            retry = b.listState.listStateRetry,
                            content = b.dictionaryList,
                            state = state.listState,
                            emptyText = getString(empty),
                            errorText = state.errorMessage,
                            offlineText = getString(R.string.state_offline),
                            onRetry = { },
                        )
                        rows.submitList(state.rows)
                    }
                }
                launch {
                    viewModel.errorFlow.collect { message ->
                        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Releases the observer and the old view tree while this fragment sits on the back stack.
        binding?.dictionaryList?.adapter = null
        binding = null
    }

    private fun addTerm() {
        val fields = DialogDictionaryTermBinding.inflate(layoutInflater)
        fields.termHeading.setText(R.string.dict_add_title)
        fields.termScope.setText(R.string.dict_scope_global)
        fields.termScope.visibility = View.VISIBLE
        showTermDialog(fields) { term, definition, meaningVi ->
            viewModel.addTerm(term, definition, meaningVi)
        }
    }

    private fun editTerm(row: DictionaryRow) {
        val fields = DialogDictionaryTermBinding.inflate(layoutInflater)
        fields.termHeading.setText(R.string.dict_edit_title)
        fields.termScope.text = scopeLabel(row)
        fields.termScope.visibility = View.VISIBLE
        fields.termInput.setText(row.entry.term)
        fields.termDefinition.setText(row.entry.definition)
        fields.termVi.setText(row.entry.meaningVi)
        showTermDialog(fields) { term, definition, meaningVi ->
            viewModel.updateTerm(row.entry, term, definition, meaningVi)
        }
    }

    private fun showTermDialog(
        fields: DialogDictionaryTermBinding,
        onSave: (String, String, String) -> Unit,
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setView(fields.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val root = binding?.root ?: return@setPositiveButton
                val term = fields.termInput.text?.toString().orEmpty().trim()
                if (term.isEmpty()) {
                    Snackbar.make(root, R.string.dict_term_required, Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onSave(
                    term,
                    fields.termDefinition.text?.toString().orEmpty(),
                    fields.termVi.text?.toString().orEmpty(),
                )
                Snackbar.make(root, R.string.dict_saved, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showRowMenu(anchor: View, row: DictionaryRow) {
        val menu = PopupMenu(requireContext(), anchor)
        menu.menu.add(R.string.action_edit).setOnMenuItemClickListener {
            editTerm(row)
            true
        }
        val postId = row.entry.postId
        if (postId != null) {
            menu.menu.add(R.string.dictionary_open_post).setOnMenuItemClickListener {
                findNavController().navigate(
                    R.id.action_global_open_post,
                    bundleOf("postId" to postId),
                )
                true
            }
        }
        menu.menu.add(R.string.action_delete).setOnMenuItemClickListener {
            deleteTerm(row.entry)
            true
        }
        menu.show()
    }

    private fun deleteTerm(entry: DictionaryEntry) {
        val root = binding?.root ?: return
        viewModel.deleteTerm(entry)
        Snackbar.make(
            root,
            getString(R.string.dict_deleted, entry.term),
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.dict_undo) { viewModel.undoDelete() }.show()
    }

    private fun scopeLabel(row: DictionaryRow): String = when {
        row.entry.postId == null -> getString(R.string.dict_scope_global)
        row.postTitle != null -> getString(R.string.dict_scope_post, row.postTitle)
        else -> getString(R.string.dict_scope_post_unknown)
    }

    private class DictionaryAdapter(
        private val onClick: (DictionaryRow) -> Unit,
        private val onMenu: (View, DictionaryRow) -> Unit,
    ) : ListAdapter<DictionaryRow, DictionaryAdapter.Holder>(Diff) {

        init {
            stateRestorationPolicy =
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val inflater = LayoutInflater.from(parent.context)
            return Holder(ItemDictionaryBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position), onClick, onMenu)
        }

        class Holder(
            private val binding: ItemDictionaryBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(
                row: DictionaryRow,
                onClick: (DictionaryRow) -> Unit,
                onMenu: (View, DictionaryRow) -> Unit,
            ) {
                val context = binding.root.context
                binding.dictTerm.text = row.entry.term
                binding.dictDefinition.text = row.entry.definition
                binding.dictScope.text = when {
                    row.entry.postId == null -> context.getString(R.string.dict_scope_global)
                    row.postTitle != null ->
                        context.getString(R.string.dict_scope_post, row.postTitle)
                    else -> context.getString(R.string.dict_scope_post_unknown)
                }
                binding.root.setOnClickListener { onClick(row) }
                binding.dictMenu.setOnClickListener { onMenu(binding.dictMenu, row) }
            }
        }

        private object Diff : DiffUtil.ItemCallback<DictionaryRow>() {
            override fun areItemsTheSame(oldItem: DictionaryRow, newItem: DictionaryRow): Boolean =
                oldItem.entry.id == newItem.entry.id

            override fun areContentsTheSame(
                oldItem: DictionaryRow,
                newItem: DictionaryRow,
            ): Boolean = oldItem == newItem
        }
    }
}
