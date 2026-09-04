package com.cs426.learningmocha.data.local

import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.ResourceType
import com.cs426.learningmocha.util.MarkdownLinkParser

/**
 * A post's reference list = the rows someone added on purpose + the YouTube URLs sitting in the
 * markdown right now.
 *
 * The inline half is derived on every read rather than stored, because the stored half used to be
 * rebuilt from the body on every save — which silently deleted any reference the user or the
 * assistant had added. Derived items carry `id = 0` so the UI can tell them apart: the way to
 * remove one is to edit the markdown.
 */
object InlineResources {

    /** Stored rows keep their position and their title; a URL is never listed twice. */
    fun merge(postId: Long, content: String, stored: List<ResourceItem>): List<ResourceItem> {
        val derived = MarkdownLinkParser.youtubeUrls(content)
            .filter { url -> stored.none { it.url.contains(url.videoId) } }
            .map { url ->
                ResourceItem(
                    postId = postId,
                    type = ResourceType.YOUTUBE,
                    title = "YouTube",
                    url = url.url,
                )
            }
        return (stored + derived).distinctBy { it.url }
    }
}
