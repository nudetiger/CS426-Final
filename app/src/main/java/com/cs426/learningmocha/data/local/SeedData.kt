package com.cs426.learningmocha.data.local

import com.cs426.learningmocha.data.local.dao.NodeDao
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** First-launch sample tree so Browse / Home / Reader are never empty. */
object SeedData {

    private val lock = Mutex()

    suspend fun ensureSeeded(dao: NodeDao) {
        lock.withLock {
            if (dao.count() == 0) {
                insertGettingStarted(dao)
            }
        }
    }

    private suspend fun insertGettingStarted(dao: NodeDao) {
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
        dao.insert(
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
        dao.insert(
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
        dao.insert(
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

        Later updates add wiki-links such as [[Branches, folders, and posts]], tags, a dictionary, search, and an AI assistant that can only write after you review the change.
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
    """.trimIndent()

    private val MARKDOWN_GUIDE = """
        # Writing in Markdown

        Posts are plain Markdown plus an optional wiki-link syntax.

        ## Common marks

        - **Bold** with double asterisks
        - *Italic* with single asterisks
        - `Inline code` with backticks

        ## Lists and headings

        ### A nested idea

        1. Write the source in the **Edit** tab
        2. Switch to **Preview** to see the rendered article
        3. Save — the post is stored locally in Room

        ```
        fun hello() = "Learning Mocha"
        ```

        External links look like [example](https://example.com). Wiki-links look like [[Writing in Markdown]] and become tappable in a later phase.
    """.trimIndent()
}