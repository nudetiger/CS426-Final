package com.cs426.learningmocha.ui.reader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.SheetYoutubeBinding
import com.cs426.learningmocha.util.MarkdownLinkParser
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Plays one YouTube video inline through the cookie-free embed host, so watching a reference
 * never leaves the reader. The WebView is deliberately locked down: no JavaScript bridge, no
 * file or content access, and navigation is restricted to YouTube's own hosts — anything else
 * (an end-card link to another site, say) is handed to the browser instead.
 */
class YouTubePlayerSheet : BottomSheetDialogFragment() {

    private var binding: SheetYoutubeBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = SheetYoutubeBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        val args = requireArguments()
        val videoId = args.getString(ARG_VIDEO_ID).orEmpty()
        val url = args.getString(ARG_URL).orEmpty()
        val title = args.getString(ARG_TITLE).orEmpty()

        b.youtubeSheetTitle.text = title.ifBlank { getString(R.string.youtube_sheet_title) }
        b.youtubeSheetOpen.setOnClickListener {
            openExternally(url.ifBlank { watchUrl(videoId) })
        }
        keepSixteenByNine(b.youtubeSheetPlayer)
        configurePlayer(b.youtubeSheetPlayer)
        // Loading the embed URL directly sends no Referer, and YouTube answers with
        // "Video player configuration error (150/153)". Wrapping it in a one-line
        // document served from a YouTube base URL gives the player the origin it
        // requires, which is what the IFrame API documents.
        b.youtubeSheetPlayer.loadDataWithBaseURL(
            EMBED_BASE,
            embedDocument(videoId),
            "text/html",
            "utf-8",
            null,
        )
    }

    override fun onDestroyView() {
        binding?.youtubeSheetPlayer?.let { player ->
            player.stopLoading()
            player.loadUrl(BLANK)
            player.onPause()
            (player.parent as? ViewGroup)?.removeView(player)
            player.destroy()
        }
        binding = null
        super.onDestroyView()
    }

    /**
     * The embed refuses to size itself, so the WebView is measured to the classic video ratio
     * once its width is known. Re-running on every layout keeps it right after a rotation.
     */
    private fun keepSixteenByNine(player: WebView) {
        player.addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
            val target = (right - left) * 9 / 16
            if (target > 0 && view.height != target) {
                view.updateLayoutParams { height = target }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurePlayer(player: WebView) {
        player.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
        }
        player.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val host = request.url.host
                if (host != null && isYouTubeHost(host)) return false
                openExternally(request.url.toString())
                return true
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) showUnavailable()
            }
        }
    }

    private fun showUnavailable() {
        val b = binding ?: return
        b.youtubeSheetPlayer.isVisible = false
        b.youtubeSheetMessage.isVisible = true
    }

    private fun openExternally(url: String) {
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.reader_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "youtube-player"
        private const val ARG_VIDEO_ID = "videoId"
        private const val ARG_URL = "url"
        private const val ARG_TITLE = "title"
        private const val BLANK = "about:blank"

        private val ALLOWED_HOSTS = listOf(
            "youtube-nocookie.com",
            "youtube.com",
            "ytimg.com",
            "googlevideo.com",
            "google.com",
        )

        /** Null when the URL carries no 11-character video id (a channel or playlist link). */
        fun videoId(url: String): String? =
            MarkdownLinkParser.youtubeUrls(url).firstOrNull()?.videoId

        fun show(manager: FragmentManager, videoId: String, url: String, title: String) {
            val sheet = YouTubePlayerSheet()
            sheet.arguments = bundleOf(
                ARG_VIDEO_ID to videoId,
                ARG_URL to url,
                ARG_TITLE to title,
            )
            sheet.show(manager, TAG)
        }

        private const val EMBED_BASE = "https://www.youtube.com"

        private fun embedUrl(videoId: String) =
            "https://www.youtube-nocookie.com/embed/$videoId" +
                "?playsinline=1&rel=0&modestbranding=1"

        /** A minimal full-bleed page around the embed; the WebView itself is sized 16:9. */
        private fun embedDocument(videoId: String) =
            """
            <!doctype html><html><head><meta name="viewport"
              content="width=device-width, initial-scale=1, user-scalable=no">
            <style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}
            iframe{border:0;display:block;width:100%;height:100%}</style></head>
            <body><iframe src="${embedUrl(videoId)}" allowfullscreen
              allow="autoplay; encrypted-media; picture-in-picture"></iframe></body></html>
            """.trimIndent()

        private fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

        private fun isYouTubeHost(host: String): Boolean = ALLOWED_HOSTS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }
}
