package com.cs426.learningmocha.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Framework-free extractors for {@code [[Wiki Title]]} and YouTube URLs in post markdown.
 */
public final class MarkdownLinkParser {

    private static final Pattern WIKI = Pattern.compile("\\[\\[([^\\[\\]]+?)]]");
    private static final Pattern YOUTUBE = Pattern.compile(
            "(?i)https?://(?:(?:www|m)\\.)?"
                    + "(?:youtube\\.com/watch\\?\\S*?v=|youtu\\.be/|youtube\\.com/(?:embed|shorts)/)"
                    + "([A-Za-z0-9_-]{11})"
    );

    private MarkdownLinkParser() {}

    public static final class WikiLink {
        public final String title;
        public final int start;
        public final int end;

        public WikiLink(String title, int start, int end) {
            this.title = title;
            this.start = start;
            this.end = end;
        }
    }

    public static final class YoutubeUrl {
        public final String url;
        public final String videoId;

        public YoutubeUrl(String url, String videoId) {
            this.url = url;
            this.videoId = videoId;
        }
    }

    public static List<WikiLink> wikiLinks(String markdown) {
        List<WikiLink> out = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return out;
        }
        Matcher matcher = WIKI.matcher(markdown);
        while (matcher.find()) {
            String title = matcher.group(1).trim();
            if (!title.isEmpty()) {
                out.add(new WikiLink(title, matcher.start(), matcher.end()));
            }
        }
        return out;
    }

    /**
     * Rewrites every {@code [[oldTitle]]} to {@code [[newTitle]]}, ignoring case and padding
     * inside the brackets, so renaming a post does not orphan the links pointing at it.
     *
     * @return the markdown unchanged when there is nothing to rewrite
     */
    public static String renameWikiLinks(String markdown, String oldTitle, String newTitle) {
        if (markdown == null || markdown.isEmpty() || oldTitle == null || newTitle == null) {
            return markdown;
        }
        String from = oldTitle.trim();
        String to = newTitle.trim();
        if (from.isEmpty() || to.isEmpty() || from.equals(to)) {
            return markdown;
        }
        Pattern pattern = Pattern.compile(
                "\\[\\[\\s*" + Pattern.quote(from) + "\\s*]]",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        return pattern.matcher(markdown)
                .replaceAll(Matcher.quoteReplacement("[[" + to + "]]"));
    }

    /** First occurrence of each video id, in document order. */
    public static List<YoutubeUrl> youtubeUrls(String markdown) {
        List<YoutubeUrl> out = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return out;
        }
        Map<String, YoutubeUrl> unique = new LinkedHashMap<>();
        Matcher matcher = YOUTUBE.matcher(markdown);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!unique.containsKey(id)) {
                unique.put(id, new YoutubeUrl(matcher.group(), id));
            }
        }
        out.addAll(unique.values());
        return out;
    }
}
