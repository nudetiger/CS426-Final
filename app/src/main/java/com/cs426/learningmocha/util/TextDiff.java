package com.cs426.learningmocha.util;

/**
 * Preview helper for the AI review screen.
 * ponytail: whole-document before/after, not a Myers diff. Upgrade if hunks are needed.
 */
public final class TextDiff {

    private TextDiff() {}

    public static String preview(String before, String after) {
        String left = before == null ? "" : before;
        String right = after == null ? "" : after;
        if (left.equals(right)) {
            return "(no text change)";
        }
        return "— before —\n" + left + "\n\n— after —\n" + right;
    }
}
