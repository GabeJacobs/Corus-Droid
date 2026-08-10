package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [SpotifyContentUri], the rule that decides whether something
 * Spotify reports over App Remote could ever have come from Corus.
 *
 * Bug: play a song on Corus, switch to Spotify, start a podcast. The reconcile
 * treated the episode URI like a misrouted skip ("this URI isn't in the feed
 * queue → force-advance"), paused it via the fast-path guard, and played its own
 * next track on top. There are no podcasts on Corus, so a `spotify:episode:` can
 * only ever be the user's own pick.
 */
class SpotifyContentUriTest {

    @Test
    fun `tracks are the only thing Corus can have started`() {
        assertEquals(
            SpotifyContentKind.TRACK,
            SpotifyContentUri.kindOf("spotify:track:4cOdK2wGLETKBW3PvgPWqT"),
        )
        assertFalse(SpotifyContentUri.isUserChosenNonCorusContent("spotify:track:4cOdK2wGLETKBW3PvgPWqT"))
    }

    @Test
    fun `podcast episodes are the user's own pick`() {
        assertEquals(
            SpotifyContentKind.FOREIGN,
            SpotifyContentUri.kindOf("spotify:episode:512ojhOuo1ktJprKbVcKyQ"),
        )
        assertTrue(SpotifyContentUri.isUserChosenNonCorusContent("spotify:episode:512ojhOuo1ktJprKbVcKyQ"))
    }

    @Test
    fun `audiobook chapters, shows and local files are the user's own pick too`() {
        listOf(
            "spotify:chapter:0Q86acNRm6V9GYx55SXKwf",
            "spotify:show:5CfCWKI5pZ28U0uOzXkDHe",
            "spotify:local:Artist:Album:Title:213",
        ).forEach { uri ->
            assertTrue("$uri should be foreign", SpotifyContentUri.isUserChosenNonCorusContent(uri))
        }
    }

    @Test
    fun `Spotify's own ad interstitial is neither ours nor a user pick`() {
        // Relinquishing on an ad would drop the session mid-feed, so ads must not
        // read as foreign content — they get no verdict of their own.
        assertEquals(SpotifyContentKind.AD, SpotifyContentUri.kindOf("spotify:ad:1234567890abcdef"))
        assertFalse(SpotifyContentUri.isUserChosenNonCorusContent("spotify:ad:1234567890abcdef"))
    }

    @Test
    fun `empty and malformed URIs give no verdict`() {
        listOf(null, "", "   ", "spotify:", "spotify:track:", "not-a-uri", "https://open.spotify.com/track/x")
            .forEach { uri ->
                assertEquals("$uri should be unknown", SpotifyContentKind.UNKNOWN, SpotifyContentUri.kindOf(uri))
                assertFalse(SpotifyContentUri.isUserChosenNonCorusContent(uri))
            }
    }

    @Test
    fun `case and whitespace don't change the verdict`() {
        assertEquals(
            SpotifyContentKind.TRACK,
            SpotifyContentUri.kindOf("  Spotify:Track:4cOdK2wGLETKBW3PvgPWqT  "),
        )
        assertTrue(SpotifyContentUri.isUserChosenNonCorusContent(" SPOTIFY:EPISODE:512ojhOuo1ktJprKbVcKyQ "))
    }

    @Test
    fun `track URI matching ignores case and compares track ids`() {
        val a = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
        val b = "Spotify:Track:4cOdK2wGLETKBW3PvgPWqT"
        assertTrue(SpotifyContentUri.trackUrisMatch(a, b))
        assertFalse(SpotifyContentUri.trackUrisMatch(a, "spotify:track:otherid0123456789012"))
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", SpotifyContentUri.trackId(a))
        assertEquals(null, SpotifyContentUri.trackId("spotify:episode:abc"))
    }

    @Test
    fun `failed handoff suppresses external adoption for the same URI only while the window is open`() {
        val failed = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
        val now = 1_000_000L
        val until = now + 30_000L
        assertTrue(
            SpotifyHandoffRecovery.shouldSuppressExternalAdoption(
                reportedUri = failed,
                failedHandoffUri = failed,
                suppressUntilMs = until,
                nowMs = now,
            ),
        )
        assertFalse(
            SpotifyHandoffRecovery.shouldSuppressExternalAdoption(
                reportedUri = "spotify:track:otherid0123456789012",
                failedHandoffUri = failed,
                suppressUntilMs = until,
                nowMs = now,
            ),
        )
        assertFalse(
            SpotifyHandoffRecovery.shouldSuppressExternalAdoption(
                reportedUri = failed,
                failedHandoffUri = failed,
                suppressUntilMs = until,
                nowMs = until + 1,
            ),
        )
        assertFalse(
            SpotifyHandoffRecovery.shouldSuppressExternalAdoption(
                reportedUri = failed,
                failedHandoffUri = null,
                suppressUntilMs = until,
                nowMs = now,
            ),
        )
    }
}
