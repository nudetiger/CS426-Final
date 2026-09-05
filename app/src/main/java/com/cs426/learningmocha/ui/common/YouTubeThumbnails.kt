package com.cs426.learningmocha.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Poster frames for the YouTube references listed under a post.
 *
 * A row that says "YouTube · youtube.com" tells the reader nothing they did not already know;
 * the frame tells them which video it is. That is the whole feature, so it is deliberately not
 * a general image-loading library: one host, one image size, no transformations, no new
 * dependency — OkHttp is already here for the AI gateway.
 *
 * Two caches, for two different problems. The [LruCache] means scrolling back to a post does
 * not re-decode anything, and OkHttp's disk cache means it does not re-download anything —
 * including on a plane, which matters for an app whose whole promise is that the library works
 * offline. Neither cache holds user content: these are public thumbnails keyed by video id.
 */
object YouTubeThumbnails {

    /** An eighth of the heap, the usual starting point for a bitmap cache. */
    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(2048),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    @Volatile
    private var http: OkHttpClient? = null

    /**
     * Shows the thumbnail for [videoId] in [image], or calls [onMissing] when there is none to
     * show — no network, a video that has been taken down, a malformed id. The caller decides
     * what to do about that, because "no thumbnail" is a layout question (the reader falls back
     * to the play glyph it used before) rather than something this loader can answer.
     *
     * [scope] should be the view's lifecycle scope: when the screen goes away the fetch is
     * cancelled with it, and nothing writes into a detached ImageView.
     */
    fun into(
        image: ImageView,
        videoId: String,
        scope: CoroutineScope,
        onMissing: () -> Unit,
    ) {
        val cached = memory.get(videoId)
        if (cached != null) {
            image.setImageBitmap(cached)
            return
        }
        // Cleared first: the card may be a recycled view still holding another video's frame.
        image.setImageDrawable(null)
        val context = image.context.applicationContext
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { fetch(context, videoId) }
            if (bitmap == null) {
                onMissing()
            } else {
                memory.put(videoId, bitmap)
                image.setImageBitmap(bitmap)
            }
        }
    }

    /**
     * `mqdefault` (320x180) is generated for every public video; `hqdefault` is the fallback
     * for the handful of old uploads that predate it. Both are ordinary static files — no API
     * key, no request that says anything about the user beyond the video id already stored in
     * their own library.
     */
    private fun fetch(context: Context, videoId: String): Bitmap? {
        val client = client(context)
        for (name in SIZES) {
            val request = Request.Builder().url("$HOST$videoId/$name").build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bytes = response.body?.bytes() ?: return@use
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
                }
            } catch (error: IOException) {
                // Offline, or the host is unreachable. The caller's fallback covers it, and a
                // reference the user cannot see a frame for is not worth an error message.
                return null
            }
        }
        return null
    }

    private fun client(context: Context): OkHttpClient = http ?: synchronized(this) {
        http ?: OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, CACHE_DIR), CACHE_BYTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
            .also { http = it }
    }

    private const val HOST = "https://i.ytimg.com/vi/"
    private val SIZES = listOf("mqdefault.jpg", "hqdefault.jpg")
    private const val CACHE_DIR = "youtube-thumbnails"
    private const val CACHE_BYTES = 8L * 1024 * 1024
}
