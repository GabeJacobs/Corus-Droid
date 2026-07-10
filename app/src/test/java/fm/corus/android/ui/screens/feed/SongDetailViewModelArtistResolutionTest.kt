package fm.corus.android.ui.screens.feed

import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * On-tap destination resolution (artist_pages_enabled). "Go to Artist"/"Go to
 * Album" always show; when the seed track reached us without a Spotify id,
 * [SongDetailViewModel.resolveDestinations] looks it up via
 * `resolveTrackDestinations` (server-cached by ISRC) and caches BOTH ids for
 * instant repeat taps. Replaces the old on-open exact-name fallback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SongDetailViewModelArtistResolutionTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var viewModel: SongDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cloudFunctions = mock()
        viewModel = SongDetailViewModel(
            postRepository = mock(),
            nowPlayingManager = mock(),
            analyticsService = mock(),
            commentEditedEvent = mock { on { events } doReturn kotlinx.coroutines.flow.MutableSharedFlow<fm.corus.android.domain.CommentEditedEvent.Payload>() },
            commentDeletedEvent = mock { on { events } doReturn kotlinx.coroutines.flow.MutableSharedFlow<fm.corus.android.domain.CommentDeletedEvent.Payload>() },
            cloudFunctions = cloudFunctions,
            remoteConfigService = mock(),
            musicServicePreference = mock(),
            authRepository = mock { on { currentUserId } doReturn "me" },
            userRepository = mock(),
            messageRepository = mock(),
            context = mock(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `resolveDestinations publishes both ids and returns them on success`() =
        runTest(testDispatcher) {
            whenever(cloudFunctions.resolveTrackDestinations(any(), anyOrNull(), any(), any()))
                .doReturn(
                    CloudFunctionsDataSource.TrackDestinations(
                        artistIds = listOf("spotify-jn"),
                        albumId = "album-1",
                    )
                )

            val dest = viewModel.resolveDestinations(
                "am:1", "USABC1234567", "Sapokanikan", "Joanna Newsom",
            )

            assertEquals(listOf("spotify-jn"), dest.artistIds)
            assertEquals("album-1", dest.albumId)
            // Cached for instant repeat taps + used by the tappable lines/menu.
            assertEquals("spotify-jn", viewModel.resolvedArtistId.value)
            assertEquals("album-1", viewModel.resolvedAlbumId.value)
            // The HUD flag always clears when the resolve settles.
            assertEquals(false, viewModel.isResolvingDestination.value)
        }

    @Test
    fun `resolveDestinations on a miss leaves both cached ids null`() =
        runTest(testDispatcher) {
            whenever(cloudFunctions.resolveTrackDestinations(any(), anyOrNull(), any(), any()))
                .doReturn(CloudFunctionsDataSource.TrackDestinations(emptyList(), null))

            val dest = viewModel.resolveDestinations("am:1", null, "Sapokanikan", "Joanna Newsom")

            assertNull(dest.albumId)
            assertNull(viewModel.resolvedArtistId.value)
            assertNull(viewModel.resolvedAlbumId.value)
            assertEquals(false, viewModel.isResolvingDestination.value)
        }
}
