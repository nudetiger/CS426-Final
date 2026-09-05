package com.cs426.learningmocha.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.Link
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.PostTag
import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow

/** Tag index row: the count comes from SQL, so the list never loads posts just to size them. */
data class TagWithCount(
    val id: Long,
    val name: String,
    val postCount: Int,
)

@Dao
interface KnowledgeDao {

    // --- Backup dump / restore (see backup/BackupRepository) ---

    @Query("SELECT * FROM links")
    suspend fun allLinks(): List<Link>

    @Query("SELECT * FROM post_tags")
    suspend fun allPostTags(): List<PostTag>

    @Query("SELECT * FROM dictionary")
    suspend fun allDictionary(): List<DictionaryEntry>

    @Query("SELECT * FROM resources")
    suspend fun allResources(): List<ResourceItem>

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    /**
     * Global terms (postId null) are the one knowledge row that does not hang off a node,
     * so wiping the library has to name them; everything else cascades from `nodes`.
     */
    @Query("DELETE FROM dictionary")
    suspend fun deleteAllDictionary()

    @Insert
    suspend fun insertLink(link: Link): Long

    @Query("DELETE FROM links WHERE fromPostId = :postId")
    suspend fun deleteOutgoing(postId: Long)

    @Query("SELECT COUNT(*) FROM links")
    suspend fun linkCount(): Int

    @Query(
        """
        SELECT n.* FROM links l
        JOIN nodes n ON n.id = l.fromPostId
        WHERE l.toPostId = :postId
        ORDER BY n.title
        """,
    )
    suspend fun backlinks(postId: Long): List<Node>

    @Query(
        """
        SELECT n.* FROM links l
        JOIN nodes n ON n.id = l.toPostId
        WHERE l.fromPostId = :postId
        ORDER BY n.title
        """,
    )
    suspend fun outgoingTargets(postId: Long): List<Node>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTag(name: String): Tag?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTag(id: Long): Tag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPostTag(postTag: PostTag)

    @Query("DELETE FROM post_tags WHERE postId = :postId")
    suspend fun deletePostTags(postId: Long)

    @Query(
        """
        SELECT t.* FROM tags t
        JOIN post_tags pt ON pt.tagId = t.id
        WHERE pt.postId = :postId
        ORDER BY t.name COLLATE NOCASE
        """,
    )
    suspend fun tagsForPost(postId: Long): List<Tag>

    @Query(
        """
        SELECT n.* FROM nodes n
        JOIN post_tags pt ON pt.postId = n.id
        WHERE pt.tagId = :tagId AND n.type = 'POST'
        ORDER BY n.title COLLATE NOCASE
        """,
    )
    fun observePostsWithTag(tagId: Long): Flow<List<Node>>

    @Query(
        """
        SELECT n.* FROM nodes n
        JOIN post_tags pt ON pt.postId = n.id
        WHERE pt.tagId = :tagId AND n.type = 'POST'
        ORDER BY n.title COLLATE NOCASE
        """,
    )
    suspend fun postsWithTag(tagId: Long): List<Node>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    suspend fun allTags(): List<Tag>

    /** Same membership rule as [postsWithTag] — only POST rows count — in one pass. */
    @Query(
        """
        SELECT t.id AS id, t.name AS name, COUNT(n.id) AS postCount FROM tags t
        LEFT JOIN post_tags pt ON pt.tagId = t.id
        LEFT JOIN nodes n ON n.id = pt.postId AND n.type = 'POST'
        GROUP BY t.id, t.name
        ORDER BY t.name COLLATE NOCASE
        """,
    )
    suspend fun tagCounts(): List<TagWithCount>

    /** A tag no post carries is dead weight: it opens an empty screen and reaches the AI. */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM post_tags)")
    suspend fun deleteOrphanTags()

    @Query("SELECT * FROM tags WHERE name LIKE :like ORDER BY name COLLATE NOCASE")
    suspend fun tagsNamedLike(like: String): List<Tag>

    @Insert
    suspend fun insertEntry(entry: DictionaryEntry): Long

    @Update
    suspend fun updateEntry(entry: DictionaryEntry)

    @Query("DELETE FROM dictionary WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    /** One term per scope: `postId = null` is the global glossary, otherwise the post's own. */
    @Query(
        """
        SELECT * FROM dictionary
        WHERE term = :term COLLATE NOCASE
          AND ((:postId IS NULL AND postId IS NULL) OR postId = :postId)
        LIMIT 1
        """,
    )
    suspend fun findEntry(postId: Long?, term: String): DictionaryEntry?

    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun dictionaryCount(): Int

    @Query(
        """
        SELECT * FROM dictionary
        WHERE postId = :postId OR postId IS NULL
        ORDER BY term COLLATE NOCASE
        """,
    )
    suspend fun dictionaryForReader(postId: Long): List<DictionaryEntry>

    @Query("SELECT * FROM dictionary ORDER BY term COLLATE NOCASE")
    fun observeDictionary(): Flow<List<DictionaryEntry>>

    @Query(
        """
        SELECT * FROM dictionary
        WHERE term LIKE :like OR definition LIKE :like OR meaningVi LIKE :like
        ORDER BY term COLLATE NOCASE
        """,
    )
    suspend fun searchDictionary(like: String): List<DictionaryEntry>

    @Insert
    suspend fun insertResource(item: ResourceItem): Long

    @Query("DELETE FROM resources WHERE id = :id")
    suspend fun deleteResource(id: Long)

    @Query("SELECT * FROM resources WHERE postId = :postId ORDER BY id")
    suspend fun resourcesForPost(postId: Long): List<ResourceItem>

    /** Search hit source: `resources.postId` is NOT NULL, so every row has an owning post. */
    @Query(
        """
        SELECT * FROM resources
        WHERE title LIKE :like OR url LIKE :like
        ORDER BY title COLLATE NOCASE
        LIMIT 20
        """,
    )
    suspend fun searchResources(like: String): List<ResourceItem>

    @Query(
        """
        SELECT nodes.* FROM posts_fts
        JOIN nodes ON nodes.rowid = posts_fts.rowid
        WHERE posts_fts MATCH :query
        LIMIT 50
        """,
    )
    suspend fun fts(query: String): List<Node>
}
