package com.cs426.learningmocha.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatModeHintTest {

    @Test
    fun `a request to write a post offers assist`() {
        assertEquals(
            ChatModes.ASSIST,
            ChatModeHint.suggest(ChatModes.ANSWER, "Write me a post about Raft"),
        )
    }

    @Test
    fun `a request to reorganize offers assist`() {
        assertEquals(
            ChatModes.ASSIST,
            ChatModeHint.suggest(ChatModes.ANSWER, "Please reorganize my whole library"),
        )
    }

    @Test
    fun `a request for recommendations offers assist`() {
        assertEquals(
            ChatModes.ASSIST,
            ChatModeHint.suggest(ChatModes.ANSWER, "suggest what I should read next"),
        )
    }

    @Test
    fun `an action request already in assist is left alone`() {
        assertNull(ChatModeHint.suggest(ChatModes.ASSIST, "Write me a post about Raft"))
    }

    @Test
    fun `a plain question in assist offers the way back to answer`() {
        assertEquals(
            ChatModes.ANSWER,
            ChatModeHint.suggest(ChatModes.ASSIST, "What is the Raft leader election?"),
        )
    }

    @Test
    fun `a plain question in answer is left alone`() {
        assertNull(ChatModeHint.suggest(ChatModes.ANSWER, "What is Raft?"))
    }

    @Test
    fun `small talk is left alone`() {
        assertNull(ChatModeHint.suggest(ChatModes.ANSWER, "thanks, that helped a lot"))
    }

    /** "What is Raft — and write it up" is a work order with a question attached, not a question. */
    @Test
    fun `a message that both asks and instructs counts as an action`() {
        assertNull(ChatModeHint.suggest(ChatModes.ASSIST, "What is Raft? Write me a post about it"))
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(
            ChatModes.ASSIST,
            ChatModeHint.suggest(ChatModes.ANSWER, "WRITE ME A POST on B-trees"),
        )
    }

    /** A conversation left open from before the merge still holds "modify" as its mode. */
    @Test
    fun `a legacy mode is read as the assist it folds to`() {
        assertNull(ChatModeHint.suggest("modify", "Write me a post about Raft"))
        assertEquals(ChatModes.ANSWER, ChatModeHint.suggest("organize", "What is Raft?"))
    }
}
