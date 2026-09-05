package com.cs426.learningmocha.ui.chat

/**
 * Guesses when the mode chips disagree with what the message actually asks for.
 *
 * The failure this exists to stop is quiet: a user types "write me a post about Raft" with
 * Answer still selected, and gets a friendly paragraph about Raft instead of a post — nothing
 * looks broken, so it is easy to blame the assistant rather than the chip. The check runs on
 * the phone, before anything is sent, and only ever *offers* a switch: it is a keyword guess,
 * so it must never quietly send under a mode the user did not pick.
 *
 * Deliberately conservative. A word that only sometimes means an action (like "update") is
 * left out, because a wrong prompt on every other message is worse than a missed one.
 */
object ChatModeHint {

    private val CREATE_WORDS = listOf(
        "create a post", "create post", "create posts", "create me", "create an article",
        "write me", "write a post", "write posts", "write an article", "write about",
        "make me a", "make a post", "make posts", "new post", "add a post", "add posts",
        "draft a", "draft me", "generate a post", "generate posts", "turn this into a post",
        "teach me", "a post about", "posts about", "article about", "learning path",
        "i want to learn", "help me learn",
    )

    private val ORGANIZE_WORDS = listOf(
        "reorganize", "reorganise", "organize", "organise", "restructure", "rearrange",
        "tidy up", "tidy my", "clean up my", "sort out", "file these", "file them",
        "group these", "group them", "split this into", "merge these", "move these",
    )

    private val SUGGEST_WORDS = listOf(
        "suggest", "recommend", "what should i learn", "what should i read",
        "ideas for", "give me ideas", "where should i start",
    )

    /**
     * A question and nothing else. Only used to steer *back* to Answer, and only when no
     * action word appears anywhere, since "what is Raft — write me a post on it" is both.
     */
    private val QUESTION_WORDS = listOf(
        "what is", "what are", "why does", "why is", "how does", "how do i",
        "explain", "tell me about", "what does",
    )

    /**
     * @return the mode this message looks like, or null when the current [mode] already fits —
     *   which includes every case where the guess is not confident enough to be worth a prompt
     */
    fun suggest(mode: String, text: String): String? {
        val body = text.lowercase()
        val wanted = buildSet {
            if (ORGANIZE_WORDS.any { it in body }) add(ChatModes.ORGANIZE)
            if (CREATE_WORDS.any { it in body }) add(ChatModes.MODIFY)
            if (SUGGEST_WORDS.any { it in body }) add(ChatModes.SUGGEST)
        }

        if (wanted.isEmpty()) {
            // Nothing here asks for a change. Offer the way back only from a mode that would
            // otherwise answer a plain question with a batch of edits to review.
            val asking = QUESTION_WORDS.any { it in body }
            return if (asking && ChatModes.proposesChanges(mode)) ChatModes.ANSWER else null
        }

        val current = ChatModes.parse(mode)
        // Already covered: the picked modes include everything the message asks for. A user
        // who picked more than the message needs is not second-guessed.
        if (current.containsAll(wanted)) return null
        return ChatModes.join(wanted)
    }
}
