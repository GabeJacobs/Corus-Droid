package fm.corus.android.ui.player

import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.PostEngagementManager
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class FullPlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val postRepo = mock<PostRepository>()
    private val authRepo = mock<AuthRepository>()
    private val engagementManager = mock<PostEngagementManager>()
    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val hapticManager = mock<HapticManager>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(authRepo.currentUserId).thenReturn("me")
        whenever(engagementManager.states).thenReturn(MutableStateFlow(emptyMap()))
        whenever(engagementManager.getState(any())).thenReturn(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = FullPlayerViewModel(
        postRepo,
        authRepo,
        engagementManager,
        cloudFunctions,
        hapticManager,
    )

    private fun user(id: String) = CymbalUser(id = id, username = "u$id", displayName = "U$id")

    private fun track(id: String = "t1") = CymbalTrack(
        id = id,
        name = "Song",
        artistName = "Artist",
        albumName = "Album",
        albumArtURL = null,
        albumArtLargeURL = null,
        spotifyURI = "spotify:track:$id",
        spotifyWebURL = "https://open.spotify.com/track/$id",
        source = TrackSource.SPOTIFY,
    )

    private fun post(id: String, userId: String = "u1") = CymbalPost(
        id = id,
        user = user(userId),
        track = track(),
        caption = "hello",
        timestamp = Date(1_700_000_000_000L),
        likeCount = 2,
        commentCount = 3,
        repostCount = 1,
        saveCount = 0,
        isLiked = false,
    )

    private fun comment(id: String, parentId: String? = null, text: String = "c") = CymbalComment(
        id = id,
        user = user("author_$id"),
        text = text,
        timestamp = Date(0),
        parentCommentId = parentId,
        likeCount = 0,
    )

    @Test
    fun loadSourcePostHydratesEngagementAndComments() = runTest {
        val p = post("p1")
        whenever(postRepo.getPostDetail("p1", "me")).doReturn(p)
        whenever(postRepo.getComments("p1")).doReturn(
            listOf(comment("c1"), comment("c2", parentId = "c1")),
        )
        whenever(postRepo.checkCommentLikesBatch(eq("me"), eq("p1"), any())).doReturn(setOf("c1"))

        val viewModel = vm()
        viewModel.loadSourcePost("p1")
        advanceUntilIdle()

        assertEquals("p1", viewModel.sourcePost.value?.id)
        assertEquals(listOf("c1"), viewModel.comments.value.map { it.id })
        assertEquals(listOf("c2"), viewModel.repliesByParent.value["c1"]?.map { it.id })
        assertEquals(setOf("c1"), viewModel.likedCommentIds.value)
        assertFalse(viewModel.isLoadingSourcePost.value)
        assertFalse(viewModel.isLoadingComments.value)
    }

    @Test
    fun clearingSourcePostClearsComments() = runTest {
        val p = post("p1")
        whenever(postRepo.getPostDetail("p1", "me")).doReturn(p)
        whenever(postRepo.getComments("p1")).doReturn(listOf(comment("c1")))
        whenever(postRepo.checkCommentLikesBatch(eq("me"), eq("p1"), any())).doReturn(emptySet())

        val viewModel = vm()
        viewModel.loadSourcePost("p1")
        advanceUntilIdle()
        viewModel.loadSourcePost(null)
        advanceUntilIdle()

        assertNull(viewModel.sourcePost.value)
        assertTrue(viewModel.comments.value.isEmpty())
    }

    @Test
    fun optimisticCommentAppendsUntilServerConfirms() = runTest {
        whenever(postRepo.getPostDetail("p1", "me")).doReturn(post("p1"))
        whenever(postRepo.getComments("p1")).doReturn(emptyList())
        whenever(postRepo.checkCommentLikesBatch(eq("me"), eq("p1"), any())).doReturn(emptySet())

        val viewModel = vm()
        viewModel.loadSourcePost("p1")
        advanceUntilIdle()

        val temp = comment("temp_1", text = "new")
        viewModel.insertOptimisticComment(temp, parentId = null)
        assertEquals(listOf("temp_1"), viewModel.comments.value.map { it.id })

        whenever(postRepo.getComments("p1")).doReturn(
            listOf(comment("server_1", text = "new").copy(user = temp.user, timestamp = temp.timestamp)),
        )
        viewModel.refreshComments()
        advanceUntilIdle()

        assertEquals(listOf("server_1"), viewModel.comments.value.map { it.id })
    }

    @Test
    fun catalogEmptyShowsNoPostsAndClearsOnSourcePost() = runTest {
        whenever(
            postRepo.fetchSongPostsFromCloud(
                trackId = eq("t1"),
                spotifyURI = anyOrNull(),
                isrc = anyOrNull(),
                trackName = anyOrNull(),
                artistName = anyOrNull(),
                pageSize = any(),
                beforeMs = anyOrNull(),
            ),
        ).doReturn(
            CloudFunctionsDataSource.SongPostsPage(
                posts = emptyList(),
                uniquePosterCount = 0,
                firstPosterId = null,
            ),
        )

        val viewModel = vm()
        viewModel.loadCatalogPostsIfNeeded(trackId = "t1", trackName = "Song", artistName = "Artist")
        advanceUntilIdle()

        assertTrue(viewModel.catalogPosts.value.isEmpty())
        assertNull(viewModel.catalogPostsError.value)
        assertFalse(viewModel.isLoadingCatalogPosts.value)

        whenever(postRepo.getPostDetail("p9", "me")).doReturn(post("p9"))
        whenever(postRepo.getComments("p9")).doReturn(emptyList())
        whenever(postRepo.checkCommentLikesBatch(eq("me"), eq("p9"), any())).doReturn(emptySet())
        viewModel.onPlaybackIdentityChanged(sourcePostId = "p9", trackId = "t1")
        advanceUntilIdle()

        assertTrue(viewModel.catalogPosts.value.isEmpty())
        assertEquals("p9", viewModel.sourcePost.value?.id)
    }

    @Test
    fun catalogErrorSurfacesRetryableMessage() = runTest {
        whenever(
            postRepo.fetchSongPostsFromCloud(
                trackId = eq("t1"),
                spotifyURI = anyOrNull(),
                isrc = anyOrNull(),
                trackName = anyOrNull(),
                artistName = anyOrNull(),
                pageSize = any(),
                beforeMs = anyOrNull(),
            ),
        ).thenThrow(RuntimeException("network"))

        val viewModel = vm()
        viewModel.loadCatalogPostsIfNeeded(trackId = "t1")
        advanceUntilIdle()

        assertEquals("Couldn't load posts for this song.", viewModel.catalogPostsError.value)
        assertTrue(viewModel.catalogPosts.value.isEmpty())
    }
}
