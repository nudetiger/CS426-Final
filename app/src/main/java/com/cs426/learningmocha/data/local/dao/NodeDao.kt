package com.cs426.learningmocha.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cs426.learningmocha.data.local.entity.Node
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {

    @Query("SELECT * FROM nodes WHERE parentId IS NULL ORDER BY orderIndex ASC, title ASC")
    fun observeRoots(): Flow<List<Node>>

    @Query("SELECT * FROM nodes WHERE parentId = :parentId ORDER BY orderIndex ASC, title ASC")
    fun observeChildren(parentId: Long): Flow<List<Node>>

    @Query("SELECT * FROM nodes WHERE type = 'BRANCH' AND parentId IS NULL ORDER BY orderIndex ASC, title ASC")
    fun observeRootBranches(): Flow<List<Node>>

    @Query("SELECT * FROM nodes WHERE type = 'POST' ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecentPosts(limit: Int): Flow<List<Node>>

    @Query(
        """
        SELECT * FROM nodes
        WHERE type = 'POST' AND status IN ('READING', 'IN_PROGRESS')
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeContinueReading(limit: Int): Flow<List<Node>>

    @Query("SELECT * FROM nodes WHERE id = :id")
    fun observeById(id: Long): Flow<Node?>

    @Query("SELECT COUNT(*) FROM nodes")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM nodes WHERE type = 'POST'")
    fun observePostCount(): Flow<Int>

    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun getById(id: Long): Node?

    @Query("SELECT * FROM nodes")
    suspend fun getAll(): List<Node>

    @Query("SELECT * FROM nodes WHERE type IN ('BRANCH', 'FOLDER') ORDER BY title ASC")
    suspend fun getContainers(): List<Node>

    @Query("SELECT * FROM nodes WHERE type = 'POST' AND favorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<Node>>

    @Query("SELECT * FROM nodes WHERE title = :title COLLATE NOCASE LIMIT 1")
    suspend fun findByTitle(title: String): Node?

    @Query("SELECT * FROM nodes WHERE type = 'POST' AND title = :title COLLATE NOCASE LIMIT 1")
    suspend fun findPostByTitle(title: String): Node?

    @Query("SELECT * FROM nodes WHERE parentId IS NULL ORDER BY orderIndex ASC, title ASC")
    suspend fun getRoots(): List<Node>

    @Query("SELECT * FROM nodes WHERE parentId = :parentId ORDER BY orderIndex ASC, title ASC")
    suspend fun getChildren(parentId: Long): List<Node>

    @Query("SELECT * FROM nodes WHERE type = 'POST' ORDER BY title COLLATE NOCASE")
    suspend fun getPosts(): List<Node>

    @Query("SELECT * FROM nodes WHERE type IN ('POST', 'BRANCH') AND title LIKE :like ORDER BY title COLLATE NOCASE LIMIT 30")
    suspend fun titlesLike(like: String): List<Node>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM nodes WHERE parentId IS NULL")
    suspend fun maxOrderRoot(): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM nodes WHERE parentId = :parentId")
    suspend fun maxOrder(parentId: Long): Int

    @Insert
    suspend fun insert(node: Node): Long

    @Update
    suspend fun update(node: Node)

    @Update
    suspend fun updateAll(nodes: List<Node>)

    @Query("DELETE FROM nodes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Replace-mode restore. Cascades to links, post_tags, dictionary and resources. */
    @Query("DELETE FROM nodes")
    suspend fun deleteAll()
}
