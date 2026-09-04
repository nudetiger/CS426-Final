package com.cs426.learningmocha.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StreamingAnswerExtractorTest {

    @Test
    public void classifiesEnvelopeKinds() {
        assertEquals(StreamingAnswerExtractor.KIND_ANSWER,
                StreamingAnswerExtractor.kind("{\"type\":\"answer\",\"text\":\"hi\"}"));
        assertEquals(StreamingAnswerExtractor.KIND_ACTIONS,
                StreamingAnswerExtractor.kind("{\"type\":\"actions\",\"summary\":\"x\""));
        assertEquals(StreamingAnswerExtractor.KIND_CONTEXT_REQUEST,
                StreamingAnswerExtractor.kind("{\"type\": \"context_request\", \"queries\": ["));
        assertEquals(StreamingAnswerExtractor.KIND_PROSE,
                StreamingAnswerExtractor.kind("Sorry, I cannot do that."));
        assertEquals(StreamingAnswerExtractor.KIND_UNKNOWN,
                StreamingAnswerExtractor.kind("{\"ty"));
        assertEquals(StreamingAnswerExtractor.KIND_UNKNOWN,
                StreamingAnswerExtractor.kind(""));
        assertEquals(StreamingAnswerExtractor.KIND_UNKNOWN,
                StreamingAnswerExtractor.kind(null));
    }

    @Test
    public void growsTheAnswerTokenByToken() {
        String[] tokens = {
            "{", "\"", "type", "\":\"", "answer", "\",\"", "text", "\":\"",
            "Raft", " is", " a", " consensus", " algorithm", ".\"", "}",
        };
        StringBuilder buffer = new StringBuilder();
        String last = "";
        for (String token : tokens) {
            buffer.append(token);
            String partial = StreamingAnswerExtractor.partialAnswerText(buffer.toString());
            // Monotonic: the visible answer only ever grows while tokens arrive.
            assertEquals(partial.substring(0, Math.min(last.length(), partial.length())),
                    last.substring(0, Math.min(last.length(), partial.length())));
            last = partial;
        }
        assertEquals("Raft is a consensus algorithm.", last);
    }

    @Test
    public void decodesJsonEscapes() {
        String buffer = "{\"type\":\"answer\",\"text\":\"# Raft\\n\\nUses a **leader**.\\tTab \\\"quoted\\\" "
                + "path\\/here \\u00e9\"}";
        assertEquals("# Raft\n\nUses a **leader**.\tTab \"quoted\" path/here \u00e9",
                StreamingAnswerExtractor.partialAnswerText(buffer));
    }

    @Test
    public void waitsForEscapesSplitAcrossChunks() {
        assertEquals("line", StreamingAnswerExtractor.partialAnswerText(
                "{\"type\":\"answer\",\"text\":\"line\\"));
        assertEquals("line", StreamingAnswerExtractor.partialAnswerText(
                "{\"type\":\"answer\",\"text\":\"line\\u00"));
        assertEquals("line\n", StreamingAnswerExtractor.partialAnswerText(
                "{\"type\":\"answer\",\"text\":\"line\\n"));
    }

    @Test
    public void hidesActionBatchesAndPassesProseThrough() {
        assertEquals("", StreamingAnswerExtractor.partialAnswerText(
                "{\"type\":\"actions\",\"summary\":\"Create 4 posts\",\"actions\":[{\"op\":\"create_post\""));
        assertEquals("", StreamingAnswerExtractor.partialAnswerText(
                "{\"type\":\"context_request\",\"queries\":[{\"op\":\"get_tags\"}]}"));
        assertEquals("plain markdown reply",
                StreamingAnswerExtractor.partialAnswerText("plain markdown reply"));
    }

    @Test
    public void returnsEmptyBeforeTheTextFieldArrives() {
        assertEquals("", StreamingAnswerExtractor.partialAnswerText("{\"type\":\"answer\""));
        assertEquals("", StreamingAnswerExtractor.partialAnswerText("{\"type\":\"answer\",\"text\":"));
        assertEquals("", StreamingAnswerExtractor.partialAnswerText("{\"type\":\"answer\",\"text\":\"\""));
    }
}
