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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MessageThreadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var messageRepository: MessageRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var remoteConfigService: RemoteConfigService
    private lateinit var gifRepository: fm.corus.android.data.repository.GifRepository
    private lateinit var nowPlayingManager: fm.corus.android.domain.NowPlayingManager
    private lateinit var analyticsService: fm.corus.android.service.AnalyticsService
    private lateinit var viewModel: MessageThreadViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        messageRepository = mock {
            on { listenToMessages(any()) } doReturn emptyFlow()
            on { listenToGroupThreadInfo(any()) } doReturn emptyFlow()
            on { listenToThreadRow(any(), any()) } doReturn emptyFlow()
        }
        authRepository = mock {
            on { currentUserId } doReturn "user1"
        }
        userRepository = mock { on { blockedIds } doReturn kotlinx.coroutines.flow.MutableStateFlow(emptySet()) }
        remoteConfigService = mock {
            on { gifSupport } doReturn false
        }
        gifRepository = mock()
        nowPlayingManager = mock()
        analyticsService = mock()
        viewModel = MessageThreadViewModel(
            messageRepository = messageRepository,
            authRepository = authRepository,
            userRepository = userRepository,
            postRepository = mock(),
            remoteConfigService = remoteConfigService,
            gifRepository = gifRepository,
            nowPlayingManager = nowPlayingManager,
            analyticsService = analyticsService,
            context = mock(),
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

    /**
     * Regression: the send callable returning is NOT the same moment the canonical
     * Firestore doc lands in the snapshot. Dropping the optimistic copy on ack left
     * a window where the message existed in neither `pending` nor `server`, so it
     * vanished from the merged list. In the UI that shrank the reverseLayout
     * LazyColumn by one item, which re-anchored it and hid the just-sent bubble
     * behind the composer until the snapshot arrived and it animated back down
     * (the "jump" Gabe reported on device). The optimistic copy must survive until
     * the listener actually has the real message.
     */
    @Test
    fun `optimistic message stays visible between send ack and server snapshot`() = runTest {
        // Repository call succeeds immediately; no snapshot has been delivered.
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doReturn(Unit)

        viewModel.sendMessage("thread1", "Hello")
        advanceUntilIdle()

        val messages = viewModel.messages.first()
        assertEquals(
            "Message must not disappear between the send ack and the server snapshot",
            1, messages.size,
        )
        assertEquals("Hello", messages[0].text)
        // ...and the ack clears the sending clock right away (iOS parity), so a
        // lagging snapshot can't leave a delivered message spinning.
        assertEquals(MessageSendStatus.SENT, messages[0].sendStatus)
    }

    /**
     * The other half of the contract: once the canonical doc arrives (the server
     * reuses our clientMessageId as the doc id) the optimistic copy is pruned, so
     * keeping it past the ack must not leave a duplicate bubble.
     */
    @Test
    fun `server snapshot supersedes the optimistic copy without duplicating`() = runTest {
        val messagesFlow = MutableSharedFlow<List<CymbalMessage>>(extraBufferCapacity = 1)
        whenever(messageRepository.listenToMessages(any())).doReturn(messagesFlow)
        whenever(messageRepository.listenToRecipientUnreadCount(any(), any())).doReturn(emptyFlow())
        whenever(messageRepository.listenToReadReceiptsEnabled(any())).doReturn(emptyFlow())
        whenever(messageRepository.sendTextMessage(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .doReturn(Unit)

        viewModel.loadMessages("thread1", "other")
        advanceUntilIdle()

        viewModel.sendMessage("thread1", "Hello")
        advanceUntilIdle()
        val clientId = viewModel.messages.first()[0].id

        // Canonical doc lands, reusing the client id as the Firestore doc id.
        messagesFlow.emit(
            listOf(
                CymbalMessage(
                    id = clientId, threadId = "thread1", fromUserId = "user1",
                    text = "Hello", type = MessageType.TEXT,
                    sendStatus = MessageSendStatus.SENT,
                )
            )
        )
        advanceUntilIdle()

        val messages = viewModel.messages.first()
        assertEquals("Confirmed message must not be duplicated", 1, messages.size)
        assertEquals(clientId, messages[0].id)
        assertEquals(MessageSendStatus.SENT, messages[0].sendStatus)
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

        // The retry was re-attempted against the repository...
        verify(messageRepository, times(2)).sendTextMessage(
            any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), eq(failedId),
        )
        // ...and the bubble stays put until the snapshot confirms it, rather than
        // blinking out on the ack (see `optimistic message stays visible…`).
        val afterRetry = viewModel.messages.first()
        assertEquals(1, afterRetry.size)
        assertEquals(failedId, afterRetry[0].id)
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
            any(), any(), any(), any(), anyOrNull(),
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
    fun `sendSongMessage keeps pending until the snapshot confirms it`() = runTest {
        whenever(messageRepository.sendSharedTrackMessage(
            any(), any(), any(), any(), anyOrNull(),
        )).doReturn(Unit)

        val track = CymbalTrack(id = "t1", name = "Song", artistName = "Artist", albumName = "Album")
        viewModel.sendSongMessage("thread1", track)
        advanceUntilIdle()

        // Ack landed but no snapshot yet — the card must not blink out.
        val messages = viewModel.messages.first()
        assertEquals(1, messages.size)
        assertEquals(MessageType.SHARED_TRACK, messages[0].type)
    }

    @Test
    fun `sendSongMessage marks FAILED on exception`() = runTest {
        whenever(messageRepository.sendSharedTrackMessage(
            any(), any(), any(), any(), anyOrNull(),
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
            any(), any(), any(), any(), anyOrNull(),
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
    fun `sendFilmMessage keeps pending until the snapshot confirms it`() = runTest {
        whenever(messageRepository.sendSharedFilmMessage(
            any(), any(), any(), any(), anyOrNull(),
        )).doReturn(Unit)

        val movie = CymbalMovie(id = "m1", title = "Film")
        viewModel.sendFilmMessage("thread1", movie)
        advanceUntilIdle()

        // Ack landed but no snapshot yet — the card must not blink out.
        val messages = viewModel.messages.first()
        assertEquals(1, messages.size)
        assertEquals(MessageType.SHARED_FILM, messages[0].type)
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

    // ── Edit message ──

    private fun textMessage(id: String = "m1", text: String = "old") = CymbalMessage(
        id = id, threadId = "thread1", fromUserId = "user1", text = text, type = MessageType.TEXT,
    )

    /** Seed [_serverMessages] via the listener so optimistic edits are observable. */
    private fun TestScope.loadServerMessage(msg: CymbalMessage) {
        whenever(messageRepository.listenToMessages(any())).doReturn(flowOf(listOf(msg)))
        whenever(messageRepository.listenToRecipientUnreadCount(any(), any())).doReturn(emptyFlow())
        whenever(messageRepository.listenToReadReceiptsEnabled(any())).doReturn(emptyFlow())
        viewModel.loadMessages("thread1", "other")
        advanceUntilIdle()
    }

    // ── Instant header seed from the inbox cache (iOS parity) ──

    @Test
    fun `loadMessages seeds 1-1 header from cached inbox immediately`() = runTest {
        // Profile fetch is suspended so it can't be the source of the header values.
        val neverCompletes = CompletableDeferred<fm.corus.android.data.model.CymbalUser?>()
        whenever(userRepository.fetchUserProfile(any())).doSuspendableAnswer { neverCompletes.await() }
        whenever(messageRepository.listenToRecipientUnreadCount(any(), any())).doReturn(emptyFlow())
        whenever(messageRepository.listenToReadReceiptsEnabled(any())).doReturn(emptyFlow())

        val other = fm.corus.android.data.model.CymbalUser(
            id = "other", username = "arielle", displayName = "arielle lana",
            avatarURL = "https://img/a.jpg", avatarThumbURL = "https://img/a_thumb.jpg",
        )
        val cachedThread = fm.corus.android.data.model.CymbalThread(
            id = "thread1", otherUser = other, otherUserId = "other",
        )
        whenever(messageRepository.cachedInbox).doReturn(
            MessageRepository.CachedInbox("user1", listOf(cachedThread), null, false)
        )

        // No advanceUntilIdle: the seed must be synchronous, before the async load runs.
        viewModel.loadMessages("thread1", "other")

        assertEquals("arielle", viewModel.otherUsername.first())
        assertEquals("arielle lana", viewModel.otherDisplayName.first())
        assertEquals("https://img/a.jpg", viewModel.otherAvatarURL.first())
        assertEquals("https://img/a_thumb.jpg", viewModel.otherAvatarThumbURL.first())
    }

    @Test
    fun `loadMessages seeds group header from cached inbox immediately`() = runTest {
        whenever(messageRepository.listenToRecipientUnreadCount(any(), any())).doReturn(emptyFlow())
        whenever(messageRepository.listenToReadReceiptsEnabled(any())).doReturn(emptyFlow())

        val m1 = fm.corus.android.data.model.CymbalUser(id = "m1", username = "arielle", displayName = "arielle lana")
        val m2 = fm.corus.android.data.model.CymbalUser(id = "m2", username = "aiden", displayName = "Aiden Baxter")
        val cachedGroup = fm.corus.android.data.model.CymbalThread(
            id = "grp1", otherUserId = "", isGroup = true, groupName = "corus squad",
            memberIds = listOf("user1", "m1", "m2"), members = listOf(m1, m2),
        )
        whenever(messageRepository.cachedInbox).doReturn(
            MessageRepository.CachedInbox("user1", listOf(cachedGroup), null, false)
        )

        // Group threads pass an empty otherUserId; seed must still fill the header.
        viewModel.loadMessages("grp1", "")

        val info = viewModel.groupInfo.first()
        assertEquals(true, info?.isGroup)
        assertEquals("corus squad", info?.name)
        assertEquals(listOf("user1", "m1", "m2"), info?.memberIds)
        assertEquals(setOf("m1", "m2"), viewModel.membersById.first().keys)
    }

    // ── Initial-load spinner (blank-page regression) ──

    @Test
    fun `isLoading stays true after load until the first message snapshot arrives`() = runTest {
        // A hot flow we control so the listener is attached but hasn't delivered a
        // snapshot yet — the state the DM thread showed as a blank white page.
        val messagesFlow = MutableSharedFlow<List<CymbalMessage>>(extraBufferCapacity = 1)
        whenever(messageRepository.listenToMessages(any())).doReturn(messagesFlow)
        whenever(messageRepository.listenToRecipientUnreadCount(any(), any())).doReturn(emptyFlow())
        whenever(messageRepository.listenToReadReceiptsEnabled(any())).doReturn(emptyFlow())

        viewModel.loadMessages("thread1", "other")
        advanceUntilIdle()

        // Setup finished (profile fetch, mark-read) but no snapshot yet → still loading.
        assertTrue("spinner should stay up until messages are ready", viewModel.isLoading.first())

        // The first snapshot lands (even empty) → messages are ready, drop the spinner.
        messagesFlow.emit(emptyList())
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoading.first())
    }

    @Test
    fun `isLoading clears when thread setup fails before the listener starts`() = runTest {
        // No signed-in user + a blank threadId forces the getOrCreateThread path,
        // which throws before startListening — the spinner must still come down.
        whenever(authRepository.currentUserId).doReturn(null)

        viewModel.loadMessages("", "other")
        advanceUntilIdle()

        assertEquals(false, viewModel.isLoading.first())
    }

    @Test
    fun `startEditing sets editing target and clears reply`() = runTest {
        viewModel.setReplyTo(CymbalMessage(id = "r1", threadId = "thread1", fromUserId = "other", text = "hi", type = MessageType.TEXT))
        val target = textMessage()
        viewModel.startEditing(target)
        assertEquals(target, viewModel.editingMessage.first())
        assertEquals(null, viewModel.replyToMessage.first())
    }

    @Test
    fun `setReplyTo clears editing target`() = runTest {
        viewModel.startEditing(textMessage())
        val reply = CymbalMessage(id = "r1", threadId = "thread1", fromUserId = "other", text = "hi", type = MessageType.TEXT)
        viewModel.setReplyTo(reply)
        assertEquals(null, viewModel.editingMessage.first())
        assertEquals(reply, viewModel.replyToMessage.first())
    }

    @Test
    fun `cancelEditing clears editing target`() = runTest {
        viewModel.startEditing(textMessage())
        viewModel.cancelEditing()
        assertEquals(null, viewModel.editingMessage.first())
    }

    @Test
    fun `editMessage with changed text calls repository and clears editing`() = runTest {
        whenever(messageRepository.editMessage(any(), any(), any())).doReturn(Unit)
        viewModel.startEditing(textMessage(id = "m1", text = "old"))

        viewModel.editMessage("thread1", "new text")
        advanceUntilIdle()

        verify(messageRepository).editMessage(eq("thread1"), eq("m1"), eq("new text"))
        assertEquals(null, viewModel.editingMessage.first())
    }

    @Test
    fun `editMessage with unchanged text does not call repository`() = runTest {
        viewModel.startEditing(textMessage(text = "same"))

        viewModel.editMessage("thread1", "same")
        advanceUntilIdle()

        verify(messageRepository, never()).editMessage(any(), any(), any())
        assertEquals(null, viewModel.editingMessage.first())
    }

    @Test
    fun `editMessage with blank text does not call repository`() = runTest {
        viewModel.startEditing(textMessage(text = "old"))

        viewModel.editMessage("thread1", "   ")
        advanceUntilIdle()

        verify(messageRepository, never()).editMessage(any(), any(), any())
    }

    @Test
    fun `editMessage optimistically updates text and marks edited`() = runTest {
        whenever(messageRepository.editMessage(any(), any(), any())).doReturn(Unit)
        val target = textMessage(id = "m1", text = "old")
        loadServerMessage(target)

        viewModel.startEditing(target)
        viewModel.editMessage("thread1", "new text")
        advanceUntilIdle()

        val msg = viewModel.messages.first().single()
        assertEquals("new text", msg.text)
        assertTrue("message should be marked edited", msg.isEdited)
    }

    @Test
    fun `failed editMessage reverts text and re-opens editor`() = runTest {
        whenever(messageRepository.editMessage(any(), any(), any()))
            .doSuspendableAnswer { throw RuntimeException("Network error") }
        val target = textMessage(id = "m1", text = "old")
        loadServerMessage(target)

        viewModel.startEditing(target)
        viewModel.editMessage("thread1", "new text")
        advanceUntilIdle()

        val msg = viewModel.messages.first().single()
        assertEquals("old", msg.text)
        assertTrue("edited marker should be reverted", !msg.isEdited)
        assertEquals(target, viewModel.editingMessage.first())
    }
}
