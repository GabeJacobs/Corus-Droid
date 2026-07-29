package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SongDetailViewModelShareTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var postRepository: fm.corus.android.data.repository.PostRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var userRepository: UserRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: SongDetailViewModel

    private val track = CymbalTrack(id = "t1", name = "Love Story", artistName = "My New Band Believe", albumName = "Album")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        postRepository = mock()
        messageRepository = mock()
        userRepository = mock()
        authRepository = mock { on { currentUserId } doReturn "me" }
        viewModel = SongDetailViewModel(
            postRepository = postRepository,
            nowPlayingManager = mock(),
            analyticsService = mock(),
            commentEditedEvent = mock { on { events } doReturn kotlinx.coroutines.flow.MutableSharedFlow<fm.corus.android.domain.CommentEditedEvent.Payload>() },
            commentDeletedEvent = mock { on { events } doReturn kotlinx.coroutines.flow.MutableSharedFlow<fm.corus.android.domain.CommentDeletedEvent.Payload>() },
            cloudFunctions = mock(),
            remoteConfigService = mock(),
            preferencesDataStore = mock(),
            musicServicePreference = mock(),
            authRepository = authRepository,
            userRepository = userRepository,
            messageRepository = messageRepository,
            context = mock(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendTrackToUser sends a sharedTrack message on the created thread`() = runTest {
        whenever(messageRepository.getOrCreateThread(eq("me"), eq("friend"))).doReturn("thread1")

        viewModel.sendTrackToUser("friend", track, "  check this out  ")
        advanceUntilIdle()

        verify(messageRepository).getOrCreateThread("me", "friend")
        verify(messageRepository).sendSharedTrackMessage(
            threadId = eq("thread1"),
            fromUserId = eq("me"),
            text = eq("check this out"),
            track = eq(track),
            clientMessageId = anyOrNull(),
        )
    }

    @Test
    fun `sendTrackToUser no-ops when signed out`() = runTest {
        whenever(authRepository.currentUserId).doReturn(null)

        viewModel.sendTrackToUser("friend", track, "hi")
        advanceUntilIdle()

        verify(messageRepository, never()).getOrCreateThread(any(), any())
        verify(messageRepository, never()).sendSharedTrackMessage(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `searchShareUsers clears results on blank query without hitting the repo`() = runTest {
        viewModel.searchShareUsers("   ")
        advanceUntilIdle()

        assertTrue(viewModel.shareSearchResults.first().isEmpty())
        assertTrue(!viewModel.isShareSearching.first())
        verify(userRepository, never()).searchUsers(any(), any(), any())
    }

    @Test
    fun `searchShareUsers debounces and publishes results`() = runTest {
        val results = listOf(CymbalUser(id = "u1", username = "alice", displayName = "Alice"))
        whenever(userRepository.searchUsers(eq("ali"), any(), any())).doReturn(results)

        viewModel.searchShareUsers("ali")
        advanceUntilIdle()

        assertEquals(results, viewModel.shareSearchResults.first())
        assertTrue(!viewModel.isShareSearching.first())
    }

    @Test
    fun `loadRecentShareContacts skips follow-graph fetch when shares plus DMs fill the grid`() = runTest {
        val recipients = (1..SHARE_CONTACTS_TARGET).map { CymbalUser(id = "r$it", username = "r$it", displayName = "R$it") }
        whenever(messageRepository.listShareRecipients(any())).doReturn(recipients)
        whenever(messageRepository.listThreads(eq("me"))).doReturn(emptyList())

        viewModel.loadRecentShareContacts()
        advanceUntilIdle()

        assertEquals(SHARE_CONTACTS_TARGET, viewModel.recentShareContacts.first().size)
        assertTrue(!viewModel.isLoadingShareContacts.first())
        verify(userRepository, never()).fetchFollowingPaginated(any(), any(), anyOrNull())
        verify(userRepository, never()).fetchFollowersPaginated(any(), any(), anyOrNull())
    }
}
