package com.cs426.learningmocha.ai.protocol

import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionValidatorTest {

    private fun post(id: Long, title: String, parentId: Long? = 1L) = Node(
        id = id,
        parentId = parentId,
        type = NodeType.POST,
        title = title,
    )

    private fun branch(id: Long, title: String) = Node(
        id = id,
        type = NodeType.BRANCH,
        title = title,
    )

    @Test
    fun laterActionCanUseEarlierRef() {
        val actions = listOf(
            KbAction(op = "create_branch", title = "Distributed Systems", ref = "b1"),
            KbAction(op = "create_post", parentRef = "b1", title = "Raft", content = "# Raft"),
        )
        assertTrue(ActionValidator.validate(actions, emptyList()).isEmpty())
    }

    @Test
    fun earlierActionCannotUseLaterRef() {
        val actions = listOf(
            KbAction(op = "create_post", parentRef = "b1", title = "Raft", content = "# Raft"),
            KbAction(op = "create_branch", title = "Distributed Systems", ref = "b1"),
        )
        assertFalse(ActionValidator.validate(actions, emptyList()).isEmpty())
    }

    // Creating a post whose title the library already holds is allowed: the executor stores it
    // as "Raft (2)". Only a deliberate retitle still refuses a taken name.
    @Test
    fun acceptsCreatingAPostWhoseTitleIsTaken() {
        val actions = listOf(KbAction(op = "create_post", title = "Raft", content = "x"))
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, listOf(post(2, "Raft"))))
    }

    @Test
    fun rejectsUnknownOpAndEmptyBatch() {
        assertTrue(ActionValidator.validate(emptyList(), emptyList()).isNotEmpty())
        val errors = ActionValidator.validate(
            listOf(KbAction(op = "explode", title = "x")),
            emptyList(),
        )
        assertTrue(errors.any { it.contains("unknown op") })
    }

    @Test
    fun rejectsMoveOntoSelf() {
        val nodes = listOf(branch(1, "A"), post(3, "Raft", parentId = 1))
        val actions = listOf(
            KbAction(op = "move_post", postTitle = "Raft", newParentTitle = "Raft"),
        )
        assertTrue(ActionValidator.validate(actions, nodes).any { it.contains("cycle") })
    }

    @Test
    fun rejectsMissingParent() {
        val actions = listOf(
            KbAction(op = "create_post", parentTitle = "No Such Branch", title = "Raft"),
        )
        assertTrue(ActionValidator.validate(actions, emptyList()).any { it.contains("does not exist") })
    }

    // The backend prompt tells the model to address posts by title, so a learning-path batch
    // routinely creates a post and then tags/annotates it in the same batch.
    @Test
    fun acceptsPostCreatedEarlierInBatchByTitle() {
        val actions = listOf(
            KbAction(op = "create_branch", title = "Distributed Systems", ref = "b1"),
            KbAction(op = "create_post", parentRef = "b1", title = "Raft", content = "# Raft"),
            KbAction(op = "add_tag", postTitle = "Raft", tag = "consensus"),
            KbAction(op = "set_status", postTitle = "Raft", status = "READING"),
            KbAction(op = "set_favorite", postTitle = "Raft", favorite = true),
            KbAction(
                op = "add_dictionary_entry",
                postTitle = "Raft",
                term = "quorum",
                definition = "A majority of nodes",
            ),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, emptyList()))
    }

    @Test
    fun acceptsContainerCreatedEarlierInBatchByTitle() {
        val actions = listOf(
            KbAction(op = "create_branch", title = "Distributed Systems", ref = "b1"),
            KbAction(op = "create_post", parentTitle = "Distributed Systems", title = "Raft"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, emptyList()))
    }

    @Test
    fun stillRejectsTitleNeverCreated() {
        val actions = listOf(
            KbAction(op = "create_post", title = "Raft", content = "# Raft"),
            KbAction(op = "add_tag", postTitle = "Paxos", tag = "consensus"),
        )
        assertTrue(
            ActionValidator.validate(actions, emptyList())
                .any { it.contains("Paxos") && it.contains("does not exist") },
        )
    }

    @Test
    fun rejectsTaggingABranchCreatedInBatch() {
        val actions = listOf(
            KbAction(op = "create_branch", title = "Distributed Systems", ref = "b1"),
            KbAction(op = "add_tag", postTitle = "Distributed Systems", tag = "x"),
        )
        assertTrue(
            ActionValidator.validate(actions, emptyList()).any { it.contains("is not a post") },
        )
    }

    // A post under a post is a sub-post, which the library supports; the parent only has to
    // exist, and one created earlier in the same batch counts.
    @Test
    fun acceptsBatchCreatedPostUsedAsParent() {
        val actions = listOf(
            KbAction(op = "create_post", title = "Raft", content = "# Raft"),
            KbAction(op = "create_post", parentTitle = "Raft", title = "Raft Election"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, emptyList()))
    }

    @Test
    fun rejectsParentThatIsNowhereInTheLibraryOrTheBatch() {
        val actions = listOf(
            KbAction(op = "create_post", parentTitle = "Nothing Here", title = "Raft"),
        )
        assertTrue(
            ActionValidator.validate(actions, emptyList())
                .any { it.contains("does not exist") },
        )
    }

    // "Loop" is not in the library and nothing earlier in the batch creates it, so the parent
    // reference dangles — an action can still never be its own parent.
    @Test
    fun rejectsActionThatIsItsOwnParent() {
        val actions = listOf(
            KbAction(op = "create_folder", parentTitle = "Loop", title = "Loop"),
        )
        assertTrue(ActionValidator.validate(actions, emptyList()).isNotEmpty())
    }

    // The executor numbers a duplicate post ("Raft (2)") and reuses a container that is
    // already in place, so a taken title no longer costs the batch every action after it.
    @Test
    fun acceptsDuplicateTitleWithinBatch() {
        val actions = listOf(
            KbAction(op = "create_post", title = "Raft", content = "a"),
            KbAction(op = "create_post", title = "raft", content = "b"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, emptyList()))
    }

    @Test
    fun acceptsCreatingAFolderThatAlreadyExists() {
        val nodes = listOf(branch(1, "Root"))
        val actions = listOf(
            KbAction(op = "create_folder", parentTitle = "Root", title = "Root"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, nodes))
    }

    @Test
    fun rejectsDuplicateRef() {
        val actions = listOf(
            KbAction(op = "create_branch", title = "A", ref = "b1"),
            KbAction(op = "create_branch", title = "B", ref = "b1"),
        )
        assertTrue(
            ActionValidator.validate(actions, emptyList()).any { it.contains("duplicate ref") },
        )
    }

    // Refs of every kind share one namespace, so a post-only field could otherwise be applied
    // to a branch or folder created in the same batch.
    @Test
    fun rejectsPostRefThatNamesAContainer() {
        val branchRef = listOf(
            KbAction(op = "create_branch", title = "Distributed Systems", ref = "b1"),
            KbAction(op = "set_status", postRef = "b1", status = "READING"),
        )
        assertTrue(
            ActionValidator.validate(branchRef, emptyList())
                .any { it.contains("\"b1\" is not a post") },
        )

        val folderRef = listOf(
            KbAction(op = "create_folder", title = "Consensus", ref = "f1"),
            KbAction(op = "add_tag", postRef = "f1", tag = "consensus"),
        )
        assertTrue(
            ActionValidator.validate(folderRef, emptyList())
                .any { it.contains("\"f1\" is not a post") },
        )
    }

    @Test
    fun acceptsPostRefThatNamesAPostCreatedInBatch() {
        val actions = listOf(
            KbAction(op = "create_post", title = "Raft", content = "# Raft", ref = "p1"),
            KbAction(op = "add_tag", postRef = "p1", tag = "consensus"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, emptyList()))
    }

    // The executor stores and reads refs trimmed, so padding must not fail here either.
    @Test
    fun acceptsPaddedPostRef() {
        val actions = listOf(
            KbAction(op = "create_post", title = "Raft", content = "# Raft", ref = "p1"),
            KbAction(op = "set_status", postRef = " p1 ", status = "FINISHED"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, emptyList()))
    }

    @Test
    fun knowsOnlyProtocolOps() {
        assertTrue(ActionValidator.isKnownOp("create_post"))
        assertTrue(ActionValidator.isKnownOp("  set_status  "))
        assertFalse(ActionValidator.isKnownOp("summon_daemon"))
        assertFalse(ActionValidator.isKnownOp(null))
    }

    @Test
    fun rejectsOversizedBatchAndLongTitle() {
        val many = List(ActionValidator.MAX_ACTIONS + 1) {
            KbAction(op = "create_post", title = "Post $it")
        }
        assertTrue(ActionValidator.validate(many, emptyList()).any { it.contains("Too many") })

        val long = listOf(KbAction(op = "create_post", title = "x".repeat(ActionValidator.MAX_TITLE + 1)))
        assertTrue(ActionValidator.validate(long, emptyList()).any { it.contains("too long") })
    }

    @Test
    fun rejectsUnknownStatusAndResourceType() {
        val nodes = listOf(branch(1, "A"), post(3, "Raft"))
        val bad = listOf(KbAction(op = "set_status", postTitle = "Raft", status = "SLEEPING"))
        assertTrue(ActionValidator.validate(bad, nodes).any { it.contains("unknown status") })

        val resource = listOf(
            KbAction(op = "add_resource", postTitle = "Raft", type = "PODCAST", url = "http://x"),
        )
        assertTrue(
            ActionValidator.validate(resource, nodes).any { it.contains("unknown resource type") },
        )
    }

    @Test
    fun rejectsMoveIntoOwnDescendant() {
        val nodes = listOf(
            branch(1, "Root"),
            Node(id = 2, parentId = 1, type = NodeType.FOLDER, title = "Mid"),
            post(3, "Leaf", parentId = 2),
        )
        val actions = listOf(KbAction(op = "move_post", postTitle = "Leaf", newParentTitle = "Leaf"))
        assertTrue(ActionValidator.validate(actions, nodes).any { it.contains("cycle") })
    }

    // Moving a post under another post makes it a sub-post — a shape Browse walks into.
    @Test
    fun acceptsMoveUnderAPost() {
        val nodes = listOf(branch(1, "Root"), post(3, "Raft"), post(4, "Paxos"))
        val actions = listOf(
            KbAction(op = "move_post", postTitle = "Raft", newParentTitle = "Paxos"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, nodes))
    }

    @Test
    fun acceptsMoveUnderAPostCreatedEarlierInBatch() {
        val nodes = listOf(branch(1, "Root"), post(3, "Raft"))
        val actions = listOf(
            KbAction(op = "create_post", title = "Paxos", content = "# Paxos"),
            KbAction(op = "move_post", postTitle = "Raft", newParentTitle = "Paxos"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, nodes))
    }

    @Test
    fun acceptsMoveUnderAFolder() {
        val nodes = listOf(
            branch(1, "Root"),
            Node(id = 2, parentId = 1, type = NodeType.FOLDER, title = "Consensus"),
            post(3, "Raft"),
        )
        val actions = listOf(
            KbAction(op = "move_post", postTitle = "Raft", newParentTitle = "Consensus"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, nodes))
    }

    // Titles address posts everywhere, so a retitle onto a taken one must fail at review time
    // instead of throwing halfway through the executor's transaction.
    @Test
    fun rejectsRetitleOntoAnExistingPost() {
        val nodes = listOf(branch(1, "Root"), post(3, "Raft"), post(4, "Paxos"))
        val actions = listOf(
            KbAction(op = "update_post", postTitle = "Raft", title = "Paxos"),
        )
        assertTrue(
            ActionValidator.validate(actions, nodes).any { it.contains("already exists") },
        )
    }

    @Test
    fun acceptsRetitleToAFreeTitle() {
        val nodes = listOf(branch(1, "Root"), post(3, "Raft"))
        val actions = listOf(
            KbAction(op = "update_post", postTitle = "Raft", title = "Raft consensus"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, nodes))
    }

    // The review screen validates only the checked rows, but the numbers must still match them.
    @Test
    fun skipsDeselectedActionsWithoutRenumbering() {
        val actions = listOf(
            KbAction(op = "create_post", parentTitle = "No Such Branch", title = "Raft"),
            KbAction(op = "create_post", title = "Paxos", content = "# Paxos"),
        )
        val enabled = booleanArrayOf(false, true)
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, emptyList(), enabled))

        val both = ActionValidator.validate(actions, emptyList(), booleanArrayOf(true, true))
        assertTrue(both.any { it.startsWith("Action 1:") })
    }

    @Test
    fun allowsLinkToPostThatDoesNotExistYet() {
        val nodes = listOf(branch(1, "A"), post(3, "Raft"))
        val actions = listOf(
            KbAction(op = "create_link", fromTitle = "Raft", toTitle = "Not Written Yet"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, nodes))
    }

    @Test
    fun acceptsAPrerequisiteBetweenTwoExistingPosts() {
        val nodes = listOf(branch(1, "A"), post(2, "Raft"), post(3, "Consensus"))
        val actions = listOf(
            KbAction(op = "add_prerequisite", postTitle = "Consensus", requiresTitle = "Raft"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, nodes))
    }

    @Test
    fun aPrerequisiteNeedsSomethingToRequire() {
        val nodes = listOf(branch(1, "A"), post(2, "Raft"))
        val actions = listOf(KbAction(op = "add_prerequisite", postTitle = "Raft"))
        assertTrue(
            ActionValidator.validate(actions, nodes).any { it.contains("requiresTitle") },
        )
    }

    /** Unlike a wiki-link, a prerequisite has to resolve: a bar cannot count a post that isn't. */
    @Test
    fun aPrerequisiteMustNameAPostThatExists() {
        val nodes = listOf(branch(1, "A"), post(2, "Raft"))
        val actions = listOf(
            KbAction(op = "add_prerequisite", postTitle = "Raft", requiresTitle = "Nowhere"),
        )
        assertTrue(ActionValidator.validate(actions, nodes).isNotEmpty())
    }

    @Test
    fun aPostCannotBeItsOwnPrerequisite() {
        val nodes = listOf(branch(1, "A"), post(2, "Raft"))
        val actions = listOf(
            KbAction(op = "add_prerequisite", postTitle = "Raft", requiresTitle = "Raft"),
        )
        assertTrue(ActionValidator.validate(actions, nodes).any { it.contains("itself") })
    }

    /** A learning path is written and chained in one batch, so refs have to work here too. */
    @Test
    fun aPrerequisiteCanNameAPostCreatedInTheSameBatch() {
        val actions = listOf(
            KbAction(op = "create_post", title = "Letter A", ref = "p1"),
            KbAction(op = "create_post", title = "Letter B", ref = "p2"),
            KbAction(op = "add_prerequisite", postRef = "p2", requiresRef = "p1"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, emptyList()))
    }

    @Test
    fun aPrerequisiteRefMustNameAPostNotAContainer() {
        val actions = listOf(
            KbAction(op = "create_branch", title = "Alphabet", ref = "b1"),
            KbAction(op = "create_post", title = "Letter A", parentRef = "b1", ref = "p1"),
            KbAction(op = "add_prerequisite", postRef = "p1", requiresRef = "b1"),
        )
        assertTrue(ActionValidator.validate(actions, emptyList()).isNotEmpty())
    }

    @Test
    fun removePrerequisiteIsValidatedLikeAdd() {
        val nodes = listOf(branch(1, "A"), post(2, "Raft"), post(3, "Consensus"))
        val actions = listOf(
            KbAction(op = "remove_prerequisite", postTitle = "Consensus", requiresTitle = "Raft"),
        )
        assertEquals(emptyList<String>(), ActionValidator.validate(actions, nodes))
    }

    @Test
    fun bothPrerequisiteOpsAreKnown() {
        assertTrue(ActionValidator.isKnownOp("add_prerequisite"))
        assertTrue(ActionValidator.isKnownOp("remove_prerequisite"))
    }
}
