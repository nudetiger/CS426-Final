package com.cs426.learningmocha.util;

/**
 * Reads a partially-received AI envelope so the chat screen can render tokens
 * as they arrive.
 *
 * <p>The model is asked for exactly one JSON object (see {@code backend/prompts.js}),
 * which means the useful prose sits inside {@code {"type":"answer","text":"…"}} and
 * only becomes valid JSON once the last brace lands. Waiting for that would defeat
 * the point of streaming, so this class scans the buffer as-is: it reports which
 * kind of envelope is taking shape and decodes however much of the {@code text}
 * string has arrived.
 *
 * <p>Framework-free and deterministic, so it is covered by JVM unit tests.
 */
public final class StreamingAnswerExtractor {

    /** Envelope kinds the protocol defines, plus the two "not yet known" cases. */
    public static final String KIND_ANSWER = "answer";
    public static final String KIND_ACTIONS = "actions";
    public static final String KIND_CONTEXT_REQUEST = "context_request";
    /** The buffer is JSON but has not revealed its {@code type} yet. */
    public static final String KIND_UNKNOWN = "unknown";
    /** The model replied with plain prose instead of an envelope. */
    public static final String KIND_PROSE = "prose";

    private StreamingAnswerExtractor() {}

    /**
     * Classifies a partial reply.
     *
     * @param buffer everything received so far (may be empty or mid-token)
     */
    public static String kind(String buffer) {
        if (buffer == null) {
            return KIND_UNKNOWN;
        }
        String trimmed = buffer.trim();
        if (trimmed.isEmpty()) {
            return KIND_UNKNOWN;
        }
        if (trimmed.charAt(0) != '{') {
            return KIND_PROSE;
        }
        String type = stringValue(buffer, "type");
        if (type == null) {
            return KIND_UNKNOWN;
        }
        switch (type) {
            case KIND_ANSWER:
                return KIND_ANSWER;
            case KIND_ACTIONS:
                return KIND_ACTIONS;
            case KIND_CONTEXT_REQUEST:
                return KIND_CONTEXT_REQUEST;
            default:
                return KIND_UNKNOWN;
        }
    }

    /**
     * The text to show while the reply is still arriving.
     *
     * <p>Plain prose is echoed unchanged. An {@code answer} envelope yields as much
     * of its {@code text} field as has arrived, with JSON escapes decoded. Anything
     * else yields an empty string — action batches are never shown raw, they go to
     * the review screen once complete.
     */
    public static String partialAnswerText(String buffer) {
        String kind = kind(buffer);
        if (KIND_PROSE.equals(kind)) {
            return buffer;
        }
        if (!KIND_ANSWER.equals(kind) && !KIND_UNKNOWN.equals(kind)) {
            return "";
        }
        String text = stringValue(buffer, "text");
        return text == null ? "" : text;
    }

    /**
     * Decodes the value of a top-level-ish {@code "key":"…"} pair, tolerating a
     * value whose closing quote has not arrived yet.
     *
     * @return the decoded value, or {@code null} if the key or its opening quote
     *         is not in the buffer yet
     */
    private static String stringValue(String buffer, String key) {
        String needle = '"' + key + '"';
        int at = buffer.indexOf(needle);
        if (at < 0) {
            return null;
        }
        int cursor = at + needle.length();
        // Skip whitespace, then the colon, then whitespace again.
        cursor = skipWhitespace(buffer, cursor);
        if (cursor >= buffer.length() || buffer.charAt(cursor) != ':') {
            return null;
        }
        cursor = skipWhitespace(buffer, cursor + 1);
        if (cursor >= buffer.length() || buffer.charAt(cursor) != '"') {
            return null;
        }
        return decode(buffer, cursor + 1);
    }

    private static int skipWhitespace(String buffer, int from) {
        int cursor = from;
        while (cursor < buffer.length() && Character.isWhitespace(buffer.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    /** Decodes a JSON string body starting at {@code from}, stopping at the closing quote or end of buffer. */
    private static String decode(String buffer, int from) {
        StringBuilder out = new StringBuilder();
        int cursor = from;
        while (cursor < buffer.length()) {
            char c = buffer.charAt(cursor);
            if (c == '"') {
                break;
            }
            if (c != '\\') {
                out.append(c);
                cursor++;
                continue;
            }
            // An escape split across two chunks: stop here and wait for the rest.
            if (cursor + 1 >= buffer.length()) {
                break;
            }
            char esc = buffer.charAt(cursor + 1);
            switch (esc) {
                case 'n':
                    out.append('\n');
                    cursor += 2;
                    break;
                case 't':
                    out.append('\t');
                    cursor += 2;
                    break;
                case 'r':
                    out.append('\r');
                    cursor += 2;
                    break;
                case 'b':
                    out.append('\b');
                    cursor += 2;
                    break;
                case 'f':
                    out.append('\f');
                    cursor += 2;
                    break;
                case '"':
                case '\\':
                case '/':
                    out.append(esc);
                    cursor += 2;
                    break;
                case 'u':
                    if (cursor + 5 >= buffer.length()) {
                        return out.toString(); // incomplete unicode escape — wait
                    }
                    try {
                        out.append((char) Integer.parseInt(buffer.substring(cursor + 2, cursor + 6), 16));
                    } catch (NumberFormatException malformed) {
                        return out.toString();
                    }
                    cursor += 6;
                    break;
                default:
                    out.append(esc);
                    cursor += 2;
                    break;
            }
        }
        return out.toString();
    }
}
