package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The backend (`generateHashtagPlaylist` Cloud Function) does exact key
 * matching on the request payload — a silent rename (e.g. `supportsGating`
 * instead of `supportsPlaylistGating`) would not crash, it would just drop the
 * paywall or regress the export. Pin the wire shape for both the Spotify path
 * and the client-side (TIDAL) tracks path.
 */
class HashtagPlaylistPayloadTest {

    @Test
    fun `spotify path sends hashtag and gating flag only`() {
        val payload = CloudFunctionsDataSource.hashtagPlaylistPayload(
            hashtag = "indierock", fullExport = false, appleMusicTracks = false,
        )
        assertEquals(
            mapOf<String, Any>("hashtag" to "indierock", "supportsPlaylistGating" to true),
            payload,
        )
    }

    @Test
    fun `tracks path adds the appleMusicTracks flag`() {
        val payload = CloudFunctionsDataSource.hashtagPlaylistPayload(
            hashtag = "indierock", fullExport = false, appleMusicTracks = true,
        )
        assertEquals(
            mapOf<String, Any>(
                "hashtag" to "indierock",
                "supportsPlaylistGating" to true,
                "appleMusicTracks" to true,
            ),
            payload,
        )
    }

    @Test
    fun `fullExport attaches only when requested`() {
        val payload = CloudFunctionsDataSource.hashtagPlaylistPayload(
            hashtag = "indierock", fullExport = true, appleMusicTracks = false,
        )
        assertEquals(
            mapOf<String, Any>(
                "hashtag" to "indierock",
                "supportsPlaylistGating" to true,
                "fullExport" to true,
            ),
            payload,
        )
    }
}
