package com.cs426.learningmocha.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.databinding.ItemHomeRowBinding
import com.cs426.learningmocha.ui.common.NodeDiffCallback
import com.cs426.learningmocha.ui.common.NodePalette
import com.cs426.learningmocha.ui.common.PostMarks
import com.cs426.learningmocha.ui.common.stripe

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
        holder.bind(getItem(position), position, onClick)
    }

    class Holder(private val binding: ItemHomeRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: Node, position: Int, onClick: (Node) -> Unit) {
            binding.root.stripe(position)
            binding.rowTitle.text = node.title
            binding.rowCaption.setText(R.string.browse_type_post)
            PostMarks.paint(binding.rowIcon, node, fallback = NodePalette.statusInk(node.status))
            binding.root.setOnClickListener { onClick(node) }
        }
    }
}
