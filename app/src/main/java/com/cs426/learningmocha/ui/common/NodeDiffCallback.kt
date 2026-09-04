package com.cs426.learningmocha.ui.common

import androidx.recyclerview.widget.DiffUtil
import com.cs426.learningmocha.data.local.entity.Node

object NodeDiffCallback : DiffUtil.ItemCallback<Node>() {
    override fun areItemsTheSame(oldItem: Node, newItem: Node): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Node, newItem: Node): Boolean = oldItem == newItem
}
