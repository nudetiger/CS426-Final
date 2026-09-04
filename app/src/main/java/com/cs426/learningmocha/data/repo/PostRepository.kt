package com.cs426.learningmocha.data.repo

import androidx.room.withTransaction
import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.local.KnowledgeSync
import com.cs426.learningmocha.data.local.SeedData
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.Tag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest

data class PostDetail(
    val post: Node,
    val tags: List<Tag>,
    val backlinks: List<Node>,
    val related: List<Node>,
    val resources: List<ResourceItem>,
    val terms: List<DictionaryEntry>,
    val titleToId: Map<String, Long>,
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
                backlinks = knowledge.backlinks(id),
                related = related(id),
                resources = knowledge.resourcesForPost(id),
                terms = terms,
                titleToId = dao.getPosts().associate { it.title.lowercase() to it.id },
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

    suspend fun postTitles(): List<String> = dao.getPosts().map { it.title }

    suspend fun tagsForPost(id: Long): List<Tag> = knowledge.tagsForPost(id)

    suspend fun createPost(
        parentId: Long?,
        title: String,
        content: String,
        status: LearningStatus = LearningStatus.READING,
        tagNames: List<String> = emptyList(),
        terms: List<DictionaryEntry> = emptyList(),
    ): Long {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Title is required" }
        val max = if (parentId == null) dao.maxOrderRoot() else dao.maxOrder(parentId)
        val now = System.currentTimeMillis()
        return db.withTransaction {
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
    ) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Title is required" }
        db.withTransaction {
            val node = dao.getById(id) ?: return@withTransaction
            dao.update(
                node.copy(
                    title = trimmed,
                    content = content,
                    status = status,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            KnowledgeSync.reindex(db, id, content)
            KnowledgeSync.replaceTags(db, id, tagNames)
            terms.forEach { term ->
                KnowledgeSync.addTerm(db, id, term.term, term.definition, term.meaningVi)
            }
        }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        val node = dao.getById(id) ?: return
        dao.update(node.copy(favorite = favorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setStatus(id: Long, status: LearningStatus) {
        val node = dao.getById(id) ?: return
        dao.update(node.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addTerm(postId: Long?, term: String, definition: String, meaningVi: String) {
        KnowledgeSync.addTerm(db, postId, term, definition, meaningVi)
    }

    suspend fun titleToId(): Map<String, Long> =
        dao.getPosts().associate { it.title.lowercase() to it.id }

    suspend fun touch(id: Long) {
        val node = dao.getById(id) ?: return
        if (node.type != NodeType.POST) return
        dao.update(node.copy(updatedAt = System.currentTimeMillis()))
    }

    private suspend fun related(postId: Long, limit: Int = 5): List<Node> {
        val scores = HashMap<Long, Int>()
        for (tag in knowledge.tagsForPost(postId)) {
            for (node in knowledge.postsWithTag(tag.id)) {
                if (node.id != postId) {
                    scores[node.id] = (scores[node.id] ?: 0) + 2
                }
            }
        }
        for (target in knowledge.outgoingTargets(postId)) {
            for (node in knowledge.backlinks(target.id)) {
                if (node.id != postId) {
                    scores[node.id] = (scores[node.id] ?: 0) + 1
                }
            }
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { dao.getById(it.key) }
    }
}
