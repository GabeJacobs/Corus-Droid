package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.model.TrendingWindow
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
        val first = mapOf(TrendingWindow.MONTH to listOf(trendingSong("a")))
        val second = mapOf(TrendingWindow.MONTH to listOf(trendingSong("b")))
        whenever(dataSource.fetchTrendingSongsByWindow(20))
            .thenReturn(first)
            .thenReturn(second)

        assertEquals(first[TrendingWindow.MONTH], repo.fetchTrendingSongs(TrendingWindow.MONTH))
        // Without clear, TTL would serve `first` again.
        assertEquals(first[TrendingWindow.MONTH], repo.fetchTrendingSongs(TrendingWindow.MONTH))

        repo.clearCaches()

        assertEquals(second[TrendingWindow.MONTH], repo.fetchTrendingSongs(TrendingWindow.MONTH))
        verify(dataSource, org.mockito.kotlin.times(2)).fetchTrendingSongsByWindow(20)
    }

    @Test
    fun `second fetch within TTL returns cached value without hitting source`() = runTest {
        val songs = mapOf(TrendingWindow.MONTH to listOf(trendingSong("a")))
        whenever(dataSource.fetchTrendingSongsByWindow(20)).thenReturn(songs)

        repo.fetchTrendingSongs()
        repo.fetchTrendingSongs()

        verify(dataSource, org.mockito.kotlin.times(1)).fetchTrendingSongsByWindow(20)
    }

    // One Firestore read populates all three windows — switching the
    // selected window after that should be served from cache, not refetch.
    @Test
    fun `switching window after first fetch is served from cache`() = runTest {
        val all = mapOf(
            TrendingWindow.WEEK to listOf(trendingSong("w")),
            TrendingWindow.MONTH to listOf(trendingSong("m")),
            TrendingWindow.YEAR to listOf(trendingSong("y")),
        )
        whenever(dataSource.fetchTrendingSongsByWindow(20)).thenReturn(all)

        assertEquals(all[TrendingWindow.MONTH], repo.fetchTrendingSongs(TrendingWindow.MONTH))
        assertEquals(all[TrendingWindow.WEEK], repo.fetchTrendingSongs(TrendingWindow.WEEK))
        assertEquals(all[TrendingWindow.YEAR], repo.fetchTrendingSongs(TrendingWindow.YEAR))

        verify(dataSource, org.mockito.kotlin.times(1)).fetchTrendingSongsByWindow(20)
    }

    @Test
    fun `trending albums window switch is served from cache`() = runTest {
        val all = mapOf(
            TrendingWindow.WEEK to listOf(trendingAlbum("w")),
            TrendingWindow.MONTH to listOf(trendingAlbum("m")),
            TrendingWindow.YEAR to listOf(trendingAlbum("y")),
        )
        whenever(dataSource.fetchTrendingAlbumsByWindow(20)).thenReturn(all)

        assertEquals(all[TrendingWindow.MONTH], repo.fetchTrendingAlbums(TrendingWindow.MONTH))
        assertEquals(all[TrendingWindow.WEEK], repo.fetchTrendingAlbums(TrendingWindow.WEEK))
        verify(dataSource, org.mockito.kotlin.times(1)).fetchTrendingAlbumsByWindow(20)
    }

    @Test
    fun `new release albums stay cached until clearCaches`() = runTest {
        val first = listOf(trendingAlbum("a"))
        val second = listOf(trendingAlbum("b"))
        whenever(dataSource.fetchNewReleaseAlbums(20))
            .thenReturn(first)
            .thenReturn(second)

        assertEquals(first, repo.fetchNewReleaseAlbums())
        assertEquals(first, repo.fetchNewReleaseAlbums())
        repo.clearCaches()
        assertEquals(second, repo.fetchNewReleaseAlbums())
        verify(dataSource, org.mockito.kotlin.times(2)).fetchNewReleaseAlbums(20)
    }

    @Test
    fun `new release movies stay cached until clearCaches`() = runTest {
        val first = listOf(trendingMovie("a"))
        val second = listOf(trendingMovie("b"))
        whenever(dataSource.fetchNewReleaseMovies(20))
            .thenReturn(first)
            .thenReturn(second)

        assertEquals(first, repo.fetchNewReleaseMovies())
        assertEquals(first, repo.fetchNewReleaseMovies())
        repo.clearCaches()
        assertEquals(second, repo.fetchNewReleaseMovies())
        verify(dataSource, org.mockito.kotlin.times(2)).fetchNewReleaseMovies(20)
    }

    @Test
    fun `trending directors window switch is served from cache`() = runTest {
        val all = mapOf(
            TrendingWindow.WEEK to listOf(trendingDirector("w")),
            TrendingWindow.MONTH to listOf(trendingDirector("m")),
            TrendingWindow.YEAR to listOf(trendingDirector("y")),
        )
        whenever(dataSource.fetchTrendingDirectorsByWindow(20)).thenReturn(all)

        assertEquals(all[TrendingWindow.MONTH], repo.fetchTrendingDirectors(TrendingWindow.MONTH))
        assertEquals(all[TrendingWindow.WEEK], repo.fetchTrendingDirectors(TrendingWindow.WEEK))
        verify(dataSource, org.mockito.kotlin.times(1)).fetchTrendingDirectorsByWindow(20)
    }

    private fun trendingDirector(id: String) = fm.corus.android.data.model.TrendingDirector(
        id = id,
        rank = 1,
        directorId = id,
        directorName = id,
        cymbalCount = 1,
    )

    private fun trendingMovie(id: String) = fm.corus.android.data.model.TrendingMovie(
        id = id,
        rank = 1,
        movieId = id,
        movieTitle = id,
        directorName = "Director",
        releaseYear = "2026",
        cymbalCount = 1,
    )

    private fun trendingAlbum(id: String) = fm.corus.android.data.model.TrendingAlbum(
        id = id,
        rank = 1,
        albumId = id,
        albumName = id,
        artistName = "Artist",
        cymbalCount = 1,
    )

    private fun trendingSong(id: String) = TrendingSong(
        id = id,
        rank = 1,
        track = CymbalTrack(id = id, name = id, artistName = "", albumName = ""),
        cymbalCount = 1,
    )
}
