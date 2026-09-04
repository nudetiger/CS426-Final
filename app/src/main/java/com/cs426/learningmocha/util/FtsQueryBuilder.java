package com.cs426.learningmocha.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns a user search box into an FTS4 MATCH query.
 * Strips MATCH operators so a typed quote or star cannot blow up the query.
 */
public final class FtsQueryBuilder {

    private static final Pattern KEEP = Pattern.compile("[^\\p{L}\\p{N}_]+");

    private FtsQueryBuilder() {}

    /**
     * @return MATCH query, or {@code null} if nothing searchable remains
     */
    public static String toMatchQuery(String raw) {
        if (raw == null) {
            return null;
        }
        String[] bits = raw.trim().split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String bit : bits) {
            String clean = KEEP.matcher(bit).replaceAll("");
            if (!clean.isEmpty()) {
                tokens.add(clean + "*");
            }
        }
        if (tokens.isEmpty()) {
            return null;
        }
        return String.join(" AND ", tokens);
    }
}
