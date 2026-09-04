package com.cs426.learningmocha.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseFramesTest {

    @Test
    fun parsesDeltasInOrder() {
        val lines = listOf(
            """data: {"delta":"Raft "}""",
            """data: {"delta":"is a "}""",
            """data: {"delta":"consensus algorithm."}""",
        )
        val text = lines.mapNotNull { SseFrames.parse(it) }
            .joinToString("") { (it as StreamFrame.Delta).text }
        assertEquals("Raft is a consensus algorithm.", text)
    }

    @Test
    fun parsesDoneFrameWithFullReply() {
        val frame = SseFrames.parse(
            """data: {"done":true,"reply":"{\"type\":\"answer\",\"text\":\"Hi\"}","usage":{"total_tokens":42}}""",
        )
        assertEquals(StreamFrame.Done("""{"type":"answer","text":"Hi"}"""), frame)
    }

    @Test
    fun parsesErrorFrame() {
        val frame = SseFrames.parse("""data: {"error":"DeepSeek timed out","retryable":true}""")
        assertEquals(StreamFrame.Failure("DeepSeek timed out", true), frame)
    }

    @Test
    fun errorWithoutRetryableIsNotRetryable() {
        val frame = SseFrames.parse("""data: {"error":"Unknown mode \"chat\""}""")
        assertEquals(StreamFrame.Failure("Unknown mode \"chat\"", false), frame)
    }

    @Test
    fun ignoresCommentsBlanksAndSentinel() {
        assertNull(SseFrames.parse(": open"))
        assertNull(SseFrames.parse(""))
        assertNull(SseFrames.parse("   "))
        assertNull(SseFrames.parse("data: [DONE]"))
        assertNull(SseFrames.parse("data:"))
    }

    @Test
    fun ignoresMalformedJson() {
        assertNull(SseFrames.parse("data: {\"delta\":\"half"))
        assertNull(SseFrames.parse("data: not json at all"))
        assertNull(SseFrames.parse("""data: ["delta","x"]"""))
        assertNull(SseFrames.parse("""data: {"usage":{"total_tokens":1}}"""))
    }

    @Test
    fun decodesEscapedNewlinesAndQuotes() {
        val frame = SseFrames.parse("""data: {"delta":"# Title\n\nHe said \"hi\"\tand left"}""")
        assertTrue(frame is StreamFrame.Delta)
        assertEquals("# Title\n\nHe said \"hi\"\tand left", (frame as StreamFrame.Delta).text)
    }

    @Test
    fun acceptsLinesWithoutTheDataPrefix() {
        assertEquals(StreamFrame.Delta("x"), SseFrames.parse("""{"delta":"x"}"""))
    }
}
