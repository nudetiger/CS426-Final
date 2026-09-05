package com.cs426.learningmocha.ui.chat

/**
 * The chat mode: whether this conversation may touch the library.
 *
 * There used to be four — Answer, Suggest, Modify, Organize — with the last three combinable,
 * because a real request is often two of them at once ("write me posts on graph algorithms and
 * file them under Algorithms"). Combining was the right fix for the wrong problem: all three
 * produce the same artefact, an actions batch that lands on the review screen, and the model
 * was already free to mix them inside one reply. What was left on screen was a row of chips
 * asking the user to pre-classify their own sentence before typing it.
 *
 * So there are two, and they are exclusive. [ANSWER] is read-only by contract; [ASSIST] may
 * propose changes, which the user still approves one by one before anything is written.
 *
 * `backend/prompts.js` parses the same two strings. Keep both sides in step.
 */
object ChatModes {

    const val ANSWER = "answer"
    const val ASSIST = "assist"

    /**
     * Modes written by builds from before the merge. All three meant "may propose changes", so
     * they fold into [ASSIST] on read and no stored chat has to be rewritten to stay readable.
     */
    private val LEGACY_ACTION_MODES = setOf("suggest", "modify", "organize")

    /**
     * The canonical mode for a stored row or a wire value.
     *
     * Anything unrecognised reads as [ANSWER] rather than [ASSIST]: a mode string this build
     * does not understand must never be mistaken for permission to change the user's library.
     */
    fun parse(mode: String?): String {
        val parts = mode.orEmpty()
            .split('+')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        val proposes = parts.any { it == ASSIST || it in LEGACY_ACTION_MODES }
        return if (proposes) ASSIST else ANSWER
    }

    /** Whether [mode] is allowed to propose changes at all. */
    fun proposesChanges(mode: String?): Boolean = parse(mode) == ASSIST
}
