package com.cs426.learningmocha.backup

import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.Link
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.PostTag
import com.cs426.learningmocha.data.local.entity.Prerequisite
import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.Tag

/**
 * Everything a `.mocha.json` backup carries.
 *
 * Chat sessions and messages are deliberately excluded: they are conversation
 * scratch, and exporting them would put chat text into a shared file, which the
 * privacy promise in docs/plan.md §22 rules out. `posts_fts` is excluded too —
 * it is a content-backed FTS4 index that Room rebuilds from `nodes`.
 */
// @JvmOverloads so the pure-Java round-trip tests (and ImportJsonReader) keep compiling as
// fields are added: Kotlin default arguments are invisible from Java without it.
data class BackupSnapshot @JvmOverloads constructor(
    val nodes: List<Node> = emptyList(),
    val links: List<Link> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val postTags: List<PostTag> = emptyList(),
    val dictionary: List<DictionaryEntry> = emptyList(),
    val resources: List<ResourceItem> = emptyList(),
    val prerequisites: List<Prerequisite> = emptyList(),
) {
    val isEmpty: Boolean get() = nodes.isEmpty() && tags.isEmpty() && dictionary.isEmpty()

    val postCount: Int
        get() = nodes.count { it.type == com.cs426.learningmocha.data.local.entity.NodeType.POST }

    companion object {
        const val FORMAT = "mocha.backup"
        const val VERSION = 1
    }
}
