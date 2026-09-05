package com.cs426.learningmocha.data.repo

import androidx.room.withTransaction
import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.local.InlineResources
import com.cs426.learningmocha.data.local.KnowledgeSync
import com.cs426.learningmocha.data.local.SeedData
import com.cs426.learningmocha.data.local.dao.TagWithCount
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.ResourceType
import com.cs426.learningmocha.data.local.entity.Tag
import com.cs426.learningmocha.util.TitleDedup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest

data class PostDetail(
    val post: Node,
    val tags: List<Tag>,
    /** Sub-posts and any folders filed under this post, so the reader can offer them. */
    val children: List<Node>,
    val backlinks: List<Node>,
    val related: List<Node>,
    val resources: List<ResourceItem>,
    val terms: List<DictionaryEntry>,
    val titleToId: Map<String, Long>,
    val nextPost: Node? = null,
)

class PostRepository(private val db: AppDatabase) {

    private val dao = db.nodeDao()
    private val knowledge = db.knowledgeDao()

    fun observePost(id: Long): Flow<Node?> = flow {
        SeedData.ensureSeeded(db)
        emitAll(dao.observeById(id))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDetail(id: Long): Flow<PostDetail?> = observePost(id).mapLatest { node ->
        if (node == null || node.type != NodeType.POST) {
            null
        } else {
            val content = node.content.orEmpty()
            val terms = knowledge.dictionaryForReader(id).filter { entry ->
                entry.postId == id || content.contains(entry.term, ignoreCase = true)
            }
            PostDetail(
                post = node,
                tags = knowledge.tagsForPost(id),
                children = dao.getChildren(id),
                backlinks = knowledge.backlinks(id),
                related = related(id),
                resources = InlineResources.merge(id, content, knowledge.resourcesForPost(id)),
                terms = terms,
                titleToId = dao.postTitleIds().associate { it.title.lowercase() to it.id },
                nextPost = node.nextPostId?.let { dao.getById(it) }
                    ?.takeIf { it.type == NodeType.POST },
            )
        }
    }

    fun observeRecentPosts(limit: Int): Flow<List<Node>> = flow {
        SeedData.ensureSeeded(db)
        emitAll(dao.observeRecentPosts(limit))
    }

    fun observeContinueReading(limit: Int): Flow<List<Node>> = flow {
        SeedData.ensureSeeded(db)
        emitAll(dao.observeContinueReading(limit))
    }

    fun observeFavorites(): Flow<List<Node>> = flow {
        SeedData.ensureSeeded(db)
        emitAll(dao.observeFavorites())
    }

    fun observePostCount(): Flow<Int> = flow {
        SeedData.ensureSeeded(db)
        emitAll(dao.observePostCount())
    }

    fun observeDictionary(): Flow<List<DictionaryEntry>> = flow {
        SeedData.ensureSeeded(db)
        emitAll(knowledge.observeDictionary())
    }

    fun observePostsWithTag(tagId: Long): Flow<List<Node>> = flow {
        SeedData.ensureSeeded(db)
        emitAll(knowledge.observePostsWithTag(tagId))
    }

    suspend fun getTag(id: Long): Tag? = knowledge.getTag(id)

    /** Alphabetical tag index. One query, so the screen never reads a post row to count it. */
    suspend fun tagCounts(): List<TagWithCount> {
        SeedData.ensureSeeded(db)
        return knowledge.tagCounts()
    }

    suspend fun postTitles(): List<String> = dao.postTitleIds().map { it.title }

    suspend fun tagsForPost(id: Long): List<Tag> = knowledge.tagsForPost(id)

    /**
     * Creates a post. [parentId] may name another post — that is a sub-post, and Browse walks
     * into it like any other container. A title another post already holds is numbered rather
     * than refused (see [TitleDedup]), so a create never costs the user the write; the caller
     * learns which title was actually used by reading the returned row.
     */
    suspend fun createPost(
        parentId: Long?,
        title: String,
        content: String,
        status: LearningStatus = LearningStatus.READING,
        tagNames: List<String> = emptyList(),
        terms: List<DictionaryEntry> = emptyList(),
        icon: String? = null,
        color: String? = null,
        nextPostId: Long? = null,
    ): Long {
        val wanted = title.trim()
        require(wanted.isNotEmpty()) { "Title is required" }
        val max = if (parentId == null) dao.maxOrderRoot() else dao.maxOrder(parentId)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val trimmed = freeTitle(wanted, null)
            val id = dao.insert(
                Node(
                    parentId = parentId,
                    type = NodeType.POST,
                    title = trimmed,
                    content = content,
                    status = status,
                    orderIndex = max + 1,
                    createdAt = now,
                    updatedAt = now,
                    icon = icon,
                    color = color,
                    nextPostId = nextPostId?.takeUnless { it == 0L },
                ),
            )
            KnowledgeSync.reindex(db, id, content)
            KnowledgeSync.replaceTags(db, id, tagNames)
            terms.forEach { term ->
                KnowledgeSync.addTerm(db, id, term.term, term.definition, term.meaningVi)
            }
            id
        }
    }

