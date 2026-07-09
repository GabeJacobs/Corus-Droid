package fm.corus.android.ui.screens.compose

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.PostDraft
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.NetworkMonitor
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Regression test for the Android drafts editing-context leak.
 *
 * Bug: [ComposeViewModel.clearSelectionKeepingResults] — the return-to-picker
 * path used after Save/Discard-via-back and when changing selection — cleared
 * only the selected track/movie, leaving `editingDraftId` and
 * `resumedVoiceNoteURL` set. Picking a new song and saving then updated the
 * PREVIOUS draft in place (an overwrite) and could leak the old voice-note URL
 * into the new post. Reported alongside a voice note that vanished on the first
 * resume; both live in the same save/resume flow.
 *
 * Fix: clear the whole draft-editing context (editingDraftId, resumedVoiceNoteURL,
 * draftCreatedAt, savedSignature) on picker return, mirroring the fresh-compose
 * reset and iOS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeViewModelDraftEditingContextTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var postRepository: PostRepository
    private lateinit var spotifyRepository: SpotifyRepository
    private lateinit var musicSearchRepository: MusicSearchRepository
    private lateinit var tmdbRepository: TMDBRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var analyticsService: AnalyticsService
    private lateinit var userRepository: UserRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var exploreRepository: ExploreRepository
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var postCreationEvent: PostCreationEvent
    private lateinit var hapticManager: HapticManager
    private lateinit var remoteConfigService: RemoteConfigService
    private lateinit var networkMonitor: NetworkMonitor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        postRepository = mock()
        spotifyRepository = mock()
        musicSearchRepository = mock()
        tmdbRepository = mock()
        // null currentUserId keeps the profile-refresh init coroutine inert.
        authRepository = mock { on { currentUserId } doReturn null }
        analyticsService = mock()
        userRepository = mock()
        subscriptionRepository = mock()
        // Keep the trending-load init coroutine on its happy path.
        exploreRepository = mock {
            onBlocking { fetchTrendingSongs(any(), any()) } doReturn emptyList()
            onBlocking { fetchTrendingMovies(any(), any()) } doReturn emptyList()
        }
        nowPlayingManager = mock()
        postCreationEvent = mock()
        hapticManager = mock()
        remoteConfigService = mock()
        networkMonitor = mock { on { isConnected } doReturn MutableStateFlow(true) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ComposeViewModel(
        postRepository = postRepository,
        postDraftRepository = mock(),
        spotifyRepository = spotifyRepository,
        musicSearchRepository = musicSearchRepository,
        tmdbRepository = tmdbRepository,
        authRepository = authRepository,
        analyticsService = analyticsService,
        userRepository = userRepository,
        subscriptionRepository = subscriptionRepository,
        exploreRepository = exploreRepository,
        nowPlayingManager = nowPlayingManager,
        postCreationEvent = postCreationEvent,
        hapticManager = hapticManager,
        remoteConfigService = remoteConfigService,
        networkMonitor = networkMonitor,
    )

    // An "sc:" id makes resumeDraft's staleness refresh return early (no
    // coroutine, no repo call), so the resume is fully synchronous.
    private fun voiceDraft(id: String) = PostDraft(
        id = id,
        mediaType = MediaType.TRACK,
        caption = "",
        captionMode = "voice",
        track = CymbalTrack(
            id = "sc:track1",
            name = "Song",
            artistName = "Artist",
            albumName = "Album",
            albumArtURL = "https://img/1.jpg",
            source = TrackSource.SPOTIFY,
        ),
        voiceNoteURL = "https://storage/$id/voice.m4a",
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )

    @Test
    fun `returning to the picker clears the resumed draft editing context`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                val vm = createViewModel()

                vm.resumeDraft(voiceDraft("draftA"))
                // Precondition: resuming set the editing context.
                assertEquals("draftA", vm.editingDraftId.value)
                assertEquals(
                    "https://storage/draftA/voice.m4a",
                    vm.resumedVoiceNoteURL.value,
                )

                // Returning to the picker must abandon that context so the next
                // pick saves as a NEW draft (not an in-place overwrite of draftA)
                // and never leaks the old voice-note URL.
                vm.clearSelectionKeepingResults()

                assertNull(
                    "editingDraftId must reset on picker return",
                    vm.editingDraftId.value,
                )
                assertNull(
                    "resumedVoiceNoteURL must reset on picker return",
                    vm.resumedVoiceNoteURL.value,
                )

                // Drain the VM's init coroutines (trending load) while Log is
                // mocked so their catch-branch Log.e calls stay no-ops.
                advanceUntilIdle()
            }
        }
}
