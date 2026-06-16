package fm.corus.android.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates inline trailer playback across the feed so that:
 *   1. At most one trailer plays at a time (starting a new one stops the other).
 *   2. Starting a trailer pauses music, and starting music stops the trailer.
 *
 * A plain object (not a Hilt singleton) so `PostCard` can reach it from any
 * screen without threading dependencies through. [NowPlayingManager] registers
 * [pauseMusic] on construction and calls [stopAll] whenever playback starts.
 *
 * Note: our inline player is a minimal YouTube WebView with no JS bridge, so
 * "stopping" a trailer means unmounting it (returning to the poster) rather than
 * freezing on the current frame. Music, which we control directly, truly pauses.
 */
object TrailerPlaybackCoordinator {
    private val _activePostId = MutableStateFlow<String?>(null)

    /** The post whose trailer is currently playing inline, if any. */
    val activePostId: StateFlow<String?> = _activePostId.asStateFlow()

    /** Pauses music. Registered by [NowPlayingManager] so the coordinator can
     *  keep trailers and music mutually exclusive without a direct dependency. */
    var pauseMusic: (() -> Unit)? = null

    /** Start a trailer for [postId]: claims the single slot (stopping any other
     *  trailer) and pauses music so the two never play over each other. */
    fun play(postId: String) {
        _activePostId.value = postId
        pauseMusic?.invoke()
    }

    /** Stop the trailer for [postId], but only if it's the one currently active. */
    fun stop(postId: String) {
        if (_activePostId.value == postId) _activePostId.value = null
    }

    /** Stop whatever trailer is playing. Called when music starts. */
    fun stopAll() {
        _activePostId.value = null
    }
}
