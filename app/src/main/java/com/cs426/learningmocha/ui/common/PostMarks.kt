package com.cs426.learningmocha.ui.common

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.util.PostMarkCatalog

/**
 * Glyph + tint a post can wear, so two articles on the same topic still look distinct in a list.
 *
 * Keys live in [PostMarkCatalog] so the AI action path can validate without touching resources.
 */
object PostMarks {

    data class Icon(val key: String, @DrawableRes val drawable: Int, @StringRes val label: Int)

    data class Tint(val key: String, @AttrRes val attr: Int, @StringRes val label: Int)

    val ICONS = listOf(
        Icon("page", R.drawable.ic_post, R.string.mark_icon_page),
        Icon("book", R.drawable.ic_dictionary, R.string.mark_icon_book),
        Icon("folder", R.drawable.ic_folder, R.string.mark_icon_folder),
        Icon("branch", R.drawable.ic_branch, R.string.mark_icon_branch),
        Icon("star", R.drawable.ic_star, R.string.mark_icon_star),
        Icon("tag", R.drawable.ic_tag, R.string.mark_icon_tag),
        Icon("chat", R.drawable.ic_nav_chat, R.string.mark_icon_chat),
        Icon("graph", R.drawable.ic_graph, R.string.mark_icon_graph),
        Icon("coffee", R.drawable.ic_coffee, R.string.mark_icon_coffee),
        Icon("play", R.drawable.ic_play, R.string.mark_icon_play),
        Icon("palette", R.drawable.ic_palette, R.string.mark_icon_palette),
        Icon("search", R.drawable.ic_nav_search, R.string.mark_icon_search),
    )

    val TINTS = listOf(
        Tint("brown", R.attr.mochaBrown, R.string.mark_color_brown),
        Tint("sage", R.attr.mochaSage, R.string.mark_color_sage),
        Tint("amber", R.attr.statusReadingInk, R.string.mark_color_amber),
        Tint("blue", R.attr.statusProgressInk, R.string.mark_color_blue),
        Tint("green", R.attr.statusFinishedInk, R.string.mark_color_green),
        Tint("gold", R.attr.markGoldInk, R.string.mark_color_gold),
        Tint("sky", R.attr.markSkyInk, R.string.mark_color_sky),
        Tint("violet", R.attr.markVioletInk, R.string.mark_color_violet),
        Tint("rose", R.attr.mochaFavorite, R.string.mark_color_rose),
    )

    fun iconOf(key: String?): Icon? = ICONS.firstOrNull { it.key == key }

    fun tintOf(key: String?): Tint? = TINTS.firstOrNull { it.key == key }

    @DrawableRes
    fun drawable(node: Node, hasChildren: Boolean = false): Int =
        iconOf(node.icon)?.drawable ?: NodePalette.icon(node.type, hasChildren)

    @AttrRes
    fun tintAttr(node: Node, @AttrRes fallback: Int): Int =
        tintOf(node.color)?.attr ?: fallback

    fun paint(view: ImageView, node: Node, hasChildren: Boolean = false, @AttrRes fallback: Int) {
        view.setImageResource(drawable(node, hasChildren))
        view.imageTintList = ColorStateList.valueOf(view.context.themeColor(tintAttr(node, fallback)))
    }

    fun promptKeys(): String =
        "icons: ${PostMarkCatalog.ICONS.joinToString()}; colours: ${PostMarkCatalog.COLORS.joinToString()}"
}
