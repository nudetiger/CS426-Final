package com.cs426.learningmocha.ai.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionParserTest {

    @Test
    fun parsesAnswer() {
        val env = ActionParser.parse("""{"type":"answer","text":"Hello"}""")
        assertEquals("answer", env.type)
        assertEquals("Hello", env.text)
    }

    @Test
    fun parsesFencedActionsAndRefs() {
        val raw = """
            ```json
            {"type":"actions","summary":"Add Raft","actions":[
              {"op":"create_branch","title":"Distributed Systems","ref":"b1"},
              {"op":"create_post","parentRef":"b1","title":"Raft","content":"# Raft","tags":["consensus"]}
            ]}
            ```
        """.trimIndent()
        val env = ActionParser.parse(raw)
        assertEquals("actions", env.type)
        assertEquals(2, env.actions!!.size)
        assertEquals("b1", env.actions!![0].ref)
        assertEquals("consensus", env.actions!![1].tags!![0])
    }

    @Test
    fun parsesContextRequest() {
        val env = ActionParser.parse(
            """{"type":"context_request","queries":[{"op":"search_posts","args":{"query":"raft"}}]}""",
        )
        assertEquals("search_posts", env.queries!![0].op)
        assertEquals("raft", env.queries!![0].arg("query"))
    }

    @Test
    fun parseOrAnswerFallsBackOnGarbage() {
        val env = ActionParser.parseOrAnswer("not json")
        assertEquals("answer", env.type)
        assertEquals("not json", env.text)
    }

    // Models often wrap the envelope in a sentence despite the "no prose" instruction.
    @Test
    fun stripsProseAroundTheEnvelope() {
        val env = ActionParser.parse("""Sure! {"type":"answer","text":"Hi"} Hope that helps.""")
        assertEquals("answer", env.type)
        assertEquals("Hi", env.text)
    }

    @Test
    fun keepsBracesInsideGeneratedMarkdown() {
        val raw = """{"type":"actions","summary":"s","actions":[
            {"op":"create_post","title":"Kotlin","content":"fun main() { println(1) }"}
        ]}"""
        val env = ActionParser.parse(raw)
        assertEquals("fun main() { println(1) }", env.actions!![0].content)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEnvelopeWithoutType() {
        ActionParser.parse("""{"text":"no type here"}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonJson() {
        ActionParser.parse("just talking")
    }

    @Test
    fun parseOrAnswerFallsBackWhenTypeIsMissing() {
        val raw = """{"text":"no type here"}"""
        val env = ActionParser.parseOrAnswer(raw)
        assertEquals("answer", env.type)
        assertEquals(raw, env.text)
    }

    @Test
    fun parsesBareFenceWithoutLanguageTag() {
        val env = ActionParser.parse("```\n{\"type\":\"answer\",\"text\":\"Hi\"}\n```")
        assertEquals("Hi", env.text)
    }
}
