package com.cs426.learningmocha.ui.browse

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.databinding.ItemTreeNodeBinding
import com.cs426.learningmocha.ui.common.NodePalette
import com.cs426.learningmocha.ui.common.PostMarks
import com.cs426.learningmocha.ui.common.labelRes
import com.cs426.learningmocha.ui.common.themeColor

/**
 * One row as Browse needs it: the node, plus the two counts and the stripe flag that would
 * otherwise have to be recomputed at bind time.
 *
 * [stripe] rides along in the item rather than being read from the adapter position, so
 * DiffUtil repaints the rows whose parity actually changed when something is inserted above
 * them — reading parity from the position would leave stale colours behind.
 */
data class BrowseRow(
    val node: Node,
    /** Posts anywhere beneath this node; for a post, itself included. */
    val postsInside: Int,
    /** Direct children, which is what decides whether the row can be descended into. */
    val directChildren: Int,
    val stripe: Boolean,
    /** The user's "colour-code lists" setting; false draws the plainer, untinted row. */
    val colorful: Boolean = true,
)

class BrowseAdapter(
    private val onClick: (Node) -> Unit,
    private val onOpenChildren: (Node) -> Unit,
    private val onToggleFavorite: (Node) -> Unit,
    private val onMenu: (Node, View) -> Unit,
) : ListAdapter<BrowseRow, BrowseAdapter.Holder>(Diff) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(ItemTreeNodeBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), onClick, onOpenChildren, onToggleFavorite, onMenu)
    }

    class Holder(private val binding: ItemTreeNodeBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            row: BrowseRow,
            onClick: (Node) -> Unit,
            onOpenChildren: (Node) -> Unit,
            onToggleFavorite: (Node) -> Unit,
            onMenu: (Node, View) -> Unit,
        ) {
            val node = row.node
            val context = binding.root.context
            val isPost = node.type == NodeType.POST
            val hasChildren = row.directChildren > 0

            binding.root.setBackgroundColor(
                color(
                    when {
                        !row.colorful -> R.attr.mochaRowEven
                        row.stripe -> R.attr.mochaRowOdd
                        else -> R.attr.mochaRowEven
                    },
                ),
            )
            binding.nodeTitle.text = node.title

            // A post is coloured by how far through it the user is; a container by what it is.
            // Both use the same rail and icon tint, so the row reads the same way either way.
            // With colour off the rail follows the node type only, which keeps branches,
            // folders and posts apart without painting progress across the whole list.
            val accent = if (isPost && row.colorful) {
                NodePalette.statusInk(node.status)
            } else {
                NodePalette.typeInk(node.type)
            }
            binding.nodeAccent.backgroundTintList = ColorStateList.valueOf(color(accent))
            binding.nodeIcon.setImageResource(PostMarks.drawable(node, hasChildren))
            binding.nodeIcon.imageTintList = ColorStateList.valueOf(
                color(PostMarks.tintAttr(node, accent)),
            )
            binding.nodeIcon.contentDescription =
                context.getString(NodePalette.contentDescriptionRes(node.type))

            // The pill stays whatever the colour setting is: it is the label, not decoration.
            binding.nodeStatus.isVisible = isPost
            if (isPost) {
                binding.nodeStatus.setText(node.status.labelRes())
                binding.nodeStatus.setTextColor(
                    color(
                        if (row.colorful) {
                            NodePalette.statusInk(node.status)
                        } else {
                            R.attr.mochaTextSecondary
                        },
                    ),
                )
                binding.nodeStatus.backgroundTintList = ColorStateList.valueOf(
                    color(
                        if (row.colorful) {
                            NodePalette.statusWash(node.status)
                        } else {
                            R.attr.mochaCreamDark
                        },
                    ),
                )
            }
            binding.nodeCaption.text = caption(row)

            binding.nodeFavorite.isVisible = isPost
            if (isPost) {
                binding.nodeFavorite.setImageResource(
                    if (node.favorite) R.drawable.ic_star else R.drawable.ic_star_border,
                )
                binding.nodeFavorite.imageTintList = ColorStateList.valueOf(
                    color(if (node.favorite) R.attr.mochaFavorite else R.attr.mochaTextSecondary),
                )
                binding.nodeFavorite.contentDescription = context.getString(
                    if (node.favorite) R.string.cd_favorite_remove else R.string.cd_favorite_add,
                )
                binding.nodeFavorite.setOnClickListener { onToggleFavorite(node) }
            } else {
                binding.nodeFavorite.setOnClickListener(null)
            }

            // Only a post needs this: tapping a post opens it to read, so descending into its
            // sub-posts needs a control of its own. A container descends on the row tap.
            binding.nodeChildren.isVisible = isPost && hasChildren
            binding.nodeChildren.setOnClickListener { onOpenChildren(node) }

            binding.root.setOnClickListener { onClick(node) }
            binding.nodeMenu.setOnClickListener { onMenu(node, it) }
        }

        /**
         * A post says only what is under it: the status pill sits immediately to the left and
         * the icon is already a page, so repeating the word "Post" cost the sub-post count the
         * room it needed and got it ellipsized away. Containers keep their type word, since
         * branch and folder are a real distinction the icon alone does not carry.
         */
        private fun caption(row: BrowseRow): String {
            val context = binding.root.context
            if (row.node.type == NodeType.POST) {
                if (row.directChildren == 0) return ""
                return context.resources.getQuantityString(
                    R.plurals.browse_row_sub_posts,
                    row.directChildren,
                    row.directChildren,
                )
            }
            val type = context.getString(NodePalette.typeLabelRes(row.node.type))
            val detail = if (row.postsInside > 0) {
                context.resources.getQuantityString(
                    R.plurals.browse_row_contains,
                    row.postsInside,
                    row.postsInside,
                )
            } else {
                context.getString(R.string.browse_row_empty_container)
            }
            return "$type · $detail"
        }

        @ColorInt
        private fun color(@AttrRes res: Int): Int = binding.root.context.themeColor(res)
    }

    private object Diff : DiffUtil.ItemCallback<BrowseRow>() {
        override fun areItemsTheSame(oldItem: BrowseRow, newItem: BrowseRow): Boolean =
            oldItem.node.id == newItem.node.id

        override fun areContentsTheSame(oldItem: BrowseRow, newItem: BrowseRow): Boolean =
            oldItem == newItem
    }
}
