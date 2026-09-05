package com.cs426.learningmocha.ai.engine

import androidx.room.withTransaction
import com.cs426.learningmocha.ai.protocol.KbAction
import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.local.entity.DictionaryEntry
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.data.local.entity.ResourceType
import com.cs426.learningmocha.data.repo.PostRepository
import com.cs426.learningmocha.data.repo.TreeRepository
import com.cs426.learningmocha.util.PostMarkCatalog

/**
 * What one applied batch has to put back.
 *
 * `delete_post` is deliberately absent: a delete cascades to the post's links, tags, references
 * and glossary rows, and resurrecting that is out of scope — the review screen warns instead.
 */
data class UndoSnapshot(
    val createdIds: List<Long>,
    val restored: List<Node>,
    val restoredTags: Map<Long, List<String>>,
    val insertedResourceIds: List<Long> = emptyList(),
    val insertedTermIds: List<Long> = emptyList(),
    val restoredTerms: List<DictionaryEntry> = emptyList(),
    /** Prerequisite edges this batch added, as (post, requires) pairs. */
    val insertedPrerequisites: List<Pair<Long, Long>> = emptyList(),
    val removedPrerequisites: List<Pair<Long, Long>> = emptyList(),
)

class ActionExecutor(
    private val db: AppDatabase,
    private val tree: TreeRepository,
    private val posts: PostRepository,
) {
    suspend fun apply(actions: List<KbAction>): UndoSnapshot {
        val refs = HashMap<String, Long>()
        // What this batch itself created, keyed by the title it *asked* for. A later action that
        // names one by title has to reach the batch's own row, not a namesake that was already in
        // the library - and a duplicate post is stored under a numbered title, so its wanted name
        // would otherwise resolve to the wrong post entirely.
        val createdByTitle = HashMap<String, Long>()
        val created = ArrayList<Long>()
        val restored = ArrayList<Node>()
        val restoredTags = HashMap<Long, List<String>>()
        val insertedResources = ArrayList<Long>()
        val insertedTerms = ArrayList<Long>()
        val restoredTerms = ArrayList<DictionaryEntry>()
        val pendingNext = ArrayList<Pair<Long, KbAction>>()
        val pendingPrereqs = ArrayList<Pair<Long, KbAction>>()
        val insertedPrereqs = ArrayList<Pair<Long, Long>>()
        val removedPrereqs = ArrayList<Pair<Long, Long>>()
        db.withTransaction {
            for (action in actions) {
                when (action.op) {
                    "create_branch" -> {
                        val made = tree.createContainer(null, NodeType.BRANCH, action.title.orEmpty())
                        remember(action.ref, made.id, refs)
                        rememberTitle(action.title, made.id, createdByTitle)
                        // A reused container was already the user's; undo must not delete it.
                        if (made.isNew) created.add(made.id)
                    }
                    "create_folder" -> {
                        val made = tree.createContainer(
                            parentId(action, refs, createdByTitle),
                            NodeType.FOLDER,
                            action.title.orEmpty(),
                        )
                        remember(action.ref, made.id, refs)
                        rememberTitle(action.title, made.id, createdByTitle)
                        if (made.isNew) created.add(made.id)
                    }
                    "create_post" -> {
                        val id = posts.createPost(
                            parentId = parentId(action, refs, createdByTitle),
                            title = action.title.orEmpty(),
                            content = action.content.orEmpty(),
                            status = parseStatus(action.status) ?: LearningStatus.READING,
                            tagNames = action.tags.orEmpty(),
                            icon = PostMarkCatalog.icon(action.icon),
                            color = PostMarkCatalog.color(action.color),
                        )
                        remember(action.ref, id, refs)
                        rememberTitle(action.title, id, createdByTitle)
                        created.add(id)
                        queueNext(id, action, pendingNext)
                    }
                    "update_post" -> {
                        val node = requirePost(action, refs, createdByTitle)
                        snapshot(node, restored, restoredTags)
                        posts.updatePost(
                            id = node.id,
                            title = action.title,
                            content = action.content,
                            status = parseStatus(action.status),
                            // updatePost replaces the whole tag set, so a rewrite that names one
                            // tag would drop every other tag the user had. null still means "keep".
                            tagNames = action.tags?.let {
                                merged(posts.tagsForPost(node.id).map { tag -> tag.name }, it)
                            },
                        )
                        if (action.icon != null || action.color != null) {
                            posts.setMark(
                                node.id,
                                PostMarkCatalog.icon(action.icon) ?: node.icon,
                                PostMarkCatalog.color(action.color) ?: node.color,
                            )
                        }
                        queueNext(node.id, action, pendingNext)
                    }
                    "move_post" -> {
                        val node = requirePost(action, refs, createdByTitle)
                        snapshot(node, restored, restoredTags)
                        val wanted = action.newParentTitle.orEmpty()
                        val parent = createdByTitle[wanted.trim().lowercase()]
                            ?: tree.findByTitle(wanted)?.id
                        tree.move(node.id, parent)
                    }
                    "delete_post" -> {
                        // ponytail: undo does not resurrect deleted posts (cascade drops children).
                        val node = requirePost(action, refs, createdByTitle)
                        tree.delete(node.id)
                    }
                    "create_link" -> {
                        val from = requireFrom(action, refs, createdByTitle)
                        // addWikiLink appends `[[Target]]` to the body, so undo needs the content.
                        snapshot(from, restored, restoredTags)
                        posts.addWikiLink(from.id, action.toTitle.orEmpty())
                    }
                    "remove_link" -> {
                        val from = requireFrom(action, refs, createdByTitle)
                        snapshot(from, restored, restoredTags)
                        posts.removeWikiLink(from.id, action.toTitle.orEmpty())
                    }
                    "add_tag" -> {
                        val node = requirePost(action, refs, createdByTitle)
                        snapshot(node, restored, restoredTags)
                        posts.addTag(node.id, action.tag.orEmpty())
                    }
                    "remove_tag" -> {
                        val node = requirePost(action, refs, createdByTitle)
                        snapshot(node, restored, restoredTags)
                        posts.removeTag(node.id, action.tag.orEmpty())
                    }
                    "add_prerequisite" -> {
                        val node = requirePost(action, refs, createdByTitle)
                        // The required post may be created further down this same batch, so
                        // the edge waits until every create has an id — the same deferral
                        // nextRef needs, for the same reason.
                        pendingPrereqs.add(node.id to action)
                    }
                    "remove_prerequisite" -> {
                        val node = requirePost(action, refs, createdByTitle)
                        resolveRequires(action, refs, createdByTitle)?.let { requiredId ->
                            posts.removePrerequisite(node.id, requiredId)
                            removedPrereqs.add(node.id to requiredId)
                        }
                    }
                    "set_status" -> {
                        val node = requirePost(action, refs, createdByTitle)
                        snapshot(node, restored, restoredTags)
                        posts.setStatus(node.id, parseStatus(action.status) ?: node.status)
                    }
                    "set_favorite" -> {
                        val node = requirePost(action, refs, createdByTitle)
                        snapshot(node, restored, restoredTags)
                        posts.setFavorite(node.id, action.favorite == true)
                    }
                    "add_resource" -> {
                        val node = requirePost(action, refs, createdByTitle)
                        val type = try {
                            ResourceType.valueOf(action.type?.uppercase() ?: "OTHER")
                        } catch (_: Exception) {
                            ResourceType.OTHER
                        }
                        insertedResources.add(
                            posts.addResource(
                                node.id,
                                type,
                                action.title.orEmpty(),
                                action.url.orEmpty(),
                            ),
                        )
                    }
                    "add_dictionary_entry" -> {
                        val postId = if (action.postRef != null || !action.postTitle.isNullOrBlank()) {
                            requirePost(action, refs, createdByTitle).id
                        } else {
                            null
                        }
                        // addTerm upserts, so undo has to edit a term back rather than delete it.
                        val previous = posts.getTerm(postId, action.term.orEmpty())
                        val termId = posts.addTerm(
                            postId,
                            action.term.orEmpty(),
                            action.definition.orEmpty(),
                            action.meaningVi.orEmpty(),
                        )
                        if (previous == null) insertedTerms.add(termId) else restoredTerms.add(previous)
                    }
                }
            }
            for ((id, action) in pendingNext) {
                resolveNext(action, refs, createdByTitle)?.let { posts.setNext(id, it) }
            }
            for ((id, action) in pendingPrereqs) {
                val requiredId = resolveRequires(action, refs, createdByTitle) ?: continue
                // A loop is dropped rather than failing the batch. The rest of a good plan
                // should still land, and refusing the one impossible edge is exactly what the
                // editor's own picker does with a pick that would cycle.
                if (posts.addPrerequisite(id, requiredId)) {
                    insertedPrereqs.add(id to requiredId)
                }
            }
        }
        return UndoSnapshot(
            createdIds = created,
            restored = restored,
            restoredTags = restoredTags,
            insertedResourceIds = insertedResources,
            insertedTermIds = insertedTerms,
            restoredTerms = restoredTerms,
            insertedPrerequisites = insertedPrereqs,
            removedPrerequisites = removedPrereqs,
        )
    }

    suspend fun undo(snapshot: UndoSnapshot) {
        db.withTransaction {
            for (id in snapshot.createdIds.asReversed()) {
                tree.delete(id)
            }
            for (node in snapshot.restored) {
                posts.updatePost(
                    id = node.id,
                    title = node.title,
                    content = node.content,
                    status = node.status,
                    tagNames = snapshot.restoredTags[node.id],
                )
                posts.setFavorite(node.id, node.favorite)
                posts.setMark(node.id, node.icon, node.color)
                posts.setNext(node.id, node.nextPostId)
                if (tree.getNode(node.id)?.parentId != node.parentId) {
                    tree.move(node.id, node.parentId)
                }
            }
            for (id in snapshot.insertedResourceIds) {
                posts.removeResource(id)
            }
            for (id in snapshot.insertedTermIds) {
                posts.removeTerm(id)
            }
            for (entry in snapshot.restoredTerms) {
                posts.updateTerm(entry)
            }
            for ((postId, requiresId) in snapshot.insertedPrerequisites) {
                posts.removePrerequisite(postId, requiresId)
            }
            for ((postId, requiresId) in snapshot.removedPrerequisites) {
                posts.addPrerequisite(postId, requiresId)
            }
        }
    }

    private suspend fun snapshot(
        node: Node,
        restored: MutableList<Node>,
        restoredTags: MutableMap<Long, List<String>>,
    ) {
        if (restored.any { it.id == node.id }) return
        restored.add(node)
        restoredTags[node.id] = posts.tagsForPost(node.id).map { it.name }
    }

    /** Every lookup trims the ref (so does the validator), so `"p1 "` has to be stored trimmed. */
    private fun remember(ref: String?, id: Long, refs: MutableMap<String, Long>) {
        ref?.trim()?.takeIf { it.isNotEmpty() }?.let { refs[it] = id }
    }

    /** First writer wins, so two creates asking for one title do not steal each other's rows. */
    private fun rememberTitle(title: String?, id: Long, byTitle: MutableMap<String, Long>) {
        title?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { byTitle.putIfAbsent(it, id) }
    }

    /** Existing tags first, then the proposed ones the post does not already carry. */
    private fun merged(existing: List<String>, proposed: List<String>): List<String> {
        val seen = existing.mapTo(HashSet()) { it.lowercase() }
        return existing + proposed.map { it.trim() }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }

    private fun queueNext(id: Long, action: KbAction, pending: MutableList<Pair<Long, KbAction>>) {
        if (action.nextRef.isNullOrBlank() && action.nextTitle.isNullOrBlank()) return
        pending.add(id to action)
    }

    private suspend fun resolveNext(
        action: KbAction,
        refs: Map<String, Long>,
        createdByTitle: Map<String, Long>,
    ): Long? {
        action.nextRef?.trim()?.takeIf { it.isNotEmpty() }?.let { ref ->
            return refs[ref]
        }
        val title = action.nextTitle?.trim().orEmpty()
        if (title.isEmpty()) return null
        return createdByTitle[title.lowercase()] ?: posts.findPostByTitle(title)?.id
    }

    /** Same ref-or-title resolution as [resolveNext], for the prerequisite ops. */
    private suspend fun resolveRequires(
        action: KbAction,
        refs: Map<String, Long>,
        createdByTitle: Map<String, Long>,
    ): Long? {
        action.requiresRef?.trim()?.takeIf { it.isNotEmpty() }?.let { ref ->
            return refs[ref]
        }
        val title = action.requiresTitle?.trim().orEmpty()
        if (title.isEmpty()) return null
        return createdByTitle[title.lowercase()] ?: posts.findPostByTitle(title)?.id
    }

    /** Null means the library root. A post is a legal parent - that is a sub-post. */
    private suspend fun parentId(
        action: KbAction,
        refs: Map<String, Long>,
        createdByTitle: Map<String, Long>,
    ): Long? {
        action.parentRef?.trim()?.takeIf { it.isNotEmpty() }?.let { return refs.getValue(it) }
        val title = action.parentTitle?.trim().orEmpty()
        if (title.isEmpty()) return null
        return createdByTitle[title.lowercase()] ?: tree.findByTitle(title)?.id
    }

    private suspend fun requirePost(
        action: KbAction,
        refs: Map<String, Long>,
        createdByTitle: Map<String, Long>,
    ): Node {
        action.postRef?.trim()?.takeIf { it.isNotEmpty() }?.let { ref ->
            return tree.getNode(refs.getValue(ref)) ?: error("missing ref $ref")
        }
        val title = (action.postTitle ?: action.title).orEmpty()
        return resolveByTitle(title, createdByTitle) ?: error("missing post $title")
    }

    private suspend fun requireFrom(
        action: KbAction,
        refs: Map<String, Long>,
        createdByTitle: Map<String, Long>,
    ): Node {
        action.fromRef?.trim()?.takeIf { it.isNotEmpty() }?.let { ref ->
            return tree.getNode(refs.getValue(ref)) ?: error("missing ref $ref")
        }
        val title = action.fromTitle.orEmpty()
        return resolveByTitle(title, createdByTitle) ?: error("missing post $title")
    }

    /** The batch's own creation wins over a namesake that was already in the library. */
    private suspend fun resolveByTitle(title: String, createdByTitle: Map<String, Long>): Node? {
        createdByTitle[title.trim().lowercase()]?.let { id -> tree.getNode(id)?.let { return it } }
        return posts.findPostByTitle(title)
    }

    private fun parseStatus(raw: String?): LearningStatus? {
        if (raw.isNullOrBlank()) return null
        return LearningStatus.entries.find { it.name.equals(raw, ignoreCase = true) }
    }
}
