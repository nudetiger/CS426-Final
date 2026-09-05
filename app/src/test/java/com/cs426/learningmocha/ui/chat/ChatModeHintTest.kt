package com.cs426.learningmocha.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatModeHintTest {

    @Test
    fun offersModifyWhenAnswerModeIsAskedToWrite() {
        assertEquals(
            ChatModes.MODIFY,
            ChatModeHint.suggest(ChatModes.ANSWER, "Write me a post about Raft"),
        )
    }

    @Test
    fun offersOrganizeForARestructureRequest() {
        assertEquals(
            ChatModes.ORGANIZE,
            ChatModeHint.suggest(ChatModes.ANSWER, "Please reorganize my whole library"),
        )
    }

    // The request that made combined modes necessary: create something and file it in one go.
    @Test
    fun offersBothWhenTheMessageAsksForBoth() {
        assertEquals(
            "modify+organize",
            ChatModeHint.suggest(
                ChatModes.ANSWER,
                "Write me a post on Paxos and reorganize the consensus folder",
            ),
        )
    }

    @Test
    fun staysQuietWhenTheModeAlreadyCoversTheRequest() {
        assertNull(ChatModeHint.suggest(ChatModes.MODIFY, "Write me a post about Raft"))
        assertNull(ChatModeHint.suggest("modify+organize", "Write a post and reorganize things"))
    }

    // A user who picked more than the message needs made a choice; do not second-guess it.
    @Test
    fun staysQuietWhenTheModeIsBroaderThanTheRequest() {
        assertNull(ChatModeHint.suggest("modify+organize", "Write me a post about Raft"))
    }

    @Test
    fun offersAnswerForAPlainQuestionInAnActionMode() {
        assertEquals(
            ChatModes.ANSWER,
            ChatModeHint.suggest(ChatModes.MODIFY, "What is the Raft leader election?"),
        )
    }

    // A question that also asks for a post is not a question; it must not be steered back.
    @Test
    fun prefersTheActionWhenAQuestionAlsoAsksForAPost() {
        assertNull(
            ChatModeHint.suggest(ChatModes.MODIFY, "What is Raft? Write me a post about it"),
        )
    }

    @Test
    fun staysQuietOnAnOrdinaryQuestionInAnswerMode() {
        assertNull(ChatModeHint.suggest(ChatModes.ANSWER, "What is Raft?"))
    }

    // Nothing here is a keyword either way; a guess would only get in the user's way.
    @Test
    fun staysQuietOnAmbiguousText() {
        assertNull(ChatModeHint.suggest(ChatModes.ANSWER, "thanks, that helped a lot"))
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals(
            ChatModes.MODIFY,
            ChatModeHint.suggest(ChatModes.ANSWER, "WRITE ME A POST on B-trees"),
        )
    }
}
