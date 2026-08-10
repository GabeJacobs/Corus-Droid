package fm.corus.android.ui.screens.explore

import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.domain.CommentDeletedEvent
import fm.corus.android.domain.CommentEditedEvent
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class HashtagFeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var postRepository: PostRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var firestoreDataSource: FirestoreDataSource
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var analyticsService: AnalyticsService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        postRepository = mock()
        authRepository = mock {
            on { currentUserId } doReturn "user1"
        }
        firestoreDataSource = mock()
        nowPlayingManager = mock()
        analyticsService = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HashtagFeedViewModel = HashtagFeedViewModel(
        postRepository = postRepository,
        authRepository = authRepository,
        firestoreDataSource = firestoreDataSource,
        commentEditedEvent = CommentEditedEvent(),
        commentDeletedEvent = CommentDeletedEvent(),
        nowPlayingManager = nowPlayingManager,
        musicServicePreference = mock(),
        subscriptionRepository = mock(),
        analyticsService = analyticsService,
    )

    @Test
    fun `generateHashtagPlaylist delegates to NowPlayingManager with the tag`() = runTest {
        val viewModel = createViewModel()

        viewModel.generateHashtagPlaylist("indierock")
        advanceUntilIdle()

        verify(nowPlayingManager).generateHashtagPlaylist(eq("indierock"), fullExport = eq(false))
    }

    @Test
    fun `generateHashtagPlaylist propagates fullExport`() = runTest {
        // The chooser's "All N songs" action must lift the 75-track snapshot cap
        // server-side — a dropped flag would silently truncate the export.
        val viewModel = createViewModel()

        viewModel.generateHashtagPlaylist("indierock", fullExport = true)
        advanceUntilIdle()

        verify(nowPlayingManager).generateHashtagPlaylist(eq("indierock"), fullExport = eq(true))
    }

    @Test
    fun `generateHashtagPlaylist logs the tap with the tag`() = runTest {
        val viewModel = createViewModel()

        viewModel.generateHashtagPlaylist("indierock")
        advanceUntilIdle()

        verify(analyticsService).logHashtagPlaylistTapped("indierock")
    }
}
