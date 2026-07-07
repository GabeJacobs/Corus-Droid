package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Artist-line name-resolution fallback (artist_pages_enabled): with no artist
 * ids anywhere — no route hint AND no loaded post carries artistIds — the
 * ViewModel resolves the Spotify artist id by exact name after posts load, so
 * the song page's artist line is always tappable when the flag is on. The
 * fallback must never fire when the flag is off or when ids already exist,
 * keeping today's behavior byte-identical in those cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SongDetailViewModelArtistResolutionTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var postRepository: PostRepository
    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var remoteConfigService: RemoteConfigService
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: SongDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        postRepository = mock()
        cloudFunctions = mock()
        remoteConfigService = mock()
        authRepository = mock { on { currentUserId } doReturn "me" }
        viewModel = SongDetailViewModel(
            postRepository = postRepository,
            nowPlayingManager = mock(),
            analyticsService = mock(),
            commentEditedEvent = mock { on { events } doReturn kotlinx.coroutines.flow.MutableSharedFlow<fm.corus.android.domain.CommentEditedEvent.Payload>() },
            commentDeletedEvent = mock { on { events } doReturn kotlinx.coroutines.flow.MutableSharedFlow<fm.corus.android.domain.CommentDeletedEvent.Payload>() },
            cloudFunctions = cloudFunctions,
            remoteConfigService = remoteConfigService,
            musicServicePreference = mock(),
            authRepository = authRepository,
            userRepository = mock(),
            messageRepository = mock(),
            context = mock(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun post(id: String, artistIds: List<String> = emptyList(), artistName: String = "Joanna Newsom") =
        CymbalPost(
            id = id,
            user = CymbalUser(id = "u-$id", username = "user$id", displayName = "User $id"),
            track = CymbalTrack(
                id = "am:1",
                name = "Sapokanikan",
                artistName = artistName,
                artistIds = artistIds,
                albumName = "Divers",
            ),
        )

    private suspend fun stubPosts(vararg posts: CymbalPost) {
        whenever(
            postRepository.fetchSongPostsFromCloud(any(), anyOrNull(), anyOrNull(), anyOrNull(), any(), anyOrNull())
        ).doReturn(CloudFunctionsDataSource.SongPostsPage(posts.toList(), posts.size, null))
    }

    @Test
    fun `flag on with no ids anywhere - resolves by exact name and publishes the id`() =
        runTest(testDispatcher) {
            whenever(remoteConfigService.artistPagesEnabled).thenReturn(true)
            stubPosts(post("p1"))
            whenever(cloudFunctions.resolveArtistIdByName(eq("Joanna Newsom"))).doReturn("spotify-jn")

            viewModel.loadSongPosts("am:1", artistName = "Joanna Newsom")
            advanceUntilIdle()

            verify(cloudFunctions).resolveArtistIdByName(eq("Joanna Newsom"))
            assertEquals("spotify-jn", viewModel.resolvedArtistId.value)
        }

    @Test
    fun `flag off - never resolves`() = runTest(testDispatcher) {
        whenever(remoteConfigService.artistPagesEnabled).thenReturn(false)
        stubPosts(post("p1"))

        viewModel.loadSongPosts("am:1", artistName = "Joanna Newsom")
        advanceUntilIdle()

        verify(cloudFunctions, never()).resolveArtistIdByName(any())
        assertNull(viewModel.resolvedArtistId.value)
    }

    @Test
    fun `route artist id present - never resolves`() = runTest(testDispatcher) {
        whenever(remoteConfigService.artistPagesEnabled).thenReturn(true)
        stubPosts(post("p1"))

        viewModel.loadSongPosts("t1", artistName = "Joanna Newsom", routeArtistId = "route-id")
        advanceUntilIdle()

        verify(cloudFunctions, never()).resolveArtistIdByName(any())
        assertNull(viewModel.resolvedArtistId.value)
    }

    @Test
    fun `loaded post carries artist ids - never resolves`() = runTest(testDispatcher) {
        whenever(remoteConfigService.artistPagesEnabled).thenReturn(true)
        stubPosts(post("p1", artistIds = listOf("spotify-jn")))

        viewModel.loadSongPosts("t1", artistName = "Joanna Newsom")
        advanceUntilIdle()

        verify(cloudFunctions, never()).resolveArtistIdByName(any())
        assertNull(viewModel.resolvedArtistId.value)
    }

    @Test
    fun `no route artist name - falls back to the first loaded post's name`() =
        runTest(testDispatcher) {
            whenever(remoteConfigService.artistPagesEnabled).thenReturn(true)
            stubPosts(post("p1", artistName = "Tool"))
            whenever(cloudFunctions.resolveArtistIdByName(eq("Tool"))).doReturn("spotify-tool")

            viewModel.loadSongPosts("am:2")
            advanceUntilIdle()

            verify(cloudFunctions).resolveArtistIdByName(eq("Tool"))
            assertEquals("spotify-tool", viewModel.resolvedArtistId.value)
        }

    @Test
    fun `no exact match - resolved id stays null so the line stays plain text`() =
        runTest(testDispatcher) {
            whenever(remoteConfigService.artistPagesEnabled).thenReturn(true)
            stubPosts(post("p1"))
            whenever(cloudFunctions.resolveArtistIdByName(any())).doReturn(null)

            viewModel.loadSongPosts("am:1", artistName = "Joanna Newsom")
            advanceUntilIdle()

            assertNull(viewModel.resolvedArtistId.value)
        }
}
