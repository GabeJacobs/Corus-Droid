package fm.corus.android.domain

/** Called under SpotifyPlaybackService's guard lock. Uses monotonic milliseconds. */
internal class LiveSpotifyPreviewGuard {
    data class Selection(val uri: String, val resume: Boolean, val generation: Long)
    private data class Pending(val uri: String, val at: Long)
    var backgrounded = false
    var allowed = false
    var suspended = false
        private set
    private var generation = 0L
    private var expected: String? = null
    private var revision: Long? = null
    private var armedAt = 0L
    private var contextConfirmed = false
    private var context: String? = null
    private var contextAt = 0L
    private var sampleAt: Long? = null
    private var position = 0L
    private var duration = 0L
    private var playing = false
    private var pending: Pending? = null
    private var selected: Selection? = null

    fun clear() {
        generation++
        expected = null
        revision = null
        allowed = false
        suspended = false
        pending = null
        selected = null
        sampleAt = null
        contextConfirmed = false
        context = null
    }

    fun follow(uri: String, revision: Long, now: Long, currentContext: String?) {
        if (expected == uri && this.revision == revision) return
        clear()
        expected = uri
        this.revision = revision
        armedAt = now
        context = currentContext
        contextConfirmed = currentContext?.let { SpotifyPlaybackService.spotifyURIsMatch(it, uri) } == true
    }

    fun observe(position: Long, duration: Long, paused: Boolean, now: Long) {
        if (suspended) return
        this.position = position
        this.duration = duration
        playing = !paused
        sampleAt = now
    }

    private fun eligible(now: Long, unlocked: Boolean, inFlight: Boolean): Boolean {
        val sampled = sampleAt ?: return false
        if (!allowed || !backgrounded || !unlocked || inFlight || suspended || !playing ||
            !contextConfirmed || now - armedAt < 8_000 || now - sampled !in 0..3_000
        ) return false
        val projected = position + now - sampled
        return projected >= 5_000 && duration - projected > 15_000
    }

    private fun changedContext(now: Long): Boolean = context?.let {
        it.isNotBlank() && expected?.let { expected -> !SpotifyPlaybackService.spotifyURIsMatch(it, expected) } == true &&
            now - contextAt in 0..1_500
    } == true

    fun mismatch(uri: String, paused: Boolean, now: Long, unlocked: Boolean, inFlight: Boolean): Selection? {
        if (paused || !uri.startsWith("spotify:track:") || !eligible(now, unlocked, inFlight)) return null
        if (changedContext(now)) return select(uri, resume = false)
        // Keep the immediate pause until the separately delivered Spotify
        // context confirms this was a deliberate selection. Never extend this
        // window on repeated callbacks or pause acknowledgements.
        if (pending?.uri != uri) pending = Pending(uri, now)
        return null
    }

    fun context(uri: String, now: Long, unlocked: Boolean, inFlight: Boolean): Selection? {
        context = uri
        contextAt = now
        if (expected?.let { SpotifyPlaybackService.spotifyURIsMatch(uri, it) } == true) contextConfirmed = true
        val candidate = pending ?: return null
        if (now - candidate.at !in 0..1_500) { pending = null; return null }
        if (!eligible(now, unlocked, inFlight) || !changedContext(now)) return null
        return select(candidate.uri, resume = true)
    }

    private fun select(uri: String, resume: Boolean): Selection {
        suspended = true
        pending = null
        return Selection(uri, resume, generation).also { selected = it }
    }

    fun isCurrent(selection: Selection) = suspended && selected == selection
    fun cancel(selection: Selection) { if (isCurrent(selection)) clear() }
}
