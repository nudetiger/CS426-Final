package com.cs426.learningmocha.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FtsQueryBuilderTest {

    @Test
    public void andJoinsPrefixTokens() {
        assertEquals("hello* AND world*", FtsQueryBuilder.toMatchQuery("hello world"));
    }

    @Test
    public void stripsMatchOperators() {
        assertEquals("raft*", FtsQueryBuilder.toMatchQuery("\"raft\"*"));
    }

    @Test
    public void punctuationOnlyIsNull() {
        assertNull(FtsQueryBuilder.toMatchQuery("   "));
        assertNull(FtsQueryBuilder.toMatchQuery("***"));
        assertNull(FtsQueryBuilder.toMatchQuery(null));
    }
}
