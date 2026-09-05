package com.cs426.learningmocha.ai.protocol

import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.util.TreeRules

/**
 * Gate between the model's JSON and Room: every batch is checked here before the review screen
 * shows it, so a malformed or destructive plan never reaches [com.cs426.learningmocha.ai.engine.ActionExecutor].
 *
 * The op set and the caps below are documented to the model in `backend/prompts.js`
 * (ACTION_PROTOCOL) — change one side and the other has to follow.
 */
object ActionValidator {
    const val MAX_ACTIONS = 40
    const val MAX_TITLE = 200
    const val MAX_CONTENT = 50_000
    const val MAX_TERM = 80
    const val MAX_TAGS = 20

    private val knownOps = setOf(
        "create_branch", "create_folder", "create_post", "update_post",
        "move_post", "delete_post", "create_link", "remove_link",
        "add_tag", "remove_tag", "set_status", "set_favorite",
        "add_resource", "add_dictionary_entry",
        "add_prerequisite", "remove_prerequisite",
    )

    /**
     * Whether the protocol has an operation by this name. The review screen asks before it
     * ticks a row: a model that invents an op should cost the user that one change, not the
     * whole batch.
     */
    fun isKnownOp(op: String?): Boolean = op.orEmpty().trim() in knownOps

    /**
     * @param enabled when given, only the checked actions are validated — the "Action N" numbers
     *   then still match the rows on screen, because indices come from the full list either way
     */
    fun validate(
        actions: List<KbAction>,
        nodes: List<Node>,
        enabled: BooleanArray? = null,
    ): List<String> {
        val errors = ArrayList<String>()
        if (actions.isEmpty()) {
            errors.add("No actions to apply")
            return errors
        }
        if (actions.size > MAX_ACTIONS) {
            errors.add("Too many actions (max $MAX_ACTIONS)")
        }

        val byTitle = HashMap<String, Node>()
        nodes.forEach { byTitle.putIfAbsent(it.title.lowercase(), it) }
        val parentById = HashMap<Long, Long>()
        nodes.forEach { node -> node.parentId?.let { parentById[node.id] = it } }

        val scope = Scope(byTitle)

        for ((index, action) in actions.withIndex()) {
            if (enabled != null && !enabled.getOrElse(index) { false }) continue
            val n = index + 1
            val op = action.op.orEmpty()
            if (op !in knownOps) {
                errors.add("Action $n: unknown op \"$op\"")
                continue
            }
            when (op) {
                "create_branch", "create_folder", "create_post" -> {
                    checkTitle(action.title, n, errors)
                    checkRef(action.ref, n, scope.refs, errors)
                    val key = action.title?.trim()?.lowercase().orEmpty()
                    // A taken title is no longer an error: the executor numbers a duplicate post
                    // ("Raft (2)") and reuses a container that is already where it asked for one.
                    // Failing here instead cost the user every later action in the batch, which is
                    // exactly what made a whole-library reorganize unusable.
                    if (op != "create_branch") {
                        checkParent(action, n, scope, errors)
                    }
                    // Registered after the parent check so an action cannot become its own parent.
                    if (key.isNotEmpty()) {
                        if (op == "create_post") scope.newPosts.add(key) else scope.newContainers.add(key)
                    }
                    if (op == "create_post") {
                        action.ref?.trim()?.takeIf { it.isNotEmpty() }?.let { scope.postRefs.add(it) }
                        checkContent(action.content, n, errors, required = false)
                        checkTags(action.tags, n, errors)
                        checkStatus(action.status, n, errors, required = false)
                    }
                }
                "update_post" -> {
                    val post = resolvePost(action, n, scope, errors)
                    checkContent(action.content, n, errors, required = false)
                    checkTags(action.tags, n, errors)
                    if (action.title != null) {
                        checkTitle(action.title, n, errors)
                        // Titles address posts, so a retitle onto a taken one has to fail here
                        // rather than throw halfway through the executor's transaction.
                        val key = action.title.trim().lowercase()
                        val existing = scope.byTitle[key]
                        val taken = key.isNotEmpty() &&
                            (
                                key in scope.newPosts ||
                                    key in scope.newContainers ||
                                    (existing != null && existing.id != post?.id)
                                )
                        if (taken) {
                            errors.add("Action $n: title \"${action.title}\" already exists")
                        }
                    }
                }
                "move_post" -> {
                    val post = resolvePost(action, n, scope, errors)
                    // Any node can be a parent, a post included — that is a sub-post. Only a
                    // cycle is still refused, since it would strand the subtree.
                    val parent = resolveExistingTitle(action.newParentTitle, n, "newParentTitle", scope, errors)
                    if (post != null && parent != null) {
                        if (TreeRules.wouldCreateCycle(post.id, parent.id, parentById)) {
                            errors.add("Action $n: move would create a cycle")
                        }
                    }
                }
                "delete_post" -> resolvePost(action, n, scope, errors)
                "create_link" -> {
                    resolveFrom(action, n, scope, errors)
                    if (action.toTitle.isNullOrBlank()) {
                        errors.add("Action $n: toTitle is required")
                    }
                }
                "remove_link" -> {
                    resolveExistingTitle(action.fromTitle, n, "fromTitle", scope, errors)
                    if (action.toTitle.isNullOrBlank()) {
                        errors.add("Action $n: toTitle is required")
                    }
                }
                "add_tag", "remove_tag" -> {
                    resolvePost(action, n, scope, errors)
                    if (action.tag.isNullOrBlank()) errors.add("Action $n: tag is required")
                }
                "add_prerequisite", "remove_prerequisite" -> {
                    resolvePost(action, n, scope, errors)
                    checkRequires(action, n, scope, errors)
                }
                "set_status" -> {
                    resolvePost(action, n, scope, errors)
                    checkStatus(action.status, n, errors, required = true)
                }
                "set_favorite" -> {
                    resolvePost(action, n, scope, errors)
                    if (action.favorite == null) errors.add("Action $n: favorite is required")
                }
                "add_resource" -> {
                    resolvePost(action, n, scope, errors)
                    if (action.url.isNullOrBlank()) errors.add("Action $n: url is required")
                    val kind = action.type?.uppercase()
                    if (kind != null && kind !in setOf("YOUTUBE", "ARTICLE", "BOOK", "OTHER")) {
                        errors.add("Action $n: unknown resource type")
                    }
                }
                "add_dictionary_entry" -> {
                    if (!action.postTitle.isNullOrBlank() || !action.postRef.isNullOrBlank()) {
                        resolvePost(action, n, scope, errors)
                    }
                    val term = action.term?.trim().orEmpty()
                    if (term.isEmpty()) errors.add("Action $n: term is required")
                    if (term.length > MAX_TERM) errors.add("Action $n: term is too long")
                    if (action.definition.isNullOrBlank()) errors.add("Action $n: definition is required")
                }
            }
        }
        return errors
    }

