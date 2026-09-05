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

    @Test
    fun rejectsDuplicateTitle() {
        val actions = listOf(KbAction(op = "create_post", title = "Raft", content = "x"))
        val errors = ActionValidator.validate(actions, listOf(post(2, "Raft")))
        assertTrue(errors.any { it.contains("already exists") })
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

    @Test
    fun rejectsBatchCreatedPostUsedAsParent() {
        val actions = listOf(
            KbAction(op = "create_post", title = "Raft", content = "# Raft"),
            KbAction(op = "create_post", parentTitle = "Raft", title = "Raft Election"),
        )
        assertTrue(
            ActionValidator.validate(actions, emptyList())
                .any { it.contains("parent cannot be a post") },
        )
    }

    @Test
    fun rejectsActionThatIsItsOwnParent() {
        val actions = listOf(
            KbAction(op = "create_folder", parentTitle = "Loop", title = "Loop"),
        )
        assertTrue(ActionValidator.validate(actions, emptyList()).isNotEmpty())
    }

    @Test
    fun rejectsDuplicateTitleWithinBatch() {
        val actions = listOf(
            KbAction(op = "create_post", title = "Raft", content = "a"),
            KbAction(op = "create_post", title = "raft", content = "b"),
        )
        assertTrue(
            ActionValidator.validate(actions, emptyList()).any { it.contains("already exists") },
        )
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

    // A post nested under a post is unreachable in Browse, so the batch must be rejected first.
    @Test
    fun rejectsMoveUnderAPost() {
        val nodes = listOf(branch(1, "Root"), post(3, "Raft"), post(4, "Paxos"))
        val actions = listOf(
            KbAction(op = "move_post", postTitle = "Raft", newParentTitle = "Paxos"),
        )
        assertTrue(
            ActionValidator.validate(actions, nodes).any { it.contains("parent cannot be a post") },
        )
    }

    @Test
    fun rejectsMoveUnderAPostCreatedEarlierInBatch() {
        val nodes = listOf(branch(1, "Root"), post(3, "Raft"))
        val actions = listOf(
            KbAction(op = "create_post", title = "Paxos", content = "# Paxos"),
            KbAction(op = "move_post", postTitle = "Raft", newParentTitle = "Paxos"),
        )
        assertTrue(
            ActionValidator.validate(actions, nodes).any { it.contains("parent cannot be a post") },
        )
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
}
