package com.cs426.learningmocha.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PostMarkCatalogTest {

    @Test
    public void keepsAKnownIcon() {
        assertEquals("book", PostMarkCatalog.icon("Book"));
    }

    @Test
    public void dropsAnUnknownIcon() {
        assertNull(PostMarkCatalog.icon("rocket"));
        assertNull(PostMarkCatalog.icon(""));
        assertNull(PostMarkCatalog.icon(null));
    }

    @Test
    public void keepsAKnownColor() {
        assertEquals("amber", PostMarkCatalog.color(" AMBER "));
    }

    @Test
    public void dropsAnUnknownColor() {
        assertNull(PostMarkCatalog.color("neon"));
    }
}