    /** Live nodes plus the items earlier actions in the same batch will have created. */
    private class Scope(val byTitle: Map<String, Node>) {
        val refs = HashSet<String>()

        /** Refs of `create_post` actions only — a postRef naming a container is not a post. */
        val postRefs = HashSet<String>()
        val newPosts = HashSet<String>()
        val newContainers = HashSet<String>()

        fun knows(key: String) = byTitle.containsKey(key) || key in newPosts || key in newContainers
    }

    private fun checkTitle(title: String?, n: Int, errors: MutableList<String>) {
        val trimmed = title?.trim().orEmpty()
        if (trimmed.isEmpty()) errors.add("Action $n: title is required")
        if (trimmed.length > MAX_TITLE) errors.add("Action $n: title is too long")
    }

    private fun checkRef(ref: String?, n: Int, refs: MutableSet<String>, errors: MutableList<String>) {
        val id = ref?.trim().orEmpty()
        if (id.isEmpty()) return
        if (!refs.add(id)) errors.add("Action $n: duplicate ref \"$id\"")
    }

    private fun checkParent(
        action: KbAction,
        n: Int,
        scope: Scope,
        errors: MutableList<String>,
    ) {
        val parentRef = action.parentRef?.trim().orEmpty()
        if (parentRef.isNotEmpty()) {
            if (parentRef !in scope.refs) errors.add("Action $n: unknown parentRef \"$parentRef\"")
            return
        }
        val parentTitle = action.parentTitle?.trim().orEmpty()
        if (parentTitle.isEmpty()) return
        val key = parentTitle.lowercase()
        // Branches, folders and posts can all hold children; a post under a post is a sub-post.
        // The parent only has to exist, either already or as an earlier action in this batch.
        if (scope.byTitle[key] == null && key !in scope.newContainers && key !in scope.newPosts) {
            errors.add("Action $n: parent \"${action.parentTitle}\" does not exist")
        }
    }

