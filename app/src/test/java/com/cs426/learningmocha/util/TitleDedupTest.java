package com.cs426.learningmocha.util;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class TitleDedupTest {

    private static Set<String> taken(String... titles) {
        Set<String> set = new HashSet<>();
        for (String title : titles) {
            set.add(TitleDedup.lower(title));
        }
        return set;
    }

    @Test
    public void keepsAFreeTitle() {
        assertEquals("Raft", TitleDedup.unique("Raft", taken("Paxos")));
    }

    @Test
    public void numbersTheFirstClash() {
        assertEquals("Raft (2)", TitleDedup.unique("Raft", taken("Raft")));
    }

    @Test
    public void keepsCountingPastEveryNumberedVariant() {
        assertEquals("Raft (4)", TitleDedup.unique("Raft", taken("Raft", "Raft (2)", "Raft (3)")));
    }

    // Titles are matched the way the DAO matches them (COLLATE NOCASE), so a differently
    // cased duplicate has to be numbered too rather than sneaking in as a second row.
    @Test
    public void ignoresCase() {
        assertEquals("Raft (2)", TitleDedup.unique("Raft", taken("RAFT")));
    }

    @Test
    public void trimsBeforeComparing() {
        assertEquals("Raft (2)", TitleDedup.unique("  Raft  ", taken("Raft")));
    }

    // A gap in the numbering is filled rather than skipped: the user deleted "Raft (2)",
    // so that name is free again.
    @Test
    public void reusesAFreedNumber() {
        assertEquals("Raft (2)", TitleDedup.unique("Raft", taken("Raft", "Raft (3)")));
    }

    @Test
    public void leavesAnEmptyTitleAlone() {
        assertEquals("", TitleDedup.unique("   ", taken("Raft")));
    }

    @Test
    public void numbersATitleThatIsAlreadyNumbered() {
        assertEquals(
                "Raft (2) (2)",
                TitleDedup.unique("Raft (2)", taken("Raft", "Raft (2)")));
    }

    @Test
    public void treatsAnyOrderOfArgumentsAsUnchangedWhenNothingIsTaken() {
        assertEquals("Raft", TitleDedup.unique("Raft", new HashSet<>(Arrays.asList())));
    }
}
