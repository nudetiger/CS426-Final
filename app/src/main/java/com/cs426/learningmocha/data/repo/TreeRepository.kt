package com.cs426.learningmocha.data.repo

import com.cs426.learningmocha.data.local.AppDatabase
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

    suspend fun rename(id: Long, title: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Title is required" }
        val node = dao.getById(id) ?: return
        dao.update(node.copy(title = trimmed, updatedAt = System.currentTimeMillis()))
    }

    suspend fun move(id: Long, newParentId: Long?) {
        val node = dao.getById(id) ?: return
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
        dao.deleteById(id)
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

    private suspend fun nextOrder(parentId: Long?): Int {
        val max = if (parentId == null) dao.maxOrderRoot() else dao.maxOrder(parentId)
        return max + 1
    }
}
