package com.cs426.learningmocha.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MarkdownLinkParserTest {

    @Test
    public void extractsTrimmedWikiTitlesAndSpans() {
        String md = "See [[ Branches, folders, and posts ]] and [[Writing in Markdown]].";
        List<MarkdownLinkParser.WikiLink> links = MarkdownLinkParser.wikiLinks(md);
        assertEquals(2, links.size());
        assertEquals("Branches, folders, and posts", links.get(0).title);
        assertEquals("[[ Branches, folders, and posts ]]",
                md.substring(links.get(0).start, links.get(0).end));
        assertEquals("Writing in Markdown", links.get(1).title);
    }

    @Test
    public void skipsEmptyWikiLinks() {
        assertTrue(MarkdownLinkParser.wikiLinks("[[  ]] and []").isEmpty());
    }

    // Renaming a post rewrites the links pointing at it, or every inbound link dies.
    @Test
    public void renamesWikiLinksIgnoringCaseAndPadding() {
        String md = "See [[ spring boot ]] and [[Spring Boot]] and [[Spring Boots]].";
        String out = MarkdownLinkParser.renameWikiLinks(md, "Spring Boot", "Spring Framework");
        assertEquals(
                "See [[Spring Framework]] and [[Spring Framework]] and [[Spring Boots]].",
                out);
    }

    @Test
    public void renameTreatsTitlesAsLiteralsNotPatterns() {
        String md = "Read [[C++ (basics)]] first.";
        assertEquals(
                "Read [[C# notes]] first.",
                MarkdownLinkParser.renameWikiLinks(md, "C++ (basics)", "C# notes"));
    }

    // A '$' in the new title is a group reference to Matcher.replaceAll if it is not quoted.
    @Test
    public void renameKeepsDollarsAndBackslashesInTheNewTitle() {
        assertEquals(
                "[[Cost $5 \\ net]]",
                MarkdownLinkParser.renameWikiLinks("[[Cost]]", "Cost", "Cost $5 \\ net"));
    }

    @Test
    public void renameLeavesUnrelatedMarkdownAlone() {
        String md = "No links here, and [[Other]] stays.";
        assertEquals(md, MarkdownLinkParser.renameWikiLinks(md, "Raft", "Paxos"));
        assertEquals(md, MarkdownLinkParser.renameWikiLinks(md, "Other", "Other"));
        assertEquals(md, MarkdownLinkParser.renameWikiLinks(md, "Other", "  "));
    }

    @Test
    public void extractsUniqueYoutubeIds() {
        String md = ""
                + "https://www.youtube.com/watch?v=dQw4w9WgXcQ extra\n"
                + "https://youtu.be/dQw4w9WgXcQ\n"
                + "https://youtube.com/embed/abcdefghijk\n"
                + "https://www.youtube.com/shorts/SHORTS12abc\n"
                + "https://example.com/watch?v=dQw4w9WgXcQ";
        List<MarkdownLinkParser.YoutubeUrl> urls = MarkdownLinkParser.youtubeUrls(md);
        assertEquals(3, urls.size());
        assertEquals("dQw4w9WgXcQ", urls.get(0).videoId);
        assertEquals("abcdefghijk", urls.get(1).videoId);
        assertEquals("SHORTS12abc", urls.get(2).videoId);
    }
}
