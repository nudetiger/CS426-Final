package com.cs426.learningmocha.ui.common

import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.NodeType

/**
 * The one place that decides what colour a learning status or a node type is drawn in.
 *
 * Browse rows, the reader's status button, Home's cards and the folder meter all read from
 * here, so a post that looks "amber, reading" on one screen looks the same on the next — the
 * colour is the label, and a second opinion about the mapping would undo that.
 *
 * The ids are theme attributes rather than colour resources, so a named palette can restate
 * the whole mapping; resolve one with Context.themeColor().
 *
 * Each token comes in two weights: `ink` for anything drawn on the page background (dots,
 * bars, text) and `wash` for anything the ink then sits on top of (pills, tinted cards).
 */
object NodePalette {

    @AttrRes
    fun statusInk(status: LearningStatus): Int = when (status) {
        LearningStatus.NONE -> R.attr.statusNoneInk
        LearningStatus.READING -> R.attr.statusReadingInk
        LearningStatus.IN_PROGRESS -> R.attr.statusProgressInk
        LearningStatus.FINISHED -> R.attr.statusFinishedInk
    }

    @AttrRes
    fun statusWash(status: LearningStatus): Int = when (status) {
        LearningStatus.NONE -> R.attr.statusNoneWash
        LearningStatus.READING -> R.attr.statusReadingWash
        LearningStatus.IN_PROGRESS -> R.attr.statusProgressWash
        LearningStatus.FINISHED -> R.attr.statusFinishedWash
    }

    @AttrRes
    fun typeInk(type: NodeType): Int = when (type) {
        NodeType.BRANCH -> R.attr.typeBranchInk
        NodeType.FOLDER -> R.attr.typeFolderInk
        NodeType.POST -> R.attr.typePostInk
    }

    @AttrRes
    fun typeWash(type: NodeType): Int = when (type) {
        NodeType.BRANCH -> R.attr.typeBranchWash
        NodeType.FOLDER -> R.attr.typeFolderWash
        NodeType.POST -> R.attr.typePostWash
    }

    /** [hasChildren] picks the sub-post glyph, so a post with a tree under it says so. */
    @DrawableRes
    fun icon(type: NodeType, hasChildren: Boolean = false): Int = when (type) {
        NodeType.BRANCH -> R.drawable.ic_branch
        NodeType.FOLDER -> R.drawable.ic_folder
        NodeType.POST -> if (hasChildren) R.drawable.ic_subpost else R.drawable.ic_post
    }

    fun typeLabelRes(type: NodeType): Int = when (type) {
        NodeType.BRANCH -> R.string.browse_type_branch
        NodeType.FOLDER -> R.string.browse_type_folder
        NodeType.POST -> R.string.browse_type_post
    }

    fun contentDescriptionRes(type: NodeType): Int = when (type) {
        NodeType.BRANCH -> R.string.cd_branch
        NodeType.FOLDER -> R.string.cd_folder
        NodeType.POST -> R.string.cd_post
    }

    /**
     * Chat modes, keyed by the stored mode string. A combined mode ("modify+organize") takes
     * the colour of its first part: the bubble only has to say which family of request it was,
     * and the mode chips above it still show the exact combination.
     */
    @AttrRes
    fun modeInk(mode: String): Int = when (primary(mode)) {
        "suggest" -> R.attr.modeSuggestInk
        "modify" -> R.attr.modeModifyInk
        "organize" -> R.attr.modeOrganizeInk
        else -> R.attr.modeAnswerInk
    }

    @AttrRes
    fun modeWash(mode: String): Int = when (primary(mode)) {
        "suggest" -> R.attr.modeSuggestWash
        "modify" -> R.attr.modeModifyWash
        "organize" -> R.attr.modeOrganizeWash
        else -> R.attr.modeAnswerWash
    }

    fun modeLabelRes(mode: String): Int = when (primary(mode)) {
        "suggest" -> R.string.chat_mode_suggest
        "modify" -> R.string.chat_mode_modify
        "organize" -> R.string.chat_mode_organize
        else -> R.string.chat_mode_answer
    }

    private fun primary(mode: String): String =
        mode.substringBefore('+').trim().lowercase()
}
