package com.cs426.learningmocha.ui.common

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.ItemHomeRowBinding

data class RowItem(
    val key: Long,
    val title: String,
    val caption: String,
    @DrawableRes val icon: Int = R.drawable.ic_post,
    @AttrRes val iconTint: Int = R.attr.typePostInk,
)

class RowAdapter(
    private val onClick: (RowItem) -> Unit,
    private val onLongClick: ((RowItem) -> Boolean)? = null,
) : ListAdapter<RowItem, RowAdapter.Holder>(Diff) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemHomeRowBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), position, onClick, onLongClick)
    }

    class Holder(private val binding: ItemHomeRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: RowItem,
            position: Int,
            onClick: (RowItem) -> Unit,
            onLongClick: ((RowItem) -> Boolean)?,
        ) {
            binding.root.stripe(position)
            binding.rowTitle.text = item.title
            binding.rowCaption.text = item.caption
            binding.rowIcon.setImageResource(item.icon)
            binding.rowIcon.imageTintList = ColorStateList.valueOf(
                binding.root.context.themeColor(item.iconTint),
            )
            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener { onLongClick?.invoke(item) ?: false }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RowItem>() {
        override fun areItemsTheSame(oldItem: RowItem, newItem: RowItem): Boolean =
            oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: RowItem, newItem: RowItem): Boolean =
            oldItem == newItem
    }
}
