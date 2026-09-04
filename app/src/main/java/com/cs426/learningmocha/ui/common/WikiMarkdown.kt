package com.cs426.learningmocha.ui.common

import android.net.Uri
import com.cs426.learningmocha.util.MarkdownLinkParser

object WikiMarkdown {
    const val POST_PREFIX = "mocha://post/"
    const val MISSING_PREFIX = "mocha://missing/"

    fun rewrite(markdown: String, titleToId: Map<String, Long>): String {
        val links = MarkdownLinkParser.wikiLinks(markdown)
        if (links.isEmpty()) return markdown
        val sb = StringBuilder(markdown)
        for (i in links.indices.reversed()) {
            val link = links[i]
            val id = titleToId[link.title.lowercase()]
            val dest = if (id != null) {
                POST_PREFIX + id
            } else {
                MISSING_PREFIX + Uri.encode(link.title)
            }
            sb.replace(link.start, link.end, "[${link.title}]($dest)")
        }
        return sb.toString()
    }
}
