package com.cs426.learningmocha.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TextDiffTest {

    @Test
    public void unchangedIsExplicit() {
        assertEquals("(no text change)", TextDiff.preview("a", "a"));
    }

    @Test
    public void showsBeforeAndAfter() {
        String out = TextDiff.preview("old", "new");
        assertTrue(out.contains("old"));
        assertTrue(out.contains("new"));
    }

    /** A create_post preview has no "before" side, so nulls must not blow up the review screen. */
    @Test
    public void treatsNullAsEmpty() {
        assertEquals("(no text change)", TextDiff.preview(null, null));
        assertEquals("(no text change)", TextDiff.preview(null, ""));
        assertTrue(TextDiff.preview(null, "new").contains("new"));
        assertTrue(TextDiff.preview("old", null).contains("old"));
    }
}
