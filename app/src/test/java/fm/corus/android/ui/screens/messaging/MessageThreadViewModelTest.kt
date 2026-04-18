package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MessageFailureReason
import fm.corus.android.data.model.MessageSendStatus
import fm.corus.android.data.model.MessageType
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MessageThreadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var messageRepository: MessageRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var remoteConfigService: RemoteConfigService
    private lateinit var viewModel: MessageThreadViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        messageRepository = mock {
            on { listenToMessages(any()) } doReturn emptyFlow()
        }
        authRepository = mock {
            on { currentUserId } doReturn "user1"
        }
        userRepository = mock()
        remoteConfigService = mock {
            on { giphySupport } doReturn false
        }
        viewModel = MessageThreadViewModel(
            messageRepository = messageRepository,
            authRepository = authRepository,
            userRepository = userRepository,
            remoteConfigService = remoteConfigService,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Optimistic insert ──

    @Test
    fun `sendMessage adds optimistic message with SENDING status immediately`() = runTest {
        // Suspend the repository call indefinitely so it doesn't complete
        val neverCompletes = CompletableDeferred<Unit>()
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doSuspendableAnswer { neverCompletes.await() }

        viewModel.sendMessage("thread1", "Hello")
        advanceUntilIdle()

        val messages = viewModel.messages.first()
        assertEquals(1, messages.size)

        val msg = messages[0]
        assertEquals("Hello", msg.text)
        assertEquals(MessageSendStatus.SENDING, msg.sendStatus)
        assertEquals("user1", msg.fromUserId)
        assertEquals("thread1", msg.threadId)
        assertEquals(MessageType.TEXT, msg.type)
    }

    // ── Successful send ──

    @Test
    fun `successful send removes pending message`() = runTest {
        // Repository call succeeds immediately
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doReturn(Unit)

        viewModel.sendMessage("thread1", "Hello")
        advanceUntilIdle()

        val messages = viewModel.messages.first()
        assertTrue("Pending message should be removed after successful send", messages.isEmpty())
    }

    // ── Failed send ──

    @Test
    fun `failed send sets FAILED status with generic reason`() = runTest {
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doSuspendableAnswer { throw RuntimeException("Network error") }

        viewModel.sendMessage("thread1", "Hello")
        advanceUntilIdle()

        val messages = viewModel.messages.first()
        assertEquals(1, messages.size)
        assertEquals(MessageSendStatus.FAILED, messages[0].sendStatus)
        assertEquals(MessageFailureReason.GENERIC, messages[0].failureReason)
    }

    @Test
    fun `failed send detects messaging disabled reason`() = runTest {
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doSuspendableAnswer { throw RuntimeException("This user has turned off messaging") }

        viewModel.sendMessage("thread1", "Hello")
        advanceUntilIdle()

        val messages = viewModel.messages.first()
        assertEquals(1, messages.size)
        assertEquals(MessageSendStatus.FAILED, messages[0].sendStatus)
        assertEquals(MessageFailureReason.MESSAGING_DISABLED, messages[0].failureReason)
    }

    // ── Retry ──

    @Test
    fun `retrySendMessage resets status to SENDING and re-attempts`() = runTest {
        // First send fails
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doSuspendableAnswer { throw RuntimeException("Network error") }

        viewModel.sendMessage("thread1", "Hello")
        advanceUntilIdle()

        val failedId = viewModel.messages.first()[0].id
        assertEquals(MessageSendStatus.FAILED, viewModel.messages.first()[0].sendStatus)

        // Now make retry succeed
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doReturn(Unit)

        viewModel.retrySendMessage(failedId)
        advanceUntilIdle()

        // Message should be removed after successful retry
        assertTrue(viewModel.messages.first().isEmpty())
    }

    @Test
    fun `retrySendMessage does nothing for messaging disabled`() = runTest {
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doSuspendableAnswer { throw RuntimeException("This user has turned off messaging") }

        viewModel.sendMessage("thread1", "Hello")
        advanceUntilIdle()

        val failedId = viewModel.messages.first()[0].id

        // Retry should be a no-op
        viewModel.retrySendMessage(failedId)
        advanceUntilIdle()

        // Message should still be failed
        assertEquals(MessageSendStatus.FAILED, viewModel.messages.first()[0].sendStatus)
        assertEquals(MessageFailureReason.MESSAGING_DISABLED, viewModel.messages.first()[0].failureReason)
    }

    // ── Reply context ──

    @Test
    fun `sendMessage clears reply context`() = runTest {
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doReturn(Unit)

        val replyMsg = CymbalMessage(
            id = "reply1", threadId = "thread1", fromUserId = "other",
            text = "Original message", type = MessageType.TEXT,
        )
        viewModel.setReplyTo(replyMsg)
        assertEquals(replyMsg, viewModel.replyToMessage.first())

        viewModel.sendMessage("thread1", "Reply text")
        advanceUntilIdle()

        // Reply context should be cleared after sending
        assertEquals(null, viewModel.replyToMessage.first())
    }

    @Test
    fun `optimistic message includes reply context`() = runTest {
        val neverCompletes = CompletableDeferred<Unit>()
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doSuspendableAnswer { neverCompletes.await() }

        val replyMsg = CymbalMessage(
            id = "reply1", threadId = "thread1", fromUserId = "other",
            text = "Original message", type = MessageType.TEXT,
        )
        viewModel.setReplyTo(replyMsg)
        viewModel.sendMessage("thread1", "Reply text")
        advanceUntilIdle()

        val msg = viewModel.messages.first()[0]
        assertEquals("reply1", msg.replyToMessageId)
        assertEquals("Original message", msg.replyToText)
        assertEquals("other", msg.replyToUserId)
    }

    // ── GIF optimistic send ──

    @Test
    fun `sendGifMessage adds optimistic GIF message`() = runTest {
        val neverCompletes = CompletableDeferred<Unit>()
        whenever(messageRepository.sendGifMessage(any(), any(), any(), anyOrNull()))
            .doSuspendableAnswer { neverCompletes.await() }

        viewModel.sendGifMessage("thread1", "https://media.giphy.com/test.gif")
        advanceUntilIdle()

        val messages = viewModel.messages.first()
        assertEquals(1, messages.size)
        assertEquals(MessageType.GIF, messages[0].type)
        assertEquals("https://media.giphy.com/test.gif", messages[0].mediaURL)
        assertEquals(MessageSendStatus.SENDING, messages[0].sendStatus)
    }

    // ── Image optimistic send ──

    @Test
    fun `sendImageMessage adds optimistic image message`() = runTest {
        val neverCompletes = CompletableDeferred<String>()
        whenever(messageRepository.sendImageMessage(any(), any(), any(), anyOrNull()))
            .doSuspendableAnswer { neverCompletes.await() }

        viewModel.sendImageMessage("thread1", byteArrayOf(1, 2, 3))
        advanceUntilIdle()

        val messages = viewModel.messages.first()
        assertEquals(1, messages.size)
        assertEquals(MessageType.IMAGE, messages[0].type)
        assertEquals(MessageSendStatus.SENDING, messages[0].sendStatus)
    }

    // ── Song optimistic send ──

    @Test
    fun `sendSongMessage adds optimistic SHARED_TRACK message`() = runTest {
        val neverCompletes = CompletableDeferred<Unit>()
        whenever(messageRepository.sendSharedTrackMessage(
            any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(),
        )).doSuspendableAnswer { neverCompletes.await() }

        val track = CymbalTrack(
            id = "t1",
            name = "Song Name",
            artistName = "Artist",
            albumName = "Album",
            albumArtURL = "https://img/art.jpg",
            spotifyWebURL = "https://open.spotify.com/track/t1",
        )
        viewModel.sendSongMessage("thread1", track)
        advanceUntilIdle()

        val msg = viewModel.messages.first().single()
        assertEquals(MessageType.SHARED_TRACK, msg.type)
        assertEquals(MessageSendStatus.SENDING, msg.sendStatus)
        assertEquals("Song Name", msg.trackName)
        assertEquals("Artist", msg.artistName)
        assertEquals("https://img/art.jpg", msg.albumArtURL)
        assertEquals("https://open.spotify.com/track/t1", msg.spotifyURL)
    }

    @Test
    fun `sendSongMessage removes pending on success`() = runTest {
        whenever(messageRepository.sendSharedTrackMessage(
            any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(),
        )).doReturn(Unit)

        val track = CymbalTrack(id = "t1", name = "Song", artistName = "Artist", albumName = "Album")
        viewModel.sendSongMessage("thread1", track)
        advanceUntilIdle()

        assertTrue(viewModel.messages.first().isEmpty())
    }

    @Test
    fun `sendSongMessage marks FAILED on exception`() = runTest {
        whenever(messageRepository.sendSharedTrackMessage(
            any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(),
        )).doSuspendableAnswer { throw RuntimeException("Network error") }

        val track = CymbalTrack(id = "t1", name = "Song", artistName = "Artist", albumName = "Album")
        viewModel.sendSongMessage("thread1", track)
        advanceUntilIdle()

        val msg = viewModel.messages.first().single()
        assertEquals(MessageSendStatus.FAILED, msg.sendStatus)
        assertEquals(MessageType.SHARED_TRACK, msg.type)
    }

    // ── Film optimistic send ──

    @Test
    fun `sendFilmMessage adds optimistic SHARED_FILM message`() = runTest {
        val neverCompletes = CompletableDeferred<Unit>()
        whenever(messageRepository.sendSharedFilmMessage(
            any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(),
        )).doSuspendableAnswer { neverCompletes.await() }

        val movie = CymbalMovie(
            id = "m1",
            title = "Film Title",
            directorName = "Director",
            posterURL = "https://img/poster.jpg",
            tmdbWebURL = "https://themoviedb.org/movie/m1",
        )
        viewModel.sendFilmMessage("thread1", movie)
        advanceUntilIdle()

        val msg = viewModel.messages.first().single()
        assertEquals(MessageType.SHARED_FILM, msg.type)
        assertEquals(MessageSendStatus.SENDING, msg.sendStatus)
        assertEquals("Film Title", msg.movieTitle)
        assertEquals("Director", msg.directorName)
        assertEquals("https://img/poster.jpg", msg.posterURL)
        assertEquals("https://themoviedb.org/movie/m1", msg.tmdbWebURL)
    }

    @Test
    fun `sendFilmMessage removes pending on success`() = runTest {
        whenever(messageRepository.sendSharedFilmMessage(
            any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(),
        )).doReturn(Unit)

        val movie = CymbalMovie(id = "m1", title = "Film")
        viewModel.sendFilmMessage("thread1", movie)
        advanceUntilIdle()

        assertTrue(viewModel.messages.first().isEmpty())
    }

    // ── clientMessageId passed to repository ──

    @Test
    fun `sendMessage passes clientMessageId to repository`() = runTest {
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doReturn(Unit)

        viewModel.sendMessage("thread1", "Hello")
        advanceUntilIdle()

        verify(messageRepository).sendTextMessage(
            threadId = any(),
            fromUserId = any(),
            text = any(),
            replyToMessageId = anyOrNull(),
            replyToText = anyOrNull(),
            replyToUserId = anyOrNull(),
            clientMessageId = any(),
        )
    }
}
