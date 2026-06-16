package fm.corus.android.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * Plays a YouTube trailer inline via YouTube's official IFrame embed.
 *
 * We intentionally use the sanctioned embed player (not a raw stream) — pulling
 * the underlying video out of YouTube violates their ToS and breaks often. The
 * embed is hosted in a lightweight [WebView] configured for inline, autoplaying,
 * unmuted playback with the control bar hidden.
 *
 * Prototype scope: tap-to-play only. A card creates this lazily when the user
 * taps a poster, so the feed never pays the WebView cost on every film card.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun InlineYouTubePlayer(
    videoID: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    // Start with sound. Unmuted autoplay works here because we disable the
    // user-gesture requirement and the poster tap that mounts this is itself a
    // user gesture.
    muted: Boolean = false,
    // Hide YouTube's control bar by default for a cleaner in-feed look.
    showControls: Boolean = false,
    // Fired when the video finishes, so the card can drop back to the poster.
    onEnded: () -> Unit = {},
) {
    // Keep the latest callback without re-creating the WebView each recomposition.
    val currentOnEnded by rememberUpdatedState(onEnded)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                setBackgroundColor(android.graphics.Color.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                // JS calls back here when the IFrame player reports it ended.
                // @JavascriptInterface methods run on a binder thread, so hop to
                // the main thread before invoking Compose state.
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onEnded() {
                        Handler(Looper.getMainLooper()).post { currentOnEnded() }
                    }
                }, "AndroidTrailer")
                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    embedHTML(videoID, autoplay, muted, showControls),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        onRelease = { webView ->
            // Stop playback + free the WebView when the card scrolls off or the
            // user closes the trailer.
            webView.loadUrl("about:blank")
            webView.destroy()
        },
    )
}

/**
 * HTML hosting YouTube's IFrame Player API. We use the API (not a bare embed)
 * so we can listen for the ENDED state and auto-dismiss back to the poster.
 */
private fun embedHTML(videoID: String, autoplay: Boolean, muted: Boolean, showControls: Boolean): String {
    val autoplayFlag = if (autoplay) 1 else 0
    val muteFlag = if (muted) 1 else 0
    val controlsFlag = if (showControls) 1 else 0
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
          <style>
            * { margin: 0; padding: 0; }
            html, body { background: #000; height: 100%; overflow: hidden; }
            #player { position: absolute; inset: 0; width: 100%; height: 100%; }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            function onYouTubeIframeAPIReady() {
              new YT.Player('player', {
                videoId: '$videoID',
                host: 'https://www.youtube-nocookie.com',
                playerVars: {
                  autoplay: $autoplayFlag, mute: $muteFlag, controls: $controlsFlag,
                  playsinline: 1, rel: 0, modestbranding: 1
                },
                events: {
                  onStateChange: function(e) {
                    // YT.PlayerState.ENDED === 0
                    if (e.data === 0 && window.AndroidTrailer) {
                      window.AndroidTrailer.onEnded();
                    }
                  }
                }
              });
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Extracts the YouTube video ID from the trailer URLs we store
 * (always `https://www.youtube.com/watch?v=KEY`, occasionally `youtu.be/KEY`).
 */
fun youTubeVideoID(urlString: String?): String? {
    if (urlString.isNullOrBlank()) return null
    val uri = runCatching { Uri.parse(urlString) }.getOrNull() ?: return null

    uri.getQueryParameter("v")?.takeIf { it.isNotBlank() }?.let { return it }

    val last = uri.lastPathSegment
    val host = uri.host ?: ""
    if ((host.contains("youtu.be") || uri.path?.contains("/embed/") == true) && !last.isNullOrBlank()) {
        return last
    }
    return null
}