    /**
     * The other end of a prerequisite edge. Stricter than [create_link]'s `toTitle`, which is
     * allowed to name a post that does not exist yet: a wiki-link to nothing renders as plain
     * text, but a prerequisite pointing at nothing would sit in a readiness bar as a
     * requirement that can never be met.
     */
    private fun checkRequires(
        action: KbAction,
        n: Int,
        scope: Scope,
        errors: MutableList<String>,
    ) {
        val ref = action.requiresRef?.trim().orEmpty()
        if (ref.isNotEmpty()) {
            when {
                ref !in scope.refs -> errors.add("Action $n: unknown requiresRef \"$ref\"")
                ref !in scope.postRefs -> errors.add("Action $n: \"$ref\" is not a post")
            }
            if (ref == action.postRef?.trim()) {
                errors.add("Action $n: a post cannot require itself")
            }
            return
        }
        val title = action.requiresTitle?.trim().orEmpty()
        if (title.isEmpty()) {
            errors.add("Action $n: requiresTitle is required")
            return
        }
        val key = title.lowercase()
        val existing = scope.byTitle[key]
        when {
            existing != null && existing.type != NodeType.POST ->
                errors.add("Action $n: \"$title\" is not a post")
            existing == null && key !in scope.newPosts ->
                errors.add("Action $n: post \"$title\" does not exist")
        }
        val own = (action.postTitle ?: action.title)?.trim()?.lowercase()
        if (own != null && own == key) {
            errors.add("Action $n: a post cannot require itself")
        }
    }

    private fun checkContent(content: String?, n: Int, errors: MutableList<String>, required: Boolean) {
        if (required && content.isNullOrBlank()) errors.add("Action $n: content is required")
        if ((content?.length ?: 0) > MAX_CONTENT) errors.add("Action $n: content is too long")
    }

    private fun checkTags(tags: List<String>?, n: Int, errors: MutableList<String>) {
        if (tags != null && tags.size > MAX_TAGS) errors.add("Action $n: too many tags")
    }

    private fun checkStatus(status: String?, n: Int, errors: MutableList<String>, required: Boolean) {
        if (status.isNullOrBlank()) {
            if (required) errors.add("Action $n: status is required")
            return
        }
        val ok = LearningStatus.entries.any { it.name.equals(status, ignoreCase = true) }
        if (!ok) errors.add("Action $n: unknown status \"$status\"")
    }

    private fun resolvePost(
        action: KbAction,
        n: Int,
        scope: Scope,
        errors: MutableList<String>,
    ): Node? {
        val postRef = action.postRef?.trim().orEmpty()
        if (postRef.isNotEmpty()) {
            when (postRef) {
                !in scope.refs -> errors.add("Action $n: unknown postRef \"$postRef\"")
                !in scope.postRefs -> errors.add("Action $n: \"$postRef\" is not a post")
            }
            return null
        }
        val title = (action.postTitle ?: action.title)?.trim().orEmpty()
        if (title.isEmpty()) {
            errors.add("Action $n: postTitle is required")
            return null
        }
        val key = title.lowercase()
        val node = scope.byTitle[key]
        if (node == null) {
            when (key) {
                in scope.newPosts -> Unit
                in scope.newContainers -> errors.add("Action $n: \"$title\" is not a post")
                else -> errors.add("Action $n: post \"$title\" does not exist")
            }
            return null
        }
        if (node.type != NodeType.POST) {
            errors.add("Action $n: \"$title\" is not a post")
            return null
        }
        return node
    }

