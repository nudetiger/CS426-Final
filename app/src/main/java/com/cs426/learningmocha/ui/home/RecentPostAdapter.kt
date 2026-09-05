package com.cs426.learningmocha.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.databinding.ItemHomeRowBinding
import com.cs426.learningmocha.ui.common.NodeDiffCallback

class RecentPostAdapter(
    private val onClick: (Node) -> Unit,
) : ListAdapter<Node, RecentPostAdapter.Holder>(NodeDiffCallback) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemHomeRowBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class Holder(private val binding: ItemHomeRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: Node, onClick: (Node) -> Unit) {
            binding.rowTitle.text = node.title
            binding.rowCaption.setText(R.string.browse_type_post)
            binding.root.setOnClickListener { onClick(node) }
        }
    }
}
