package fm.corus.android.ui.screens.profile

import android.content.Context
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.LinkedArtist
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * Regression guard for the "header says 2, grid says no songs yet" bug on
 * cold profile loads from the New on Corus list.
 *
 * Context: when the header count and the posts grid come from two separate
 * cloud calls (fetchUserProfile + getProfilePosts), Firestore's composite
 * index for `(userId, createdAt)` can briefly lag the user document's
 * cymbalCount for brand-new users who just posted. The fix consolidates
 * cold load into a single getProfileData call so the header and grid
 * always come from the same backend snapshot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OtherProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var userRepository: UserRepository
    private lateinit var postRepository: PostRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var engagementManager: PostEngagementManager
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var postDeletionEvent: PostDeletionEvent
    private lateinit var cloudFunctions: CloudFunctionsDataSource

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mock()
        authRepository = mock {
            on { currentUserId } doReturn "viewer1"
            on { userProfile } doReturn MutableStateFlow(null)
        }
        userRepository = mock {
            on { blockedIds } doReturn MutableStateFlow(emptySet())
            on { isFollowing(any()) } doReturn false
            on { isUserMuted(any()) } doReturn false
        }
        postRepository = mock()
        nowPlayingManager = mock()
        engagementManager = mock {
            on { states } doReturn MutableStateFlow(emptyMap())
        }
        subscriptionRepository = mock {
            on { hasFullAccessFlow } doReturn MutableStateFlow(false)
        }
        postDeletionEvent = mock {
            on { events } doReturn MutableSharedFlow()
        }
        cloudFunctions = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): OtherProfileViewModel = OtherProfileViewModel(
        context = context,
        userRepository = userRepository,
        postRepository = postRepository,
        authRepository = authRepository,
        messageRepository = mock(),
        nowPlayingManager = nowPlayingManager,
        cloudFunctions = cloudFunctions,
        musicServicePreference = mock(),
        analyticsService = mock(),
        remoteConfig = mock(),
        engagementManager = engagementManager,
        subscriptionRepository = subscriptionRepository,
        postDeletionEvent = postDeletionEvent,
        favoriteChangedEvent = fm.corus.android.domain.FavoriteChangedEvent(),
        commentEditedEvent = fm.corus.android.domain.CommentEditedEvent(),
        commentDeletedEvent = fm.corus.android.domain.CommentDeletedEvent(),
    )

    private fun makeUser(id: String, cymbalCount: Int): CymbalUser = CymbalUser(
        id = id,
        username = "user_$id",
        displayName = "User $id",
        cymbalCount = cymbalCount,
    )

    private fun makePost(id: String, userId: String): CymbalPost = CymbalPost(
        id = id,
        user = makeUser(userId, 0),
        track = CymbalTrack(
            id = "t_$id",
            name = "Track $id",
            artistName = "Artist",
            albumName = "Album",
        ),
        timestamp = Date(),
        mediaType = MediaType.TRACK,
    )

    private fun makeUserWithCounts(
        id: String,
        trackCount: Int? = null,
        movieCount: Int? = null,
    ): CymbalUser = CymbalUser(
        id = id,
        username = "user_$id",
        displayName = "User $id",
        trackCount = trackCount,
        movieCount = movieCount,
    )

    private fun makeMoviePost(id: String, userId: String, timestampMs: Long = 1L): CymbalPost = CymbalPost(
        id = id,
        user = makeUser(userId, 0),
        track = CymbalTrack(
            id = "t_$id",
            name = "Film $id",
            artistName = "Director",
            albumName = "Film",
        ),
        timestamp = Date(timestampMs),
        mediaType = MediaType.MOVIE,
    )

    private fun makeTrackPostTs(id: String, userId: String, timestampMs: Long): CymbalPost = CymbalPost(
        id = id,
        user = makeUser(userId, 0),
        track = CymbalTrack(
            id = "t_$id",
            name = "Track $id",
            artistName = "Artist",
            albumName = "Album",
        ),
        timestamp = Date(timestampMs),
        mediaType = MediaType.TRACK,
    )

    @Test
    fun `loadProfile populates header and grid from a single getProfileData call`() = runTest {
        val targetId = "target1"
        val cloudUser = makeUser(targetId, cymbalCount = 2)
        val cloudPosts = listOf(makePost("p1", targetId), makePost("p2", targetId))
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(cloudUser, cloudPosts))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()

        // Header count and posts list both came from the same backend snapshot,
        // so they can't disagree the way the original two-call path did.
        assertEquals(2, viewModel.profile.value?.cymbalCount)
        assertEquals(listOf("p1", "p2"), viewModel.posts.value.map { it.id })

        // Legacy two-call path must not be exercised when the consolidated
        // call succeeds — that's the whole point of the fix.
        verify(userRepository, never()).fetchUserProfile(any())
        verify(postRepository, never()).getProfilePosts(any(), any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `loadProfile applies linkedArtist from the same getProfileData payload`() = runTest {
        val targetId = "target1"
        val artist = LinkedArtist(id = "spotify-id", name = "Daedelus", imageUrl = "https://img")
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(
                CloudFunctionsDataSource.ProfileData(
                    user = makeUser(targetId, cymbalCount = 1),
                    posts = listOf(makePost("p1", targetId)),
                    linkedArtist = artist,
                ),
            )
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()

        assertEquals(artist, viewModel.linkedArtist.value)
    }

    /**
     * Shadow-ban containment relies on the NOT_FOUND branch bouncing WITHOUT the
     * direct-read fallback. That exact branch can't be unit-tested here — a real
     * FirebaseFunctionsException can't be instantiated in this plain-JUnit JVM
     * (its static initializer fails), and the branch mirrors the already-shipped
     * NOT_FOUND handling in CloudFunctionsDataSource.
     *
     * This guards the OTHER half — the regression risk my change introduced: a
     * TRANSIENT (non-NOT_FOUND) getProfileData failure must STILL fall back to the
     * legacy direct-read path and must NOT bounce to the unavailable state.
     */
    @Test
    fun `loadProfile still falls back on a transient error and does not bounce`() = runTest {
        val targetId = "transient1"
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenThrow(RuntimeException("network blip"))
        whenever(userRepository.fetchUserProfile(eq(targetId)))
            .thenReturn(makeUser(targetId, cymbalCount = 0))
        whenever(postRepository.getProfilePosts(eq(targetId), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(emptyList())
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()

        // A transient error is NOT a ban — the profile still loads via the fallback.
        assertEquals(false, viewModel.profileUnavailable.value)
        assertEquals(targetId, viewModel.profile.value?.id)
        verify(userRepository).fetchUserProfile(eq(targetId))
    }

    @Test
    fun `loadProfile falls back to legacy path when getProfileData returns a null user`() = runTest {
        val targetId = "target2"
        // Simulate a backend response that doesn't include the user (e.g.
        // block relationship returns user=null, posts=[]).
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(null, emptyList()))
        whenever(userRepository.fetchUserProfile(eq(targetId)))
            .thenReturn(makeUser(targetId, cymbalCount = 0))
        whenever(postRepository.getProfilePosts(eq(targetId), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(emptyList())
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()

        verify(userRepository).fetchUserProfile(eq(targetId))
        verify(postRepository).getProfilePosts(eq(targetId), any(), any(), anyOrNull(), anyOrNull())
    }

    /**
     * Regression guard for the LIKES-tab bug: tapping LIKES on another user's
     * profile used to render `posts` (the owner's OWN posts) because the
     * ViewModel never fetched likes. The fix loads a dedicated [likedPosts]
     * list from getLikedPosts — the owner's liked posts, authored by others.
     */
    @Test
    fun `loadLikedPosts shows the owner's liked posts, not their own posts`() = runTest {
        val targetId = "target3"
        val ownPosts = listOf(makePost("own1", targetId))
        val liked = listOf(makePost("liked1", "other_a"), makePost("liked2", "other_b"))
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(makeUser(targetId, 1), ownPosts))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)
        whenever(cloudFunctions.getLikedPosts(eq(targetId), eq("viewer1"), any(), any()))
            .thenReturn(CloudFunctionsDataSource.LikedPostsPage(liked, hasMore = false))

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()
        viewModel.loadLikedPosts(targetId)
        advanceUntilIdle()

        assertEquals(listOf("liked1", "liked2"), viewModel.likedPosts.value.map { it.id })
        // The owner's own posts are unchanged and distinct — proving the LIKES
        // tab no longer reuses `posts`.
        assertEquals(listOf("own1"), viewModel.posts.value.map { it.id })
        verify(cloudFunctions).getLikedPosts(eq(targetId), eq("viewer1"), any(), any())
    }

    /**
     * Regression guard for the "Trending feed shows Follow, but the profile
     * opened from it shows Following" bug. The feed nav used to hardcode
     * `initialIsFollowing = true` (a Following-feed-era assumption), so an
     * unfollowed Trending/For You author's profile rendered "Following".
     * With the fix, the feed passes no hint (null) and start() seeds the
     * follow state from the cached following set instead of assuming true.
     */
    @Test
    fun `start with null hint does not assume following for an unfollowed author`() = runTest {
        val targetId = "unfollowed1"
        // Cached following set does NOT contain the author (the Trending case).
        whenever(userRepository.isFollowing(eq(targetId))).thenReturn(false)
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(makeUser(targetId, 0), emptyList()))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = null)

        // Seeded synchronously from the cache before the server reconcile, so
        // the button never flashes "Following" for an author you don't follow.
        assertEquals(false, viewModel.isFollowing.value)

        advanceUntilIdle()
        assertEquals(false, viewModel.isFollowing.value)
    }

    @Test
    fun `start with null hint seeds following state from the cached following set`() = runTest {
        val targetId = "followed1"
        // Viewer already follows this author (e.g. a Following-feed post).
        whenever(userRepository.isFollowing(eq(targetId))).thenReturn(true)
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(makeUser(targetId, 0), emptyList()))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = null)

        // Seeded from cache on the first frame — no "Follow" flash for someone
        // the viewer already follows.
        assertEquals(true, viewModel.isFollowing.value)
    }

    @Test
    fun `loadMoreLiked paginates with the running offset`() = runTest {
        val targetId = "target4"
        val firstPage = (1..30).map { makePost("l$it", "a$it") }
        val secondPage = listOf(makePost("l31", "a31"))
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(makeUser(targetId, 0), emptyList()))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)
        whenever(cloudFunctions.getLikedPosts(eq(targetId), eq("viewer1"), any(), eq(0)))
            .thenReturn(CloudFunctionsDataSource.LikedPostsPage(firstPage, hasMore = true))
        whenever(cloudFunctions.getLikedPosts(eq(targetId), eq("viewer1"), any(), eq(30)))
            .thenReturn(CloudFunctionsDataSource.LikedPostsPage(secondPage, hasMore = false))

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()
        viewModel.loadLikedPosts(targetId)
        advanceUntilIdle()
        viewModel.loadMoreLiked(targetId)
        advanceUntilIdle()

        assertEquals(31, viewModel.likedPosts.value.size)
        // The second page is fetched at offset = size of the first page.
        verify(cloudFunctions).getLikedPosts(eq(targetId), eq("viewer1"), any(), eq(30))
    }

    /**
     * Regression guard for the film-dominant poster "No songs yet" bug: the
     * MUSIC tab filters the recency-ordered posts window, so a poster whose
     * latest page is all films shows zero songs there. With trackCount>0 the
     * ViewModel must backfill mediaType="track" so the music grid is non-empty.
     */
    @Test
    fun `loadSongPageIfNeeded backfills tracks when the recency window is film-only`() = runTest {
        val targetId = "filmposter1"
        // A FULL page (>= PAGE_SIZE) of films so hasMore stays true (songs may
        // live deeper in history). A short page would prove everything is loaded
        // and correctly skip the backfill.
        val films = (1..30).map { makeMoviePost("f$it", targetId, timestampMs = (100_000 - it).toLong()) }
        // getProfileData returns a film-only first page; trackCount says songs exist.
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(makeUserWithCounts(targetId, trackCount = 3, movieCount = 3), films))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)
        // Song-only backfill — the songs live deeper in history.
        val songs = (1..3).map { makeTrackPostTs("s$it", targetId, timestampMs = (5_000 - it).toLong()) }
        whenever(postRepository.getProfilePosts(eq(targetId), eq("viewer1"), any(), eq(null), eq("track")))
            .thenReturn(songs)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()

        // Film-only recency window auto-triggered the song backfill.
        verify(postRepository).getProfilePosts(eq(targetId), eq("viewer1"), any(), eq(null), eq("track"))
        val tracks = viewModel.posts.value.filter { it.mediaType == MediaType.TRACK }
        assertEquals(listOf("s1", "s2", "s3"), tracks.map { it.id })
        assertEquals(true, viewModel.hasFetchedSongPage.value)
        assertEquals(false, viewModel.isLoadingSongs.value)
    }

    @Test
    fun `loadSongPageIfNeeded does not fetch when trackCount is zero`() = runTest {
        val targetId = "filmonly1"
        val films = (1..2).map { makeMoviePost("f$it", targetId, timestampMs = (10_000 - it).toLong()) }
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(makeUserWithCounts(targetId, trackCount = 0, movieCount = 2), films))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()

        // trackCount==0 short-circuits — no mediaType="track" fetch.
        verify(postRepository, never()).getProfilePosts(
            eq(targetId), eq("viewer1"), any(), anyOrNull(), eq("track"),
        )
        // Marked fetched so the empty state is allowed; never entered loading.
        assertEquals(true, viewModel.hasFetchedSongPage.value)
        assertEquals(false, viewModel.isLoadingSongs.value)
    }

    @Test
    fun `song backfill keeps isLoadingSongs true in flight then false after`() = runTest {
        // The screen's songsPending skeleton-hold keys off isLoadingSongs, so the
        // "No songs yet" empty state is gated until the backfill resolves.
        val targetId = "filmposter2"
        // Full initial film page so hasMore stays true and the backfill runs.
        val films = (1..30).map { makeMoviePost("f$it", targetId, timestampMs = (100_000 - it).toLong()) }
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(makeUserWithCounts(targetId, trackCount = 2, movieCount = 30), films))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val songs = (1..2).map { makeTrackPostTs("s$it", targetId, timestampMs = (5_000 - it).toLong()) }
        postRepository.stub {
            onBlocking {
                getProfilePosts(eq(targetId), eq("viewer1"), any(), eq(null), eq("track"))
            }.doSuspendableAnswer {
                gate.await()
                songs
            }
        }

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()

        // Backfill launched but parked on the gate: skeleton-hold is active.
        assertEquals(true, viewModel.isLoadingSongs.value)
        assertEquals(true, viewModel.hasFetchedSongPage.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoadingSongs.value)
        assertEquals(true, viewModel.hasFetchedSongPage.value)
        assertEquals(2, viewModel.posts.value.count { it.mediaType == MediaType.TRACK })
    }
}
