package fm.corus.android.ui.screens.profile

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var userRepository: UserRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var engagementManager: PostEngagementManager
    private lateinit var postCreationEvent: PostCreationEvent
    private lateinit var postDeletionEvent: PostDeletionEvent
    private lateinit var analyticsService: AnalyticsService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn MutableStateFlow<CymbalUser?>(null)
        }
        cloudFunctions = mock()
        userRepository = mock()
        subscriptionRepository = mock {
            on { isClubMember } doReturn MutableStateFlow(false)
            on { hasFullAccessFlow } doReturn MutableStateFlow(false)
        }
        nowPlayingManager = mock()
        engagementManager = mock {
            on { states } doReturn MutableStateFlow(emptyMap())
        }
        postCreationEvent = mock {
            on { events } doReturn MutableSharedFlow()
        }
        postDeletionEvent = mock {
            on { events } doReturn MutableSharedFlow()
        }
        analyticsService = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProfileViewModel = ProfileViewModel(
        authRepository = authRepository,
        cloudFunctions = cloudFunctions,
        userRepository = userRepository,
        subscriptionRepository = subscriptionRepository,
        nowPlayingManager = nowPlayingManager,
        engagementManager = engagementManager,
        postCreationEvent = postCreationEvent,
        postDeletionEvent = postDeletionEvent,
        commentEditedEvent = fm.corus.android.domain.CommentEditedEvent(),
        commentDeletedEvent = fm.corus.android.domain.CommentDeletedEvent(),
        saveChangedEvent = fm.corus.android.domain.SaveChangedEvent(),
        analyticsService = analyticsService,
        musicServicePreference = mock(),
        remoteConfigService = mock(),
        networkMonitor = mock { on { isConnected } doReturn kotlinx.coroutines.flow.MutableStateFlow(true) },
    )

    private fun makeUser(id: String = "user1", movieCount: Int? = null, trackCount: Int? = null) = CymbalUser(
        id = id,
        username = "u",
        displayName = "U",
        movieCount = movieCount,
        trackCount = trackCount,
    )

    private fun makeMoviePost(id: String, timestampMs: Long) = CymbalPost(
        id = id,
        user = makeUser(),
        track = CymbalTrack(id = "t-$id", name = "t", artistName = "a", albumName = "al"),
        mediaType = MediaType.MOVIE,
        timestamp = Date(timestampMs),
    )

    private fun makeTrackPost(id: String, timestampMs: Long) = CymbalPost(
        id = id,
        user = makeUser(),
        track = CymbalTrack(id = "t-$id", name = "t", artistName = "a", albumName = "al"),
        mediaType = MediaType.TRACK,
        timestamp = Date(timestampMs),
    )

    @Test
    fun `loadFilmPageIfNeeded fetches movie-only page even when stale movieCount equals cached films`() = runTest {
        // Regression: a denormalized movieCount on the user doc used to short-
        // circuit the film-only fetch via a `cachedAllFilms` guard. If the
        // counter drifted to 1 (the backend ships a backfill repair job for
        // exactly this drift) and the recency-sorted initial mixed page already
        // pulled one film into cache, the Film tab got stuck on the featured
        // film with no grid below. We now ignore the counter and let the
        // film-only fetch be authoritative.
        val profileFlow = MutableStateFlow<CymbalUser?>(makeUser(movieCount = 1))
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn profileFlow
        }
        val cachedFilm = makeMoviePost("featured", timestampMs = 5_000)
        // Initial mixed-page fetch — returns 1 film among the recency-sorted posts.
        whenever(
            cloudFunctions.getProfilePosts(
                eq("user1"), eq("user1"), any(), eq(null), eq(null),
            )
        ).thenReturn(listOf(cachedFilm))
        // Film-only fetch — server actually has 14 films; profile counter is stale.
        val allFilms = (1..14).map { makeMoviePost("f-$it", timestampMs = (10_000 - it).toLong()) }
        whenever(
            cloudFunctions.getProfilePosts(
                eq("user1"), eq("user1"), any(), eq(null), eq("movie"),
            )
        ).thenReturn(allFilms)

        val viewModel = createViewModel()
        viewModel.loadProfile()
        advanceUntilIdle()
        viewModel.loadFilmPageIfNeeded()
        advanceUntilIdle()

        // Film fetch ran despite movieCount=1 == cached=1 — that's the regression.
        verify(cloudFunctions).getProfilePosts(
            eq("user1"), eq("user1"), any(), eq(null), eq("movie"),
        )
        val films = viewModel.posts.value.filter { it.mediaType == MediaType.MOVIE }
        assertEquals(14, films.size)
    }

    @Test
    fun `loadMoreForSegment film tab paginates with movie filter and its own cursor`() = runTest {
        // Regression: pagination on the Film tab used to share the request
        // shape with the Music tab — no mediaType filter, mixed-media cursor —
        // so each page came back as music that the UI filtered down to nothing,
        // leaving the user stuck on the featured film. The fix gives segment 1
        // its own filmsLastTimestamp and forces mediaType="movie" on every
        // pagination request.
        val profileFlow = MutableStateFlow<CymbalUser?>(makeUser(movieCount = null))
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn profileFlow
        }
        whenever(
            cloudFunctions.getProfilePosts(
                eq("user1"), eq("user1"), any(), eq(null), eq(null),
            )
        ).thenReturn(emptyList())
        // Initial film page returns a full page so hasMore[1] stays true.
        val firstFilmPage = (1..30).map { makeMoviePost("p1-$it", timestampMs = (100_000 - it).toLong()) }
        whenever(
            cloudFunctions.getProfilePosts(
                eq("user1"), eq("user1"), any(), eq(null), eq("movie"),
            )
        ).thenReturn(firstFilmPage)
        val expectedCursor = firstFilmPage.last().timestamp.time
        val secondFilmPage = (1..5).map { makeMoviePost("p2-$it", timestampMs = (50_000 - it).toLong()) }
        whenever(
            cloudFunctions.getProfilePosts(
                eq("user1"), eq("user1"), any(), eq(expectedCursor), eq("movie"),
            )
        ).thenReturn(secondFilmPage)

        val viewModel = createViewModel()
        viewModel.loadProfile()
        advanceUntilIdle()
        viewModel.loadFilmPageIfNeeded()
        advanceUntilIdle()
        viewModel.loadMoreForSegment(1)
        advanceUntilIdle()

        verify(cloudFunctions).getProfilePosts(
            eq("user1"), eq("user1"), any(), eq(expectedCursor), eq("movie"),
        )
        val films = viewModel.posts.value.filter { it.mediaType == MediaType.MOVIE }
        assertEquals(35, films.size)
    }

    @Test
    fun `loadMoreForSegment film tab is a no-op until the initial film page lands`() = runTest {
        // Without this gate, a fast scroll on the Film tab fires loadMore
        // before loadFilmPageIfNeeded has populated filmsLastTimestamp; the
        // null cursor would flip hasMore[1] to false and silently end
        // pagination. Regression coverage: pagination must do nothing (no
        // backend call, hasMore preserved) until the initial fetch completes.
        val profileFlow = MutableStateFlow<CymbalUser?>(makeUser(movieCount = null))
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn profileFlow
        }
        whenever(
            cloudFunctions.getProfilePosts(
                eq("user1"), eq("user1"), any(), eq(null), eq(null),
            )
        ).thenReturn(emptyList())

        val viewModel = createViewModel()
        viewModel.loadProfile()
        advanceUntilIdle()
        // Note: deliberately *not* calling loadFilmPageIfNeeded — simulate the
        // race where loadMore fires first.
        viewModel.loadMoreForSegment(1)
        advanceUntilIdle()

        // No film-only fetch, no mixed-media fetch beyond loadProfile's first call.
        verify(cloudFunctions, org.mockito.kotlin.never()).getProfilePosts(
            eq("user1"), eq("user1"), any(), any(), eq("movie"),
        )
        assertTrue(viewModel.hasMore.value[1] ?: false)
    }

    @Test
    fun `construction succeeds when authRepository userProfile is already non-null`() = runTest {
        // Regression: init { } launches a coroutine that collects authRepository.userProfile
        // (a StateFlow), and an event from postCreationEvent eventually calls refreshProfile()
        // — which touches _isLoading, _currentSegment, _hasMore, _hasFetchedFilmPage,
        // _isRefreshing. Those fields used to be declared *after* the init block, so during
        // init they were null. With Dispatchers.Main.immediate (viewModelScope's default),
        // the launches dispatch synchronously, and a StateFlow with a non-null current
        // value emits eagerly to a fresh collector. On a logged-in user that race produced
        // `NullPointerException: ... MutableStateFlow.setValue ... null object reference`
        // during construction. (Crashlytics 96b87ad5.)
        val creationEvents = MutableSharedFlow<fm.corus.android.data.model.MediaType>(extraBufferCapacity = 1)
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn MutableStateFlow<CymbalUser?>(makeUser())
        }
        postCreationEvent = mock { on { events } doReturn creationEvents }
        whenever(cloudFunctions.getProfilePosts(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())

        // Construction itself must not throw — this is the actual regression assertion.
        val viewModel = createViewModel()
        advanceUntilIdle()

        // And the post-creation → refreshProfile path must complete cleanly, since it
        // exercises every MutableStateFlow that was previously declared after init.
        creationEvents.tryEmit(fm.corus.android.data.model.MediaType.TRACK)
        advanceUntilIdle()

        // Sanity: refreshProfile ran and settled the loading flags.
        assertEquals(false, viewModel.isLoading.value)
        assertEquals(false, viewModel.isRefreshing.value)
    }

    @Test
    fun `loadSongPageIfNeeded backfills tracks when the recency window is film-only`() = runTest {
        // The MUSIC tab is a client-side filter over the recency-ordered posts
        // window. A film-dominant poster whose latest page is all films arrives
        // with zero tracks in that window. With trackCount>0, the backfill must
        // fetch mediaType="track" and append songs so the music grid isn't empty.
        val profileFlow = MutableStateFlow<CymbalUser?>(makeUser(trackCount = 3))
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn profileFlow
        }
        // Initial mixed page — a FULL page (>= PAGE_SIZE) of films, no tracks, so
        // hasMore[0] stays true (songs may live deeper in history). A short page
        // would prove everything is loaded and correctly skip the backfill.
        val films = (1..30).map { makeMoviePost("f-$it", timestampMs = (100_000 - it).toLong()) }
        whenever(
            cloudFunctions.getProfilePosts(eq("user1"), eq("user1"), any(), eq(null), eq(null))
        ).thenReturn(films)
        // Song-only backfill — the user does have songs deeper in history.
        val songs = (1..3).map { makeTrackPost("s-$it", timestampMs = (5_000 - it).toLong()) }
        whenever(
            cloudFunctions.getProfilePosts(eq("user1"), eq("user1"), any(), eq(null), eq("track"))
        ).thenReturn(songs)

        val viewModel = createViewModel()
        viewModel.loadProfile()
        advanceUntilIdle()

        // The film-only recency window auto-triggered the song backfill.
        verify(cloudFunctions).getProfilePosts(eq("user1"), eq("user1"), any(), eq(null), eq("track"))
        val tracks = viewModel.posts.value.filter { it.mediaType == MediaType.TRACK }
        assertEquals(listOf("s-1", "s-2", "s-3"), tracks.map { it.id })
        // After a successful backfill the page is marked fetched and not loading,
        // so the MUSIC tab can resolve (skeleton-hold released).
        assertTrue(viewModel.hasFetchedSongPage.value)
        assertEquals(false, viewModel.isLoadingSongs.value)
    }

    @Test
    fun `loadSongPageIfNeeded does not fetch when trackCount is zero`() = runTest {
        // A genuinely film-only poster (trackCount==0) must NOT fire the backfill
        // and must mark the page fetched so the MUSIC tab can immediately show
        // the empty state (no skeleton flash, no wasted round-trip).
        val profileFlow = MutableStateFlow<CymbalUser?>(makeUser(trackCount = 0))
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn profileFlow
        }
        val films = (1..3).map { makeMoviePost("f-$it", timestampMs = (10_000 - it).toLong()) }
        whenever(
            cloudFunctions.getProfilePosts(eq("user1"), eq("user1"), any(), eq(null), eq(null))
        ).thenReturn(films)

        val viewModel = createViewModel()
        viewModel.loadProfile()
        advanceUntilIdle()

        // No mediaType="track" fetch — trackCount==0 short-circuits it.
        verify(cloudFunctions, org.mockito.kotlin.never()).getProfilePosts(
            eq("user1"), eq("user1"), any(), any(), eq("track"),
        )
        // Marked fetched (so the empty state is allowed) and never entered loading.
        assertTrue(viewModel.hasFetchedSongPage.value)
        assertEquals(false, viewModel.isLoadingSongs.value)
    }

    @Test
    fun `song backfill keeps isLoadingSongs true in flight then false after`() = runTest {
        // The skeleton-hold signal (segment 0) keys off isLoadingSongs while the
        // backfill is in flight, so the "No songs yet" empty state is gated until
        // it resolves. Use a suspended fetch to observe the loading flag mid-flight.
        val profileFlow = MutableStateFlow<CymbalUser?>(makeUser(trackCount = 2))
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn profileFlow
        }
        // Full initial film page so hasMore[0] stays true and the backfill runs.
        val films = (1..30).map { makeMoviePost("f-$it", timestampMs = (100_000 - it).toLong()) }
        whenever(
            cloudFunctions.getProfilePosts(eq("user1"), eq("user1"), any(), eq(null), eq(null))
        ).thenReturn(films)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val songs = (1..2).map { makeTrackPost("s-$it", timestampMs = (5_000 - it).toLong()) }
        // doSuspendableAnswer parks the coroutine on the gate (a real suspend,
        // not a thread block) so advanceUntilIdle can still drive the dispatcher.
        cloudFunctions.stub {
            onBlocking {
                getProfilePosts(eq("user1"), eq("user1"), any(), eq(null), eq("track"))
            }.doSuspendableAnswer {
                gate.await()
                songs
            }
        }

        val viewModel = createViewModel()
        viewModel.loadProfile()
        advanceUntilIdle()

        // Backfill is launched but parked on the gate: skeleton-hold is active.
        assertTrue("expected backfill in flight", viewModel.isLoadingSongs.value)
        assertTrue(viewModel.hasFetchedSongPage.value)

        // Release the fetch and let it settle.
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoadingSongs.value)
        assertTrue(viewModel.hasFetchedSongPage.value)
        val tracks = viewModel.posts.value.filter { it.mediaType == MediaType.TRACK }
        assertEquals(2, tracks.size)
    }

    @Test
    fun `uploadAvatar exposes pending bytes synchronously before the upload runs`() = runTest {
        whenever(userRepository.uploadAvatar(any(), any())).thenReturn("https://example.com/a.jpg")
        val viewModel = createViewModel()
        val bytes = byteArrayOf(1, 2, 3, 4)

        // StandardTestDispatcher defers the launched body until advance, so this asserts
        // the state observable on the same frame as the user's tap-to-confirm.
        viewModel.uploadAvatar(bytes)
        assertArrayEquals(bytes, viewModel.pendingAvatarBytes.value)

        advanceUntilIdle()
        assertNull(viewModel.pendingAvatarBytes.value)
    }

}
