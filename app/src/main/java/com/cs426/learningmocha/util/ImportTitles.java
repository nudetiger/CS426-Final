package com.cs426.learningmocha.util;

import java.util.Locale;
import java.util.Set;

/**
 * Title de-duplication for a merge import.
 *
 * <p>Titles address posts everywhere — {@code [[wiki-links]]}, AI actions, {@code findPostByTitle}
 * — so importing a post under a title the library already holds would leave every one of those
 * lookups picking an arbitrary winner. The imported copy is renamed instead.
 */
public final class ImportTitles {

    private ImportTitles() {}

    /**
     * @param takenLower titles already in use, each passed through {@link #key(String)}
     * @param title      the incoming title
     * @return {@code title} when it is free, else "title (imported)", then "title (imported 2)",
     *         "title (imported 3)", … — the first form nothing has claimed yet
     */
    public static String uniqueTitle(Set<String> takenLower, String title) {
        if (title == null || takenLower == null || !takenLower.contains(key(title))) {
            return title;
        }
        String candidate = title + " (imported)";
        int n = 1;
        while (takenLower.contains(key(candidate))) {
            n++;
            candidate = title + " (imported " + n + ")";
        }
        return candidate;
    }

    /** Collisions are case-insensitive, matching the {@code COLLATE NOCASE} title lookups. */
    public static String key(String title) {
        return title == null ? "" : title.toLowerCase(Locale.ROOT);
    }
}
