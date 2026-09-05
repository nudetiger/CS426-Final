package com.cs426.learningmocha.data.repo

import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.local.KnowledgeSync
import com.cs426.learningmocha.data.local.SeedData
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.util.TreeRules
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class TreeRepository(private val db: AppDatabase) {

    private val dao = db.nodeDao()

    fun observeChildren(parentId: Long?): Flow<List<Node>> = flow {
        SeedData.ensureSeeded(db)
        emitAll(
            if (parentId == null) dao.observeRoots() else dao.observeChildren(parentId),
        )
    }

    fun observeRootBranches(): Flow<List<Node>> = flow {
        SeedData.ensureSeeded(db)
        emitAll(dao.observeRootBranches())
    }

    suspend fun getNode(id: Long): Node? = dao.getById(id)

    suspend fun findByTitle(title: String): Node? = dao.findByTitle(title)

    suspend fun allNodes(): List<Node> = dao.getAll()

    suspend fun breadcrumbs(parentId: Long?): List<Node> {
        if (parentId == null) return emptyList()
        val chain = ArrayList<Node>()
        var cursor: Long? = parentId
        val seen = HashSet<Long>()
        while (cursor != null && seen.add(cursor)) {
            val node = dao.getById(cursor) ?: break
            chain.add(node)
            cursor = node.parentId
        }
        return chain.asReversed()
    }

    suspend fun create(parentId: Long?, type: NodeType, title: String): Long {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Title is required" }
        requireContainer(parentId)
        val order = nextOrder(parentId)
        val now = System.currentTimeMillis()
        return dao.insert(
            Node(
                parentId = parentId,
                type = type,
                title = trimmed,
                orderIndex = order,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Renaming a post also rewrites the `[[wiki-links]]` that point at it, and refuses a title
     * another post already uses — titles are how links and AI actions address posts.
     */
    suspend fun rename(id: Long, title: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Title is required" }
        db.withTransaction {
            val node = dao.getById(id) ?: return@withTransaction
            if (node.type == NodeType.POST) {
                if (trimmed == node.title) return@withTransaction
                val clash = dao.findPostByTitle(trimmed)
                require(clash == null || clash.id == id) {
                    "A post called \"$trimmed\" already exists"
                }
                KnowledgeSync.retitle(db, node, trimmed)
                return@withTransaction
            }
            dao.update(node.copy(title = trimmed, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun move(id: Long, newParentId: Long?) {
        val node = dao.getById(id) ?: return
        requireContainer(newParentId)
        val parentById = HashMap<Long, Long>()
        dao.getAll().forEach { item ->
            item.parentId?.let { parent -> parentById[item.id] = parent }
        }
        if (TreeRules.wouldCreateCycle(id, newParentId, parentById)) {
            throw IllegalArgumentException("That move would create a cycle")
        }
        val order = nextOrder(newParentId)
        dao.update(
            node.copy(
                parentId = newParentId,
                orderIndex = order,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun reorder(parentId: Long?, orderedIds: List<Long>) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                val node = dao.getById(id) ?: return@forEachIndexed
                if (node.parentId != parentId) return@forEachIndexed
                if (node.orderIndex != index) {
                    dao.update(node.copy(orderIndex = index))
                }
            }
        }
    }

    suspend fun delete(id: Long) {
        // The foreign key cascade drops the deleted subtree's post_tags rows, but a tag whose
        // last post just vanished would otherwise linger in the tag list — and in what the
        // assistant is told the library contains.
        db.withTransaction {
            dao.deleteById(id)
            db.knowledgeDao().deleteOrphanTags()
        }
    }

    suspend fun possibleParents(movingId: Long): List<Node> {
        val all = dao.getAll()
        val parentById = HashMap<Long, Long>()
        all.forEach { item ->
            item.parentId?.let { parent -> parentById[item.id] = parent }
        }
        return all.filter { it.type != NodeType.POST }
            .filter { !TreeRules.wouldCreateCycle(movingId, it.id, parentById) }
    }

    /** Only branches and folders hold children — a child of a post is invisible in Browse. */
    internal suspend fun requireContainer(parentId: Long?) {
        if (parentId == null) return
        require(dao.getById(parentId)?.type != NodeType.POST) {
            "Posts cannot contain other items"
        }
    }

    private suspend fun nextOrder(parentId: Long?): Int {
        val max = if (parentId == null) dao.maxOrderRoot() else dao.maxOrder(parentId)
        return max + 1
    }
}
