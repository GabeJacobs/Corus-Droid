package fm.corus.android.data.repository

import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `default search excludes SoundCloud`() = runBlocking {
        whenever(cloudFunctions.searchSongs(any(), any(), any(), any(), any()))
            .thenReturn(mapOf("tracks" to emptyList<Map<String, Any?>>(), "hasMore" to false))

        repo.search("test")

        verify(cloudFunctions).searchSongs(
            query = eq("test"),
            offset = eq(0),
            limit = eq(20),
            market = any(),
            includeSoundCloud = eq(false),
        )
    }

    @Test
    fun `passing includeSoundCloud true forwards through`() = runBlocking {
        whenever(cloudFunctions.searchSongs(any(), any(), any(), any(), any()))
            .thenReturn(mapOf("tracks" to emptyList<Map<String, Any?>>(), "hasMore" to false))

        repo.search("test", includeSoundCloud = true)

        verify(cloudFunctions).searchSongs(
            query = eq("test"),
            offset = eq(0),
            limit = eq(20),
            market = any(),
            includeSoundCloud = eq(true),
        )
    }
}
