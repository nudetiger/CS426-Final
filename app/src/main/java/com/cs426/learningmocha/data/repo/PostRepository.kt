package com.cs426.learningmocha.data.repo

import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.local.SeedData
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class PostRepository(private val db: AppDatabase) {

    private val dao = db.nodeDao()

    fun observePost(id: Long): Flow<Node?> = flow {
        SeedData.ensureSeeded(dao)
        emitAll(dao.observeById(id))
    }

    fun observeRecentPosts(limit: Int): Flow<List<Node>> = flow {
        SeedData.ensureSeeded(dao)
        emitAll(dao.observeRecentPosts(limit))
    }

    fun observeContinueReading(limit: Int): Flow<List<Node>> = flow {
        SeedData.ensureSeeded(dao)
        emitAll(dao.observeContinueReading(limit))
    }

    fun observePostCount(): Flow<Int> = flow {
        SeedData.ensureSeeded(dao)
        emitAll(dao.observePostCount())
    }

    suspend fun createPost(parentId: Long?, title: String, content: String): Long {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Title is required" }
        val max = if (parentId == null) dao.maxOrderRoot() else dao.maxOrder(parentId)
        val now = System.currentTimeMillis()
        return dao.insert(
            Node(
                parentId = parentId,
                type = NodeType.POST,
                title = trimmed,
                content = content,
                status = LearningStatus.READING,
                orderIndex = max + 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun savePost(id: Long, title: String, content: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Title is required" }
        val node = dao.getById(id) ?: return
        dao.update(
            node.copy(
                title = trimmed,
                content = content,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun touch(id: Long) {
        val node = dao.getById(id) ?: return
        if (node.type != NodeType.POST) return
        dao.update(node.copy(updatedAt = System.currentTimeMillis()))
    }
}
