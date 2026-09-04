package com.cs426.learningmocha.data.local

import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.Link
import com.cs426.learningmocha.data.local.entity.PostTag
import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.ResourceType
import com.cs426.learningmocha.data.local.entity.Tag
import com.cs426.learningmocha.util.MarkdownLinkParser

/** Rebuilds derived link / tag / YouTube rows for one post. Shared by save and seed. */
internal object KnowledgeSync {

    suspend fun reindex(db: AppDatabase, postId: Long, content: String) {
        val knowledge = db.knowledgeDao()
        val nodes = db.nodeDao()
        knowledge.deleteOutgoing(postId)
        knowledge.deleteResources(postId)
        val seen = HashSet<Long>()
        for (wiki in MarkdownLinkParser.wikiLinks(content)) {
            val target = nodes.findPostByTitle(wiki.title) ?: continue
            if (target.id == postId || !seen.add(target.id)) continue
            knowledge.insertLink(
                Link(
                    fromPostId = postId,
                    toPostId = target.id,
                    anchorText = wiki.title,
                ),
            )
        }
        for (youtube in MarkdownLinkParser.youtubeUrls(content)) {
            knowledge.insertResource(
                ResourceItem(
                    postId = postId,
                    type = ResourceType.YOUTUBE,
                    title = "YouTube",
                    url = youtube.url,
                ),
            )
        }
    }

    suspend fun replaceTags(db: AppDatabase, postId: Long, names: List<String>) {
        val knowledge = db.knowledgeDao()
        knowledge.deletePostTags(postId)
        val seen = HashSet<String>()
        for (raw in names) {
            val name = raw.trim()
            if (name.isEmpty()) continue
            val key = name.lowercase()
            if (!seen.add(key)) continue
            val existing = knowledge.findTag(name)
            val tagId = existing?.id ?: run {
                val inserted = knowledge.insertTag(Tag(name = name))
                if (inserted != -1L) inserted else knowledge.findTag(name)?.id ?: return@run null
            } ?: continue
            knowledge.insertPostTag(PostTag(postId = postId, tagId = tagId))
        }
    }

    suspend fun addTerm(
        db: AppDatabase,
        postId: Long?,
        term: String,
        definition: String,
        meaningVi: String,
    ) {
        val trimmed = term.trim()
        require(trimmed.isNotEmpty()) { "Term is required" }
        db.knowledgeDao().insertEntry(
            DictionaryEntry(
                postId = postId,
                term = trimmed,
                definition = definition.trim(),
                meaningVi = meaningVi.trim(),
            ),
        )
    }
}
