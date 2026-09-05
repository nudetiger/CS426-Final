package com.cs426.learningmocha.util;

import java.util.Locale;
import java.util.Set;

/**
 * Picks a title nothing else has claimed by appending " (2)", " (3)", … .
 *
 * Creating a post whose title is taken used to fail — which cost the user the whole
 * write, and cost an AI batch every action after it. Numbering instead means a second
 * "Raft" becomes "Raft (2)" and the create always lands. Renames still refuse a taken
 * title: there the user named a specific thing and silently renaming it would be a lie.
 *
 * Framework-free so it can be unit-tested on the JVM.
 */
public final class TitleDedup {

    /** Matches the app's practical ceiling; a numbered title never grows past it. */
    private static final int MAX_TRIES = 999;

    private TitleDedup() {}

    /**
     * @param base  the wanted title; leading and trailing space is dropped
     * @param taken lowercased titles already in use (see {@link #lower})
     * @return {@code base} when it is free, else the first free "base (n)" for n ≥ 2
     */
    public static String unique(String base, Set<String> taken) {
        String trimmed = base == null ? "" : base.trim();
        if (trimmed.isEmpty() || !taken.contains(lower(trimmed))) {
            return trimmed;
        }
        for (int n = 2; n <= MAX_TRIES; n++) {
            String candidate = trimmed + " (" + n + ")";
            if (!taken.contains(lower(candidate))) {
                return candidate;
            }
        }
        // Astronomically unlikely; a timestamp still beats throwing away the user's write.
        return trimmed + " (" + System.currentTimeMillis() + ")";
    }

    /** The one case-folding rule every caller has to agree on to build the taken set. */
    public static String lower(String title) {
        return title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
    }
}
