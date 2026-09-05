package com.cs426.learningmocha.util;

/**
 * The allowed post-mark keys, shared by the UI catalog and the AI action path.
 * Unknown keys are dropped rather than stored, so a typo cannot strand a row
 * with a glyph the app will never draw.
 */
public final class PostMarkCatalog {

    private PostMarkCatalog() {}

    public static final String[] ICONS = {
        "page", "book", "folder", "branch", "star", "tag",
        "chat", "graph", "coffee", "play", "palette", "search",
    };

    public static final String[] COLORS = {
        "brown", "sage", "amber", "blue", "green", "gold", "sky", "violet", "rose",
    };

    public static String icon(String raw) {
        return match(ICONS, raw);
    }

    public static String color(String raw) {
        return match(COLORS, raw);
    }

    private static String match(String[] keys, String raw) {
        if (raw == null) return null;
        String wanted = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (wanted.isEmpty()) return null;
        for (String key : keys) {
            if (key.equals(wanted)) return key;
        }
        return null;
    }
}
