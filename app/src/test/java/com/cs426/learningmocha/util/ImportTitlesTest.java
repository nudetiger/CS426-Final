package com.cs426.learningmocha.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ImportTitlesTest {

    private static Set<String> taken(String... titles) {
        Set<String> out = new HashSet<>();
        for (String title : titles) {
            out.add(ImportTitles.key(title));
        }
        return out;
    }

    @Test
    public void keepsATitleNothingElseUses() {
        assertEquals("Raft", ImportTitles.uniqueTitle(taken("Paxos"), "Raft"));
        assertEquals("Raft", ImportTitles.uniqueTitle(new HashSet<String>(), "Raft"));
    }

    @Test
    public void suffixesTheFirstCollision() {
        assertEquals("Raft (imported)", ImportTitles.uniqueTitle(taken("Raft"), "Raft"));
    }

    @Test
    public void numbersTheSecondCollisionOnwards() {
        assertEquals(
                "Raft (imported 2)",
                ImportTitles.uniqueTitle(taken("Raft", "Raft (imported)"), "Raft"));
        assertEquals(
                "Raft (imported 3)",
                ImportTitles.uniqueTitle(
                        taken("Raft", "Raft (imported)", "Raft (imported 2)"), "Raft"));
    }

    // Titles resolve through COLLATE NOCASE lookups, so a case-only difference is a collision.
    @Test
    public void collidesIgnoringCase() {
        assertEquals("RAFT (imported)", ImportTitles.uniqueTitle(taken("raft"), "RAFT"));
        assertEquals(
                "raft (imported 2)",
                ImportTitles.uniqueTitle(taken("RAFT", "Raft (Imported)"), "raft"));
    }

    /** Importing a file twice must not produce two posts under one title. */
    @Test
    public void claimedTitlesSplitPostsInsideOneImport() {
        Set<String> taken = taken("Raft");
        for (String expected : Arrays.asList("Raft (imported)", "Raft (imported 2)")) {
            String title = ImportTitles.uniqueTitle(taken, "Raft");
            assertEquals(expected, title);
            taken.add(ImportTitles.key(title));
        }
    }
}
