package com.cs426.learningmocha.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModesTest {

    @Test
    fun `answer is its own canonical form`() {
        assertEquals(ChatModes.ANSWER, ChatModes.parse("answer"))
    }

    @Test
    fun `assist is its own canonical form`() {
        assertEquals(ChatModes.ASSIST, ChatModes.parse("assist"))
    }

    @Test
    fun `suggest, modify and organize all fold to assist`() {
        assertEquals(ChatModes.ASSIST, ChatModes.parse("suggest"))
        assertEquals(ChatModes.ASSIST, ChatModes.parse("modify"))
        assertEquals(ChatModes.ASSIST, ChatModes.parse("organize"))
    }

    @Test
    fun `a stored combination from before the merge folds to assist`() {
        assertEquals(ChatModes.ASSIST, ChatModes.parse("modify+organize"))
        assertEquals(ChatModes.ASSIST, ChatModes.parse("suggest+modify+organize"))
    }

    @Test
    fun `case and padding do not change the mode`() {
        assertEquals(ChatModes.ASSIST, ChatModes.parse("  Modify + Organize "))
    }

    @Test
    fun `nothing at all is answer`() {
        assertEquals(ChatModes.ANSWER, ChatModes.parse(""))
        assertEquals(ChatModes.ANSWER, ChatModes.parse(null))
    }

    /**
     * The safety property. A mode string the app does not recognise — a newer build's value in
     * an old row, a corrupted export — must never be read as permission to change the library.
     */
    @Test
    fun `an unrecognised mode falls back to answer rather than assist`() {
        assertEquals(ChatModes.ANSWER, ChatModes.parse("banana"))
        assertFalse(ChatModes.proposesChanges("banana"))
    }

    @Test
    fun `only assist may propose changes`() {
        assertFalse(ChatModes.proposesChanges(ChatModes.ANSWER))
        assertTrue(ChatModes.proposesChanges(ChatModes.ASSIST))
    }

    @Test
    fun `a legacy action mode still counts as proposing changes`() {
        assertTrue(ChatModes.proposesChanges("organize"))
        assertTrue(ChatModes.proposesChanges("modify+organize"))
    }
}
