package com.cs426.learningmocha.ui.review

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.databinding.ItemReviewActionBinding
import com.cs426.learningmocha.viewmodel.ReviewRow

class ReviewActionAdapter(
    private val indentPx: Int,
    private val onToggle: (Int) -> Unit,
    private val onOpen: (ReviewRow) -> Unit,
) : ListAdapter<ReviewRow, ReviewActionAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemReviewActionBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), indentPx, onToggle, onOpen)
    }

    class Holder(private val binding: ItemReviewActionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: ReviewRow,
            indentPx: Int,
            onToggle: (Int) -> Unit,
            onOpen: (ReviewRow) -> Unit,
        ) {
            binding.root.updatePadding(left = item.indent * indentPx)
            binding.reviewRowTitle.text = item.title
            binding.reviewRowCaption.text = item.caption
            binding.reviewRowCaption.isVisible = item.caption.isNotBlank()
            binding.reviewCheck.setOnCheckedChangeListener(null)
            binding.reviewCheck.isChecked = item.checked
            binding.reviewCheck.setOnCheckedChangeListener { _, _ -> onToggle(item.index) }
            binding.root.setOnClickListener { onOpen(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ReviewRow>() {
        override fun areItemsTheSame(oldItem: ReviewRow, newItem: ReviewRow): Boolean =
            oldItem.index == newItem.index

        override fun areContentsTheSame(oldItem: ReviewRow, newItem: ReviewRow): Boolean =
            oldItem == newItem
    }
}
