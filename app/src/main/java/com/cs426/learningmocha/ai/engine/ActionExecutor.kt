package com.cs426.learningmocha.ai.engine

import androidx.room.withTransaction
import com.cs426.learningmocha.ai.protocol.KbAction
import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.data.local.entity.ResourceType
import com.cs426.learningmocha.data.repo.PostRepository
import com.cs426.learningmocha.data.repo.TreeRepository

data class UndoSnapshot(
    val createdIds: List<Long>,
    val restored: List<Node>,
    val restoredTags: Map<Long, List<String>>,
)

class ActionExecutor(
    private val db: AppDatabase,
    private val tree: TreeRepository,
    private val posts: PostRepository,
) {
    suspend fun apply(actions: List<KbAction>): UndoSnapshot {
        val refs = HashMap<String, Long>()
        val created = ArrayList<Long>()
        val restored = ArrayList<Node>()
        val restoredTags = HashMap<Long, List<String>>()
        db.withTransaction {
            for (action in actions) {
                when (action.op) {
                    "create_branch" -> {
                        val id = tree.create(null, NodeType.BRANCH, action.title.orEmpty())
                        action.ref?.let { refs[it] = id }
                        created.add(id)
                    }
                    "create_folder" -> {
                        val id = tree.create(parentId(action, refs), NodeType.FOLDER, action.title.orEmpty())
                        action.ref?.let { refs[it] = id }
                        created.add(id)
                    }
                    "create_post" -> {
                        val id = posts.createPost(
                            parentId = parentId(action, refs),
                            title = action.title.orEmpty(),
                            content = action.content.orEmpty(),
                            status = parseStatus(action.status) ?: LearningStatus.READING,
                            tagNames = action.tags.orEmpty(),
                        )
                        action.ref?.let { refs[it] = id }
                        created.add(id)
                    }
                    "update_post" -> {
                        val node = requirePost(action, refs)
                        snapshot(node, restored, restoredTags)
                        posts.updatePost(
                            id = node.id,
                            title = action.title,
                            content = action.content,
                            status = parseStatus(action.status),
                            tagNames = action.tags,
                        )
                    }
                    "move_post" -> {
                        val node = requirePost(action, refs)
                        snapshot(node, restored, restoredTags)
                        val parent = tree.findByTitle(action.newParentTitle.orEmpty())
                        tree.move(node.id, parent?.id)
                    }
                    "delete_post" -> {
                        // ponytail: undo does not resurrect deleted posts (cascade drops children).
                        val node = requirePost(action, refs)
                        tree.delete(node.id)
                    }
                    "create_link" -> {
                        val from = requireFrom(action, refs)
                        posts.addWikiLink(from.id, action.toTitle.orEmpty())
                    }
                    "remove_link" -> {
                        val from = requireFrom(action, refs)
                        snapshot(from, restored, restoredTags)
                        posts.removeWikiLink(from.id, action.toTitle.orEmpty())
                    }
                    "add_tag" -> {
                        val node = requirePost(action, refs)
                        snapshot(node, restored, restoredTags)
                        posts.addTag(node.id, action.tag.orEmpty())
                    }
                    "remove_tag" -> {
                        val node = requirePost(action, refs)
                        snapshot(node, restored, restoredTags)
                        posts.removeTag(node.id, action.tag.orEmpty())
                    }
                    "set_status" -> {
                        val node = requirePost(action, refs)
                        snapshot(node, restored, restoredTags)
                        posts.setStatus(node.id, parseStatus(action.status) ?: node.status)
                    }
                    "set_favorite" -> {
                        val node = requirePost(action, refs)
                        snapshot(node, restored, restoredTags)
                        posts.setFavorite(node.id, action.favorite == true)
                    }
                    "add_resource" -> {
                        val node = requirePost(action, refs)
                        val type = try {
                            ResourceType.valueOf(action.type?.uppercase() ?: "OTHER")
                        } catch (_: Exception) {
                            ResourceType.OTHER
                        }
                        posts.addResource(node.id, type, action.title.orEmpty(), action.url.orEmpty())
                    }
                    "add_dictionary_entry" -> {
                        val postId = if (action.postRef != null || !action.postTitle.isNullOrBlank()) {
                            requirePost(action, refs).id
                        } else {
                            null
                        }
                        posts.addTerm(
                            postId,
                            action.term.orEmpty(),
                            action.definition.orEmpty(),
                            action.meaningVi.orEmpty(),
                        )
                    }
                }
            }
        }
        return UndoSnapshot(created, restored, restoredTags)
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
                if (tree.getNode(node.id)?.parentId != node.parentId) {
                    tree.move(node.id, node.parentId)
                }
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

    private suspend fun parentId(action: KbAction, refs: Map<String, Long>): Long? {
        action.parentRef?.trim()?.takeIf { it.isNotEmpty() }?.let { return refs.getValue(it) }
        val title = action.parentTitle?.trim().orEmpty()
        if (title.isEmpty()) return null
        return tree.findByTitle(title)?.id
    }

    private suspend fun requirePost(action: KbAction, refs: Map<String, Long>): Node {
        action.postRef?.trim()?.takeIf { it.isNotEmpty() }?.let { ref ->
            return tree.getNode(refs.getValue(ref)) ?: error("missing ref $ref")
        }
        val title = (action.postTitle ?: action.title).orEmpty()
        return posts.findPostByTitle(title) ?: error("missing post $title")
    }

    private suspend fun requireFrom(action: KbAction, refs: Map<String, Long>): Node {
        action.fromRef?.trim()?.takeIf { it.isNotEmpty() }?.let { ref ->
            return tree.getNode(refs.getValue(ref)) ?: error("missing ref $ref")
        }
        return posts.findPostByTitle(action.fromTitle.orEmpty())
            ?: error("missing post ${action.fromTitle}")
    }

    private fun parseStatus(raw: String?): LearningStatus? {
        if (raw.isNullOrBlank()) return null
        return LearningStatus.entries.find { it.name.equals(raw, ignoreCase = true) }
    }
}
