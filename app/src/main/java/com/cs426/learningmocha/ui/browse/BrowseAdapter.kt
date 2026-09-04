package com.cs426.learningmocha.ui.browse

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.databinding.ItemTreeNodeBinding
import com.cs426.learningmocha.ui.common.NodeDiffCallback

class BrowseAdapter(
    private val onClick: (Node) -> Unit,
    private val onMenu: (Node, View) -> Unit,
) : ListAdapter<Node, BrowseAdapter.Holder>(NodeDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemTreeNodeBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), position, onClick, onMenu)
    }

    fun currentIds(): List<Long> = currentList.map { it.id }

    fun onItemMove(from: Int, to: Int) {
        if (from == to) return
        val mutable = currentList.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        submitList(mutable)
    }

    class Holder(private val binding: ItemTreeNodeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            node: Node,
            position: Int,
            onClick: (Node) -> Unit,
            onMenu: (Node, View) -> Unit,
        ) {
            binding.nodeTitle.text = node.title
            val (icon, caption, description) = when (node.type) {
                NodeType.BRANCH -> Triple(
                    R.drawable.ic_branch,
                    R.string.browse_type_branch,
                    R.string.cd_branch,
                )
                NodeType.FOLDER -> Triple(
                    R.drawable.ic_folder,
                    R.string.browse_type_folder,
                    R.string.cd_folder,
                )
                NodeType.POST -> Triple(
                    R.drawable.ic_post,
                    R.string.browse_type_post,
                    R.string.cd_post,
                )
            }
            binding.nodeIcon.setImageResource(icon)
            binding.nodeIcon.contentDescription = binding.root.context.getString(description)
            binding.nodeCaption.setText(caption)
            binding.root.setBackgroundColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (position % 2 == 1) R.color.mocha_list_row_odd else R.color.mocha_cream,
                ),
            )
            binding.root.setOnClickListener { onClick(node) }
            binding.nodeMenu.setOnClickListener { onMenu(node, it) }
        }
    }
}
