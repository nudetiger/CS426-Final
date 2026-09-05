package com.cs426.learningmocha.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModesTest {

    @Test
    fun joinsActionModesInCanonicalOrder() {
        assertEquals(
            "suggest+modify+organize",
            ChatModes.join(setOf(ChatModes.ORGANIZE, ChatModes.SUGGEST, ChatModes.MODIFY)),
        )
    }

    // The chips can end up with nothing checked; the wire format has no such state.
    @Test
    fun emptyPickFallsBackToAnswer() {
        assertEquals(ChatModes.ANSWER, ChatModes.join(emptySet()))
    }

    // Answer is read-only by contract, so it cannot ride along with a mode that writes.
    @Test
    fun answerIsDroppedWhenAnActionModeIsAlsoPicked() {
        assertEquals(
            ChatModes.MODIFY,
            ChatModes.join(setOf(ChatModes.ANSWER, ChatModes.MODIFY)),
        )
    }

    @Test
    fun parsesACombinedMode() {
        assertEquals(setOf("modify", "organize"), ChatModes.parse("modify+organize"))
    }

    @Test
    fun parsesAnEmptyModeAsAnswer() {
        assertEquals(setOf(ChatModes.ANSWER), ChatModes.parse(""))
    }

    @Test
    fun knowsWhichModesMayPropose() {
        assertFalse(ChatModes.proposesChanges(ChatModes.ANSWER))
        assertTrue(ChatModes.proposesChanges("modify+organize"))
        assertTrue(ChatModes.proposesChanges(ChatModes.SUGGEST))
    }
}
