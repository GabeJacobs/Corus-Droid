package fm.corus.android.data.repository

import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifies the SoundCloud gating gets forwarded to the Cloud Function. The
 * UI layer flips `includeSoundCloud` from the `soundcloud_enabled` Remote
 * Config flag — make sure neither path silently re-enables SoundCloud.
 */
class MusicSearchRepositoryTest {

    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val repo = MusicSearchRepository(cloudFunctions)

    private suspend fun stubSearch(response: Map<String, Any?>) {
        whenever(
            cloudFunctions.searchSongs(any(), any(), any(), any(), any(), any(), any(), any(), any()),
        ).thenReturn(response)
    }

    private val emptyResponse =
        mapOf("tracks" to emptyList<Map<String, Any?>>(), "hasMore" to false)

    @Test
    fun `default search excludes SoundCloud`(): Unit = runBlocking {
        stubSearch(emptyResponse)

        repo.search("test")

        verify(cloudFunctions).searchSongs(
            query = eq("test"),
            offset = eq(0),
            limit = eq(20),
            market = any(),
            includeSoundCloud = eq(false),
            includeArtists = eq(false),
            includeAlbums = eq(false),
            albumsMatchArtist = eq(false),
            collapse = eq("recording"),
        )
    }

    @Test
    fun `passing includeSoundCloud true forwards through`(): Unit = runBlocking {
        stubSearch(emptyResponse)

        repo.search("test", includeSoundCloud = true)

        verify(cloudFunctions).searchSongs(
            query = eq("test"),
            offset = eq(0),
            limit = eq(20),
            market = any(),
            includeSoundCloud = eq(true),
            includeArtists = eq(false),
            includeAlbums = eq(false),
            albumsMatchArtist = eq(false),
            collapse = eq("recording"),
        )
    }

    @Test
    fun `albumsMatchArtist forwards to the callable`(): Unit = runBlocking {
        stubSearch(emptyResponse)

        repo.search("arcade fire", includeAlbums = true, albumsMatchArtist = true)

        verify(cloudFunctions).searchSongs(
            query = eq("arcade fire"),
            offset = eq(0),
            limit = eq(20),
            market = any(),
            includeSoundCloud = eq(false),
            includeArtists = eq(false),
            includeAlbums = eq(true),
            albumsMatchArtist = eq(true),
            collapse = eq("recording"),
        )
    }

    @Test
    fun `songsFirst maps from the response`(): Unit = runBlocking {
        stubSearch(
            mapOf(
                "tracks" to emptyList<Map<String, Any?>>(),
                "hasMore" to false,
                "songsFirst" to true,
            ),
        )

        assertTrue(repo.search("ocean breathes salty").songsFirst)
    }

    @Test
    fun `songsFirst defaults to false when the response omits it`(): Unit = runBlocking {
        stubSearch(emptyResponse)

        assertFalse(repo.search("test").songsFirst)
    }
}
