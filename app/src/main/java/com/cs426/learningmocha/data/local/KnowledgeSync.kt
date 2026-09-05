package com.cs426.learningmocha.data.local

import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.Link
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.PostTag
import com.cs426.learningmocha.data.local.entity.Tag
import com.cs426.learningmocha.util.MarkdownLinkParser

/** Rebuilds derived link / tag rows for one post. Shared by save, rename and seed. */
internal object KnowledgeSync {

    /**
     * Owns the `links` table for [postId] only. `resources` is deliberately untouched: it holds
     * explicitly added references, and inline YouTube URLs are derived at read time instead
     * ([InlineResources]) so a re-save can never delete a reference someone added by hand.
     */
    suspend fun reindex(db: AppDatabase, postId: Long, content: String) {
        val knowledge = db.knowledgeDao()
        val nodes = db.nodeDao()
        knowledge.deleteOutgoing(postId)
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
    }

    /**
     * Retitles [node] and rewrites `[[old title]]` in every post that links to it, so an inbound
     * wiki-link keeps resolving instead of degrading to "not created yet" and then losing its
     * link row on the next save of the linking post.
     *
     * The node is written first: [reindex] re-resolves link targets by title, so it must see the
     * new title inside the same transaction. Caller supplies the transaction.
     */
    suspend fun retitle(db: AppDatabase, node: Node, newTitle: String) {
        val nodes = db.nodeDao()
        val sources = db.knowledgeDao().backlinks(node.id)
        val now = System.currentTimeMillis()
        nodes.update(node.copy(title = newTitle, updatedAt = now))
        for (source in sources) {
            if (source.id == node.id) continue
            val content = source.content.orEmpty()
            val rewritten = MarkdownLinkParser.renameWikiLinks(content, node.title, newTitle)
            if (rewritten == content) continue
            nodes.update(source.copy(content = rewritten, updatedAt = now))
            reindex(db, source.id, rewritten)
        }
    }

    /**
     * Owns `post_tags` for [postId]. Dropping a post's last use of a tag also drops the tag: the
     * caller's transaction covers both writes, and the purge only removes rows no post_tags row
     * points at, so a tag another post still carries survives.
     */
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
        knowledge.deleteOrphanTags()
    }

    /**
     * Upsert by (scope, term): re-adding a term the post — or the global glossary — already has
     * updates that row instead of stacking a duplicate chip in the reader.
     *
     * @return the row id of the inserted or updated entry
     */
    suspend fun addTerm(
        db: AppDatabase,
        postId: Long?,
        term: String,
        definition: String,
        meaningVi: String,
    ): Long {
        val trimmed = term.trim()
        require(trimmed.isNotEmpty()) { "Term is required" }
        val knowledge = db.knowledgeDao()
        val existing = knowledge.findEntry(postId, trimmed)
        if (existing != null) {
            knowledge.updateEntry(
                existing.copy(
                    term = trimmed,
                    definition = definition.trim(),
                    meaningVi = meaningVi.trim(),
                ),
            )
            return existing.id
        }
        return knowledge.insertEntry(
            DictionaryEntry(
                postId = postId,
                term = trimmed,
                definition = definition.trim(),
                meaningVi = meaningVi.trim(),
            ),
        )
    }
}
