package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FilmDetailViewModelShareTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var postRepository: fm.corus.android.data.repository.PostRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var userRepository: UserRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: FilmDetailViewModel

    private val movie = CymbalMovie(id = "tmdb_9", title = "Whiplash", directorName = "Damien Chazelle")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        postRepository = mock()
        messageRepository = mock()
        userRepository = mock()
        authRepository = mock { on { currentUserId } doReturn "me" }
        viewModel = FilmDetailViewModel(
            postRepository = postRepository,
            nowPlayingManager = mock(),
            analyticsService = mock(),
            commentEditedEvent = mock { on { events } doReturn kotlinx.coroutines.flow.MutableSharedFlow<fm.corus.android.domain.CommentEditedEvent.Payload>() },
            commentDeletedEvent = mock { on { events } doReturn kotlinx.coroutines.flow.MutableSharedFlow<fm.corus.android.domain.CommentDeletedEvent.Payload>() },
            authRepository = authRepository,
            userRepository = userRepository,
            messageRepository = messageRepository,
            cloudFunctions = mock(),
            context = mock(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendFilmToUser sends a sharedFilm message on the created thread`() = runTest {
        whenever(messageRepository.getOrCreateThread(eq("me"), eq("friend"))).doReturn("thread1")

        viewModel.sendFilmToUser("friend", movie, "  loved it  ")
        advanceUntilIdle()

        verify(messageRepository).getOrCreateThread("me", "friend")
        verify(messageRepository).sendSharedFilmMessage(
            threadId = eq("thread1"),
            fromUserId = eq("me"),
            text = eq("loved it"),
            movie = eq(movie),
            clientMessageId = anyOrNull(),
        )
    }

    @Test
    fun `sendFilmToUser no-ops when signed out`() = runTest {
        whenever(authRepository.currentUserId).doReturn(null)

        viewModel.sendFilmToUser("friend", movie, "x")
        advanceUntilIdle()

        verify(messageRepository, never()).getOrCreateThread(any(), any())
    }
}
