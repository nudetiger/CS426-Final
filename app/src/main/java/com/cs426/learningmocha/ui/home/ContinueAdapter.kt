package com.cs426.learningmocha.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.databinding.ItemHomeContinueBinding
import com.cs426.learningmocha.ui.common.NodeDiffCallback

class ContinueAdapter(
    private val onClick: (Node) -> Unit,
) : ListAdapter<Node, ContinueAdapter.Holder>(NodeDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemHomeContinueBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class Holder(private val binding: ItemHomeContinueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: Node, onClick: (Node) -> Unit) {
            binding.continueTitle.text = node.title
            binding.continueCaption.text = when (node.status) {
                LearningStatus.IN_PROGRESS -> binding.root.context.getString(R.string.status_in_progress)
                LearningStatus.READING -> binding.root.context.getString(R.string.status_reading)
                else -> binding.root.context.getString(R.string.browse_type_post)
            }
            binding.root.setOnClickListener { onClick(node) }
        }
    }
}
