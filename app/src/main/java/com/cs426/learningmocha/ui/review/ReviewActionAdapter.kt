package com.cs426.learningmocha.ui.review

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.ItemReviewActionBinding
import com.cs426.learningmocha.viewmodel.ReviewRow

class ReviewActionAdapter(
    private val indentPx: Int,
    private val onToggle: (Int) -> Unit,
    private val onOpen: (ReviewRow) -> Unit,
    private val onMenu: (ReviewRow, View) -> Unit,
) : ListAdapter<ReviewRow, ReviewActionAdapter.Holder>(Diff) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemReviewActionBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), indentPx, onToggle, onOpen, onMenu)
    }

    class Holder(private val binding: ItemReviewActionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: ReviewRow,
            indentPx: Int,
            onToggle: (Int) -> Unit,
            onOpen: (ReviewRow) -> Unit,
            onMenu: (ReviewRow, View) -> Unit,
        ) {
            val context = binding.root.context
            // Indentation is what makes a generated learning path read as a tree.
            binding.root.updatePaddingRelative(start = item.indent * indentPx)
            binding.reviewRowIcon.setImageResource(iconFor(item.op))
            binding.reviewRowTitle.text = item.title
            binding.reviewRowTitle.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.destructive) R.color.mocha_error else R.color.mocha_text_primary,
                ),
            )
            if (item.destructive) {
                binding.reviewRowIcon.setColorFilter(ContextCompat.getColor(context, R.color.mocha_error))
            } else {
                binding.reviewRowIcon.clearColorFilter()
            }
            binding.reviewRowCaption.text = item.caption
            binding.reviewRowCaption.isVisible = item.caption.isNotBlank()
            binding.reviewRowError.text = item.error.orEmpty()
            binding.reviewRowError.isVisible = item.error != null
            binding.reviewCheck.setOnCheckedChangeListener(null)
            binding.reviewCheck.isChecked = item.checked
            binding.reviewCheck.contentDescription = item.title
            binding.reviewCheck.setOnCheckedChangeListener { _, _ -> onToggle(item.index) }
            binding.reviewRowMenu.isVisible = item.preview.isNotBlank() || item.editable || item.relocatable
            binding.reviewRowMenu.setOnClickListener { anchor -> onMenu(item, anchor) }
            val previewable = item.preview.isNotBlank()
            binding.root.setOnClickListener(if (previewable) View.OnClickListener { onOpen(item) } else null)
            binding.root.isClickable = previewable
        }

        private fun iconFor(op: String): Int = when (op) {
            "create_branch" -> R.drawable.ic_branch
            "create_folder" -> R.drawable.ic_folder
            "create_post" -> R.drawable.ic_post
            "update_post", "set_status" -> R.drawable.ic_edit
            "move_post" -> R.drawable.ic_drag
            "delete_post", "remove_tag", "remove_link" -> R.drawable.ic_delete
            "create_link" -> R.drawable.ic_chevron
            "set_favorite" -> R.drawable.ic_star
            "add_resource" -> R.drawable.ic_play
            else -> R.drawable.ic_add
        }
    }

    private object Diff : DiffUtil.ItemCallback<ReviewRow>() {
        override fun areItemsTheSame(oldItem: ReviewRow, newItem: ReviewRow): Boolean =
            oldItem.index == newItem.index

        override fun areContentsTheSame(oldItem: ReviewRow, newItem: ReviewRow): Boolean =
            oldItem == newItem
    }
}
