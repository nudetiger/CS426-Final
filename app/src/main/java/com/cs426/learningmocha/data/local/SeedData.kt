package com.cs426.learningmocha.data.local

import com.cs426.learningmocha.data.local.dao.NodeDao
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * First-launch sample tree so Browse / Home / Reader are never empty.
 *
 * Seeding is tied to the creation of the database file, not to the library being empty: the seed
 * text invites the reader to delete it, and re-growing it behind the user's back would be a bug.
 * [markFreshDatabase] is called from Room's `onCreate` callback (see [AppDatabase.build]) during
 * the very first query, which is the `count()` below.
 */
object SeedData {

    private val lock = Mutex()

    @Volatile
    private var freshDatabase = false

    @Volatile
    private var seedChecked = false

    @Volatile
    private var backfilled = false

    /** Room's one-shot database-created callback. */
    fun markFreshDatabase() {
        freshDatabase = true
    }

    suspend fun ensureSeeded(db: AppDatabase) {
        if (seedChecked && backfilled) return
        lock.withLock {
            val dao = db.nodeDao()
            if (!seedChecked) {
                // count() opens the database, so it must run before freshDatabase is read.
                val empty = dao.count() == 0
                seedChecked = true
                if (empty && freshDatabase) {
                    insertGettingStarted(db, dao)
                }
            }
            if (!backfilled) {
                backfilled = true
                backfill(db, dao)
            }
        }
    }

    private suspend fun insertGettingStarted(db: AppDatabase, dao: NodeDao) {
        val now = System.currentTimeMillis()
        val branchId = dao.insert(
            Node(
                type = NodeType.BRANCH,
                title = "Getting Started",
                orderIndex = 0,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val welcomeId = dao.insert(
            Node(
                parentId = branchId,
                type = NodeType.POST,
                title = "Welcome to Learning Mocha",
                content = WELCOME_MARKDOWN,
                status = LearningStatus.READING,
                favorite = true,
                orderIndex = 0,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val folderId = dao.insert(
            Node(
                parentId = branchId,
                type = NodeType.FOLDER,
                title = "How this library works",
                orderIndex = 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val treeId = dao.insert(
            Node(
                parentId = folderId,
                type = NodeType.POST,
                title = "Branches, folders, and posts",
                content = TREE_MARKDOWN,
                orderIndex = 0,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val mdId = dao.insert(
            Node(
                parentId = folderId,
                type = NodeType.POST,
                title = "Writing in Markdown",
                content = MARKDOWN_GUIDE,
                status = LearningStatus.IN_PROGRESS,
                orderIndex = 1,
                createdAt = now,
                updatedAt = now + 1,
            ),
        )
        KnowledgeSync.replaceTags(db, welcomeId, listOf("intro", "getting-started"))
        KnowledgeSync.replaceTags(db, treeId, listOf("intro", "tree"))
        KnowledgeSync.replaceTags(db, mdId, listOf("markdown", "writing"))
        KnowledgeSync.addTerm(
            db,
            welcomeId,
            "wiki-link",
            "A [[Title]] pointer to another post in this library.",
            "liên kết nội bộ tới bài khác",
        )
        KnowledgeSync.addTerm(
            db,
            null,
            "Markdown",
            "Plain-text format for headings, lists, links, and code.",
            "định dạng văn bản thuần",
        )
    }

    /** One-shot repair for libraries written before `links` existed (schema v1). */
    private suspend fun backfill(db: AppDatabase, dao: NodeDao) {
        if (db.knowledgeDao().linkCount() > 0) return
        dao.getPosts().forEach { post ->
            KnowledgeSync.reindex(db, post.id, post.content.orEmpty())
        }
    }

    private val WELCOME_MARKDOWN = """
        # Welcome to Learning Mocha

        This is your **personal Wikipedia** for things you are learning. Everything lives on this device — there is no account and no cloud copy of your notes.

        ## A quick tour

        1. **Home** — jump back into recent posts and open a branch.
        2. **Browse** — walk the tree, create items, drag to reorder, swipe to delete.
        3. Tap a post to **read** it, then **Edit** to change the markdown.

        The sample branch *Getting Started* is only a seed. Rename it, move it, or delete it whenever you like.

        ## What comes next

        Wiki-links such as [[Branches, folders, and posts]] jump to another article. Search looks through titles, article text, tags, references, and the dictionary. Star a post to keep it on Home.
    """.trimIndent()

    private val TREE_MARKDOWN = """
        # Branches, folders, and posts

        The library is a single tree.

        - A **branch** is a top-level subject (for example *Distributed Systems*).
        - A **folder** groups related posts inside a branch.
        - A **post** is an article written in Markdown.

        ## Organising

        In **Browse** you can:

        - Create a branch, folder, or post with the + button
        - Rename, move, or delete from the item menu
        - Drag a row to change its order among siblings

        Deleting a branch or folder also deletes everything inside it.

        Related reading: [[Writing in Markdown]].
    """.trimIndent()

    private val MARKDOWN_GUIDE = """
        # Writing in Markdown

        Posts are plain Markdown plus wiki-links and YouTube URLs.

        ## Common marks

        - **Bold** with double asterisks
        - *Italic* with single asterisks
        - `Inline code` with backticks
        - Wiki-links look like [[Welcome to Learning Mocha]]

        ## Lists and headings

        ### A nested idea

        1. Write the source in the **Edit** tab
        2. Switch to **Preview** to see the rendered article
        3. Save — the post is stored locally in Room

        ```
        fun hello() = "Learning Mocha"
        ```

        External links look like [example](https://example.com).

        A YouTube URL on its own line becomes a card under the article:

        https://www.youtube.com/watch?v=rfscVS0vtbw
    """.trimIndent()
}
