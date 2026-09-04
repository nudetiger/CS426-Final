package com.cs426.learningmocha.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.databinding.ItemHomeRowBinding

data class RowItem(
    val key: Long,
    val title: String,
    val caption: String,
)

class RowAdapter(
    private val onClick: (RowItem) -> Unit,
) : ListAdapter<RowItem, RowAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemHomeRowBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class Holder(private val binding: ItemHomeRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RowItem, onClick: (RowItem) -> Unit) {
            binding.rowTitle.text = item.title
            binding.rowCaption.text = item.caption
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RowItem>() {
        override fun areItemsTheSame(oldItem: RowItem, newItem: RowItem): Boolean =
            oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: RowItem, newItem: RowItem): Boolean =
            oldItem == newItem
    }
}
