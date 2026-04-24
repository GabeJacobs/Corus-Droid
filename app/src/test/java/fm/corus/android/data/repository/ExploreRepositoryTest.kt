package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreRepositoryTest {

    private val dataSource = mock<FirestoreDataSource>()
    private val repo = ExploreRepository(dataSource)

    // Regression for the "pull-to-refresh returned stale trending" bug: a user
    // saw a corrupted trending list, and pull-to-refresh kept returning the
    // same cached 5-min-TTL value. Explicit refresh now calls clearCaches().
    @Test
    fun `clearCaches forces next fetch to hit data source`() = runTest {
        val first = listOf(trendingSong("a"))
        val second = listOf(trendingSong("b"))
        whenever(dataSource.fetchTrendingSongs(20))
            .thenReturn(first)
            .thenReturn(second)

        assertEquals(first, repo.fetchTrendingSongs())
        // Without clear, TTL would serve `first` again.
        assertEquals(first, repo.fetchTrendingSongs())

        repo.clearCaches()

        assertEquals(second, repo.fetchTrendingSongs())
        verify(dataSource, org.mockito.kotlin.times(2)).fetchTrendingSongs(20)
    }

    @Test
    fun `second fetch within TTL returns cached value without hitting source`() = runTest {
        val songs = listOf(trendingSong("a"))
        whenever(dataSource.fetchTrendingSongs(20)).thenReturn(songs)

        repo.fetchTrendingSongs()
        repo.fetchTrendingSongs()

        verify(dataSource, org.mockito.kotlin.times(1)).fetchTrendingSongs(20)
    }

    private fun trendingSong(id: String) = TrendingSong(
        id = id,
        rank = 1,
        track = CymbalTrack(id = id, name = id, artistName = "", albumName = ""),
        cymbalCount = 1,
    )
}
