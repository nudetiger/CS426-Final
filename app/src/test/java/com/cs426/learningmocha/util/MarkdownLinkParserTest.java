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