    private fun resolveFrom(
        action: KbAction,
        n: Int,
        scope: Scope,
        errors: MutableList<String>,
    ) {
        val fromRef = action.fromRef?.trim().orEmpty()
        if (fromRef.isNotEmpty()) {
            // A link is written into the source post's markdown, so a ref naming a branch or
            // folder would append body text to a container that never renders it.
            when {
                fromRef !in scope.refs -> errors.add("Action $n: unknown fromRef \"$fromRef\"")
                fromRef !in scope.postRefs -> errors.add("Action $n: \"$fromRef\" is not a post")
            }
            return
        }
        resolveExistingTitle(action.fromTitle, n, "fromTitle", scope, errors)
    }

    private fun resolveExistingTitle(
        title: String?,
        n: Int,
        field: String,
        scope: Scope,
        errors: MutableList<String>,
    ): Node? {
        val key = title?.trim().orEmpty()
        if (key.isEmpty()) {
            errors.add("Action $n: $field is required")
            return null
        }
        val node = scope.byTitle[key.lowercase()]
        if (node == null && !scope.knows(key.lowercase())) {
            errors.add("Action $n: \"$title\" does not exist")
        }
        return node
    }
}

object ActionLabels {
    fun describe(action: KbAction): String {
        val title = action.title ?: action.postTitle ?: action.term ?: ""
        return when (action.op) {
            "create_branch" -> "Create branch $title"
            "create_folder" -> "Create folder $title"
            "create_post" -> "Create post $title"
            "update_post" -> "Update post $title"
            "move_post" -> "Move post $title"
            "delete_post" -> "Delete post $title"
            "create_link" -> "Link ${action.fromTitle ?: action.fromRef} → ${action.toTitle}"
            "remove_link" -> "Remove link ${action.fromTitle} → ${action.toTitle}"
            "add_tag" -> "Tag $title with ${action.tag}"
            "remove_tag" -> "Remove tag ${action.tag} from $title"
            "set_status" -> "Set status of $title to ${action.status}"
            "set_favorite" -> if (action.favorite == true) "Star $title" else "Unstar $title"
            "add_resource" -> "Add ${action.type ?: "resource"} to $title"
            "add_dictionary_entry" -> "Add term ${action.term}"
            "add_prerequisite" ->
                "$title requires ${action.requiresTitle ?: action.requiresRef}"
            "remove_prerequisite" ->
                "$title no longer requires ${action.requiresTitle ?: action.requiresRef}"
            else -> action.op.orEmpty()
        }
    }

    /**
     * Depth of an action inside its own batch, for the tree preview on the review screen.
     * The model wires parents with `parentRef` or `parentTitle`, so both are followed; a parent
     * that is not part of the batch is an existing node, which anchors the walk at depth 0.
     */
    fun indent(action: KbAction, all: List<KbAction>): Int {
        val byRef = all.filter { !it.ref.isNullOrBlank() }.associateBy { it.ref!!.trim() }
        val byTitle = all
            .filter { it.op?.startsWith("create_") == true && !it.title.isNullOrBlank() }
            .associateBy { it.title!!.trim().lowercase() }
        var depth = 0
        var cursor = action
        val seen = HashSet<String>()
        while (true) {
            val ref = cursor.parentRef?.trim()
            val title = cursor.parentTitle?.trim()?.lowercase()
            val token = when {
                !ref.isNullOrEmpty() -> "ref:$ref"
                !title.isNullOrEmpty() -> "title:$title"
                else -> return depth
            }
            if (!seen.add(token)) return depth
            val parent = when {
                !ref.isNullOrEmpty() -> byRef[ref]
                else -> byTitle[title.orEmpty()]
            } ?: return depth
            depth++
            cursor = parent
        }
    }
}
