package com.cs426.learningmocha.ai.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionLabelsTest {

    @Test
    fun describesEachOpInHumanTerms() {
        assertEquals(
            "Create post Raft",
            ActionLabels.describe(KbAction(op = "create_post", title = "Raft")),
        )
        assertEquals(
            "Set status of Raft to FINISHED",
            ActionLabels.describe(KbAction(op = "set_status", postTitle = "Raft", status = "FINISHED")),
        )
        assertEquals(
            "Star Raft",
            ActionLabels.describe(KbAction(op = "set_favorite", postTitle = "Raft", favorite = true)),
        )
        assertEquals(
            "Unstar Raft",
            ActionLabels.describe(KbAction(op = "set_favorite", postTitle = "Raft", favorite = false)),
        )
        assertEquals(
            "Add term quorum",
            ActionLabels.describe(KbAction(op = "add_dictionary_entry", term = "quorum")),
        )
    }

    @Test
    fun unknownOpFallsBackToTheOpName() {
        assertEquals("explode", ActionLabels.describe(KbAction(op = "explode")))
        assertEquals("", ActionLabels.describe(KbAction()))
    }

    // Indent drives the tree preview on the review screen for generated learning paths.
    @Test
    fun indentFollowsTheRefChain() {
        val branch = KbAction(op = "create_branch", title = "Distributed Systems", ref = "b1")
        val folder = KbAction(op = "create_folder", title = "Consensus", ref = "f1", parentRef = "b1")
        val post = KbAction(op = "create_post", title = "Raft", parentRef = "f1")
        val all = listOf(branch, folder, post)

        assertEquals(0, ActionLabels.indent(branch, all))
        assertEquals(1, ActionLabels.indent(folder, all))
        assertEquals(2, ActionLabels.indent(post, all))
    }

    @Test
    fun indentTerminatesOnACyclicRefChain() {
        val a = KbAction(op = "create_folder", title = "A", ref = "a", parentRef = "b")
        val b = KbAction(op = "create_folder", title = "B", ref = "b", parentRef = "a")
        assertEquals(2, ActionLabels.indent(a, listOf(a, b)))
    }
}
