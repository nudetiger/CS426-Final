package com.cs426.learningmocha.ui.chat

/**
 * The chat mode as a set rather than a single choice.
 *
 * A real request is often two things at once — "write me posts on graph algorithms and file
 * them under Algorithms" is create *and* reorganize — and forcing one chip meant sending it
 * twice, or getting half of it. The three action modes therefore combine; [ANSWER] does not,
 * because it is read-only by contract and combining it would mean "change nothing, and also
 * change these things".
 *
 * The wire format is the parts joined by "+" in canonical order, which is what
 * `backend/prompts.js` parses back out.
 */
object ChatModes {

    const val ANSWER = "answer"
    const val SUGGEST = "suggest"
    const val MODIFY = "modify"
    const val ORGANIZE = "organize"

    /** Canonical order, so "modify+organize" never also appears as "organize+modify". */
    private val ACTION_ORDER = listOf(SUGGEST, MODIFY, ORGANIZE)

    /** Whether [mode] is allowed to propose changes at all. */
    fun proposesChanges(mode: String): Boolean = parse(mode).any { it != ANSWER }

    /**
     * Normalizes a set of picked modes into the wire string. An empty pick, or one that still
     * holds [ANSWER] alongside an action mode, falls back to the action modes alone — and to
     * [ANSWER] when there are none, so there is always exactly one valid answer.
     */
    fun join(picked: Set<String>): String {
        val actions = ACTION_ORDER.filter { it in picked }
        return if (actions.isEmpty()) ANSWER else actions.joinToString("+")
    }

    fun parse(mode: String): Set<String> {
        val parts = mode.split('+').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        return if (parts.isEmpty()) setOf(ANSWER) else parts.toSet()
    }

    fun contains(mode: String, part: String): Boolean = part in parse(mode)
}
