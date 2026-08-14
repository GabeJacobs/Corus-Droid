package fm.corus.android.domain

/**
 * Cold-start rules for App Remote when the Spotify app is not listening.
 * A hung silent connect means further silent retries will also hang; wake
 * Spotify (interactive auth) instead. After that wake, connect needs its
 * own budget — auth must not share the play timeout.
 */
internal object SpotifyConnectWake {
    const val POST_AUTH_CONNECT_TIMEOUT_MS = 20_000L
    const val POST_AUTH_CONNECT_MAX_ATTEMPTS = 3

    fun shouldAbandonSilentRetries(error: Exception): Boolean {
        val msg = error.message?.lowercase().orEmpty()
        return msg.contains("timed out connecting")
    }
}