    suspend fun savePost(
        id: Long,
        title: String,
        content: String,
        status: LearningStatus,
        tagNames: List<String>,
        terms: List<DictionaryEntry>,
        icon: String? = null,
        color: String? = null,
        nextPostId: Long? = null,
        writeMarks: Boolean = true,
    ) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Title is required" }
        db.withTransaction {
            val node = dao.getById(id) ?: return@withTransaction
            if (trimmed != node.title) {
                requireTitleFree(trimmed, id)
                KnowledgeSync.retitle(db, node, trimmed)
            }
            dao.update(
                node.copy(
                    title = trimmed,
                    content = content,
                    status = status,
                    updatedAt = System.currentTimeMillis(),
                    icon = if (writeMarks) icon else node.icon,
                    color = if (writeMarks) color else node.color,
                    nextPostId = if (writeMarks) {
                        nextPostId?.takeUnless { it == 0L || it == id }
                    } else {
                        node.nextPostId
                    },
                ),
            )
            KnowledgeSync.reindex(db, id, content)
            KnowledgeSync.replaceTags(db, id, tagNames)
            terms.forEach { term ->
                KnowledgeSync.addTerm(db, id, term.term, term.definition, term.meaningVi)
            }
        }
    }

    suspend fun setMark(id: Long, icon: String?, color: String?) {
        val node = dao.getById(id) ?: return
        dao.update(node.copy(icon = icon, color = color, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setNext(id: Long, nextPostId: Long?) {
        val node = dao.getById(id) ?: return
        val next = nextPostId?.takeUnless { it == 0L || it == id }
        dao.update(node.copy(nextPostId = next, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        val node = dao.getById(id) ?: return
        dao.update(node.copy(favorite = favorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setStatus(id: Long, status: LearningStatus) {
        val node = dao.getById(id) ?: return
        dao.update(node.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    /** Upsert: the same term in the same scope is edited, never duplicated. @return its row id */
    suspend fun addTerm(
        postId: Long?,
        term: String,
        definition: String,
        meaningVi: String,
    ): Long = KnowledgeSync.addTerm(db, postId, term, definition, meaningVi)

    suspend fun updateTerm(entry: DictionaryEntry) {
        require(entry.term.isNotBlank()) { "Term is required" }
        knowledge.updateEntry(entry.copy(term = entry.term.trim()))
    }

    suspend fun removeTerm(id: Long) {
        knowledge.deleteEntry(id)
    }

    suspend fun getTerm(postId: Long?, term: String): DictionaryEntry? =
        knowledge.findEntry(postId, term.trim())

    suspend fun findPostByTitle(title: String): Node? = dao.findPostByTitle(title)

    /** Complements the Backlinks section rather than repeating it, so both are worth reading. */
    suspend fun related(postId: Long, limit: Int = 5): List<Node> {
        val scores = HashMap<Long, Int>()
        val backlinkIds = knowledge.backlinks(postId).map { it.id }.toSet()
        fun score(id: Long, points: Int) {
            if (id == postId || id in backlinkIds) return
            scores[id] = (scores[id] ?: 0) + points
        }
        for (tag in knowledge.tagsForPost(postId)) {
            for (node in knowledge.postsWithTag(tag.id)) {
                score(node.id, 2)
            }
        }
        for (target in knowledge.outgoingTargets(postId)) {
            score(target.id, 2)
            for (node in knowledge.backlinks(target.id)) {
                score(node.id, 1)
            }
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { dao.getById(it.key) }
    }

    suspend fun updatePost(
        id: Long,
        title: String? = null,
        content: String? = null,
        status: LearningStatus? = null,
        tagNames: List<String>? = null,
    ) {
        val node = dao.getById(id) ?: return
        val tags = tagNames ?: knowledge.tagsForPost(id).map { it.name }
        savePost(
            id = id,
            title = title ?: node.title,
            content = content ?: node.content.orEmpty(),
            status = status ?: node.status,
            tagNames = tags,
            terms = emptyList(),
            writeMarks = false,
        )
    }

    /** Read-then-replace, so the whole edit is one transaction like the editor's save path. */
    suspend fun addTag(postId: Long, name: String) {
        db.withTransaction {
            val current = knowledge.tagsForPost(postId).map { it.name }
            KnowledgeSync.replaceTags(db, postId, current + name)
        }
    }

    suspend fun removeTag(postId: Long, name: String) {
        db.withTransaction {
            val current = knowledge.tagsForPost(postId).map { it.name }
                .filter { !it.equals(name, ignoreCase = true) }
            KnowledgeSync.replaceTags(db, postId, current)
        }
    }

    suspend fun addWikiLink(fromId: Long, toTitle: String) {
        val node = dao.getById(fromId) ?: return
        val needle = "[[${toTitle.trim()}]]"
        val content = node.content.orEmpty()
        if (content.contains(needle, ignoreCase = true)) return
        val next = if (content.isBlank()) needle else "$content\n\n$needle"
        dao.update(node.copy(content = next, updatedAt = System.currentTimeMillis()))
        KnowledgeSync.reindex(db, fromId, next)
    }

    suspend fun removeWikiLink(fromId: Long, toTitle: String) {
        val node = dao.getById(fromId) ?: return
        val pattern = Regex("\\[\\[\\s*" + Regex.escape(toTitle.trim()) + "\\s*]]", RegexOption.IGNORE_CASE)
        val next = pattern.replace(node.content.orEmpty(), "")
        dao.update(node.copy(content = next, updatedAt = System.currentTimeMillis()))
        KnowledgeSync.reindex(db, fromId, next)
    }

    /** @return the row id, so an AI-added reference can be undone */
    suspend fun addResource(
        postId: Long,
        type: ResourceType,
        title: String,
        url: String,
    ): Long = knowledge.insertResource(
        ResourceItem(
            postId = postId,
            type = type,
            title = title.ifBlank { type.name },
            url = url,
        ),
    )

    /** Only stored rows can be removed here; an inline YouTube card goes away with its markdown. */
    suspend fun removeResource(id: Long) {
        if (id == 0L) return
        knowledge.deleteResource(id)
    }

    suspend fun titleToId(): Map<String, Long> =
        dao.postTitleIds().associate { it.title.lowercase() to it.id }

    /**
     * Rename that keeps inbound `[[wiki-links]]` alive: every linking post's markdown is
     * rewritten in the same transaction. [TreeRepository.rename] applies the same rewrite, so a
     * rename from Browse and one from the editor behave identically.
     */
    suspend fun renamePost(id: Long, newTitle: String) {
        val trimmed = newTitle.trim()
        require(trimmed.isNotEmpty()) { "Title is required" }
        db.withTransaction {
            val node = dao.getById(id) ?: return@withTransaction
            require(node.type == NodeType.POST) { "Only a post can be renamed here" }
            if (trimmed == node.title) return@withTransaction
            requireTitleFree(trimmed, id)
            KnowledgeSync.retitle(db, node, trimmed)
        }
    }

    /**
     * Titles address posts everywhere — wiki-links, AI actions, `findPostByTitle` — so two posts
     * sharing one title makes every lookup pick an arbitrary winner. Only checked when the title
     * changes, so a library that already holds duplicates (an old merge import) stays editable.
     */
    private suspend fun requireTitleFree(title: String, selfId: Long?) {
        val existing = dao.findPostByTitle(title)
        require(existing == null || existing.id == selfId) {
            "A post called \"$title\" already exists"
        }
    }

    /**
     * The title [wanted] if it is free, else the first numbered variant that is — "Raft (2)",
     * "Raft (3)", and so on. [selfId] is the post being written, so re-saving a post under its
     * own title is not treated as a clash with itself.
     */
    suspend fun freeTitle(wanted: String, selfId: Long?): String {
        val taken = dao.postTitleIds()
            .filter { it.id != selfId }
            .mapTo(HashSet()) { TitleDedup.lower(it.title) }
        return TitleDedup.unique(wanted, taken)
    }

    suspend fun touch(id: Long) {
        val node = dao.getById(id) ?: return
        if (node.type != NodeType.POST) return
        dao.update(node.copy(updatedAt = System.currentTimeMillis()))
    }

}
