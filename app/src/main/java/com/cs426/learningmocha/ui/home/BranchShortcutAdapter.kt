package com.cs426.learningmocha.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.databinding.ItemHomeBranchBinding
import com.cs426.learningmocha.ui.common.NodeDiffCallback

class BranchShortcutAdapter(
    private val onClick: (Node) -> Unit,
) : ListAdapter<Node, BranchShortcutAdapter.Holder>(NodeDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemHomeBranchBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class Holder(private val binding: ItemHomeBranchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: Node, onClick: (Node) -> Unit) {
            binding.branchTitle.text = node.title
            binding.root.setOnClickListener { onClick(node) }
        }
    }
}
