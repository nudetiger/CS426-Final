package com.cs426.learningmocha.data.local

import com.cs426.learningmocha.data.local.entity.ResourceItem
import com.cs426.learningmocha.data.local.entity.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader's reference list. Saving a post used to rebuild the `resources` table from the body,
 * which deleted every reference added by hand — these cases lock the read-time merge that
 * replaced it.
 */
class InlineResourcesTest {

    private fun stored(id: Long, type: ResourceType, title: String, url: String) =
        ResourceItem(id = id, postId = 7, type = type, title = title, url = url)

    @Test
    fun keepsStoredReferencesAndAddsInlineVideos() {
        val markdown = "Watch https://www.youtube.com/watch?v=rfscVS0vtbw for the basics."
        val merged = InlineResources.merge(
            postId = 7,
            content = markdown,
            stored = listOf(stored(1, ResourceType.ARTICLE, "GC handbook", "https://example.com/gc")),
        )

        assertEquals(2, merged.size)
        assertEquals("GC handbook", merged[0].title)
        assertEquals(ResourceType.YOUTUBE, merged[1].type)
        assertEquals("https://www.youtube.com/watch?v=rfscVS0vtbw", merged[1].url)
    }

    @Test
    fun derivedItemsCarryNoRowIdSoTheUiCanHideDelete() {
        val merged = InlineResources.merge(7, "https://youtu.be/dQw4w9WgXcQ", emptyList())
        assertEquals(1, merged.size)
        assertEquals(0L, merged[0].id)
        assertEquals(7L, merged[0].postId)
    }

    // A stored row and the same URL in the body must not render as two cards.
    @Test
    fun aStoredVideoIsNotListedTwice() {
        val url = "https://www.youtube.com/watch?v=rfscVS0vtbw"
        val merged = InlineResources.merge(
            postId = 7,
            content = "Intro: $url",
            stored = listOf(stored(1, ResourceType.YOUTUBE, "JVM garbage collection", url)),
        )

        assertEquals(1, merged.size)
        assertEquals("JVM garbage collection", merged[0].title)
        assertEquals(1L, merged[0].id)
    }

    // Existing databases still hold rows the old save pipeline derived; the same video written as
    // a short link must not resurrect as a second card.
    @Test
    fun matchesAStoredRowByVideoIdAcrossUrlForms() {
        val merged = InlineResources.merge(
            postId = 7,
            content = "https://youtu.be/rfscVS0vtbw",
            stored = listOf(
                stored(1, ResourceType.YOUTUBE, "YouTube", "https://www.youtube.com/watch?v=rfscVS0vtbw"),
            ),
        )

        assertEquals(1, merged.size)
    }

    @Test
    fun emptyBodyAndNoRowsGiveNoReferences() {
        assertTrue(InlineResources.merge(7, "", emptyList()).isEmpty())
    }
}
