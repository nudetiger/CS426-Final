package com.cs426.learningmocha.backup

import androidx.room.withTransaction
import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.data.local.entity.PostTag
import com.cs426.learningmocha.util.ExportJsonWriter
import com.cs426.learningmocha.util.ImportJsonReader
import com.cs426.learningmocha.util.ImportTitles
import com.cs426.learningmocha.util.MarkdownLinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Export / import of the whole library as a single versioned JSON file.
 *
 * Restore always reassigns primary keys and rewrites every foreign key against the
 * new ids, so a merge cannot collide with rows the user already has, and a replace
 * cannot inherit stale ids. Titles are the other address space — `[[wiki-links]]` and AI
 * actions resolve by title — so a merge also renames incoming posts whose title is taken.
 * The FTS index needs no attention: `posts_fts` is content-backed by `nodes`, so Room's
 * triggers follow the writes below.
 */
class BackupRepository(private val db: AppDatabase) {

    private val nodes = db.nodeDao()
    private val knowledge = db.knowledgeDao()

    suspend fun snapshot(): BackupSnapshot = withContext(Dispatchers.IO) {
        BackupSnapshot(
            nodes = nodes.getAll(),
            links = knowledge.allLinks(),
            tags = knowledge.allTags(),
            postTags = knowledge.allPostTags(),
            dictionary = knowledge.allDictionary(),
            resources = knowledge.allResources(),
        )
    }

    suspend fun export(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val data = snapshot()
        out.bufferedWriter().use { ExportJsonWriter.write(it, data, System.currentTimeMillis()) }
        data.postCount
    }

    suspend fun peek(input: InputStream): BackupSnapshot = withContext(Dispatchers.IO) {
        input.bufferedReader().use { ImportJsonReader.read(it) }
    }

    /** @return number of posts written. Replace wipes the library first. */
    suspend fun restore(data: BackupSnapshot, replace: Boolean): Int {
        db.withTransaction {
            if (replace) {
                nodes.deleteAll()
                knowledge.deleteAllTags()
            }

            // A merge lands beside posts the user already has, so every colliding title is
            // renamed before the first insert: the body rewrite has to know the final titles,
            // or a link would point at a title that changes later in the same loop.
            val retitled = if (replace) Retitles.NONE else planTitles(data.nodes)

            val nodeIds = HashMap<Long, Long>()
            for (node in parentsFirst(data.nodes)) {
                val parent = node.parentId?.let { nodeIds[it] }
                nodeIds[node.id] = nodes.insert(
                    node.copy(
                        id = 0,
                        parentId = parent,
                        title = retitled.titleOf(node),
                        content = retitled.contentOf(node),
                        nextPostId = null,
                    ),
                )
            }
            for (node in data.nodes) {
                val next = node.nextPostId?.let { nodeIds[it] } ?: continue
                val newId = nodeIds[node.id] ?: continue
                val stored = nodes.getById(newId) ?: continue
                nodes.update(stored.copy(nextPostId = next))
            }

            val tagIds = HashMap<Long, Long>()
            for (tag in data.tags) {
                val existing = knowledge.findTag(tag.name)
                tagIds[tag.id] = existing?.id ?: knowledge.insertTag(tag.copy(id = 0))
            }

            for (link in data.links) {
                val from = nodeIds[link.fromPostId] ?: continue
                val to = nodeIds[link.toPostId] ?: continue
                knowledge.insertLink(link.copy(id = 0, fromPostId = from, toPostId = to))
            }

            for (pair in data.postTags) {
                val post = nodeIds[pair.postId] ?: continue
                val tag = tagIds[pair.tagId] ?: continue
                knowledge.insertPostTag(PostTag(post, tag))
            }

            for (entry in data.dictionary) {
                // A global term (postId null) stays global; a term whose post is missing is dropped.
                val oldPost = entry.postId
                if (oldPost != null && oldPost !in nodeIds) continue
                knowledge.insertEntry(entry.copy(id = 0, postId = oldPost?.let { nodeIds[it] }))
            }

            for (item in data.resources) {
                val post = nodeIds[item.postId] ?: continue
                knowledge.insertResource(item.copy(id = 0, postId = post))
            }
        }
        return data.postCount
    }

    /**
     * Wipes every row the app owns: the tree (links, tags, dictionary entries and resources
     * cascade off it), global dictionary terms, and the whole chat history. One transaction, so
     * an interrupted reset cannot leave half a library behind.
     *
     * This is the destructive half of Settings -> Backup -> Delete everything; the preference
     * half is [com.cs426.learningmocha.data.prefs.SettingsStore.clearAll].
     */
    suspend fun eraseEverything() {
        db.withTransaction {
            db.chatDao().deleteAllSessions()
            nodes.deleteAll()
            knowledge.deleteAllTags()
            knowledge.deleteAllDictionary()
        }
    }

    /**
     * The titles one merge import will actually use: the posts that had to be renamed, plus the
     * `[[old]] -> [[new]]` rewrites that keep the imported set pointing at its own copies instead
     * of at the user's originals.
     */
    private class Retitles(
        private val byNodeId: Map<Long, String>,
        private val rewrites: List<Pair<String, String>>,
    ) {
        fun titleOf(node: Node): String = byNodeId[node.id] ?: node.title

        /** Reuses the rename path's helper, so an import rewrites links just as a rename does. */
        fun contentOf(node: Node): String? {
            var text = node.content ?: return null
            for ((old, new) in rewrites) {
                text = MarkdownLinkParser.renameWikiLinks(text, old, new)
            }
            return text
        }

        companion object {
            /** Replace wipes the library first, so nothing it imports can collide. */
            val NONE = Retitles(emptyMap(), emptyList())
        }
    }

    private suspend fun planTitles(incoming: List<Node>): Retitles {
        val taken = nodes.postTitleIds().mapTo(HashSet<String>()) { ImportTitles.key(it.title) }
        val byNodeId = HashMap<Long, String>()
        val rewrites = ArrayList<Pair<String, String>>()
        for (node in incoming) {
            if (node.type != NodeType.POST) continue
            val title = ImportTitles.uniqueTitle(taken, node.title)
            // Claimed even when unchanged, so two incoming posts named alike still split.
            taken.add(ImportTitles.key(title))
            if (title == node.title) continue
            byNodeId[node.id] = title
            rewrites.add(node.title to title)
        }
        return Retitles(byNodeId, rewrites)
    }

    /**
     * Parents before children, so a child's remapped parentId is always known.
     * Nodes whose parent is missing from the backup are restored at the root
     * rather than dropped — losing a subtree silently would be worse.
     */
    private fun parentsFirst(all: List<Node>): List<Node> {
        val byId = all.associateBy { it.id }
        val ordered = ArrayList<Node>(all.size)
        val placed = HashSet<Long>()

        fun place(node: Node, guard: MutableSet<Long>) {
            if (node.id in placed || !guard.add(node.id)) return
            val parent = node.parentId?.let { byId[it] }
            if (parent != null) place(parent, guard)
            if (placed.add(node.id)) ordered.add(node)
        }

        for (node in all) place(node, HashSet())
        return ordered
    }
}
