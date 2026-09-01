package fm.corus.android.ui.screens.notifications

import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.NotificationType
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.NotificationRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.CommentLikeChangedEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var postRepository: PostRepository
    private lateinit var engagementManager: PostEngagementManager
    private lateinit var commentLikeChangedEvent: CommentLikeChangedEvent

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        notificationRepository = mock {
            on { observeNotifications(any(), any()) } doReturn emptyFlow()
        }
        authRepository = mock {
            on { currentUserId } doReturn "user1"
        }
        userRepository = mock {
            on { followingIds } doReturn MutableStateFlow<Set<String>>(emptySet())
            on { hiddenUserIds } doReturn MutableStateFlow<Set<String>>(emptySet())
        }
        postRepository = mock()
        engagementManager = mock()
        commentLikeChangedEvent = CommentLikeChangedEvent()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): NotificationsViewModel = NotificationsViewModel(
        notificationRepository = notificationRepository,
        authRepository = authRepository,
        userRepository = userRepository,
        postRepository = postRepository,
        commentLikeChangedEvent = commentLikeChangedEvent,
        engagementManager = engagementManager,
        nowPlayingManager = mock(),
        analyticsService = mock(),
        remoteConfigService = mock(),
        gifRepository = mock(),
        networkMonitor = mock { on { isConnected } doReturn MutableStateFlow(true) },
        context = mock(),
    )

    private fun commentNotification(
        id: String = "n1",
        commentId: String = "c1",
        postId: String = "p1",
        fromUserId: String = "u2",
        type: NotificationType = NotificationType.COMMENT,
    ) = CymbalNotification(
        id = id,
        type = type,
        fromUser = CymbalUser(id = fromUserId, username = "u", displayName = "U"),
        postId = postId,
        commentId = commentId,
    )

    // ── Mark-as-viewed is driven by tab activation, NOT by loadNotifications ──
    //
    // Regression context: all tab screens stay composed. The list load is
    // gated on the Activity tab being selected; the previous design marked
    // everything read in loadNotifications, so a launch-time load cleared
    // the badge before the user visited Activity. The mark-read now lives
    // in markActivityViewed, bumped by the tab-activation trigger on every
    // visit. The tab-bar badge is UnreadCountsRepository (starts at sign-in).

    @Test
    fun `loadNotifications does NOT mark read or stamp lastSeen (no premature clear at launch)`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadNotifications()
        advanceUntilIdle()

        // loadNotifications is launch-time list setup only — clearing the badge
        // here would dismiss notifications the user hasn't viewed yet.
        verify(notificationRepository, never()).markAllRead(any())
        verify(notificationRepository, never()).updateLastSeenNotificationsAt(any())
    }

    @Test
    fun `markActivityViewed marks all read and stamps lastSeen`() = runTest {
        val viewModel = createViewModel()

        viewModel.markActivityViewed()
        advanceUntilIdle()

        verify(notificationRepository).markAllRead(eq("user1"))
        verify(notificationRepository).updateLastSeenNotificationsAt(eq("user1"))
    }

    @Test
    fun `markActivityViewed marks read on EVERY visit, not just the first`() = runTest {
        // This is the core fix: each Activity-tab entry must clear the badge for
        // real. Three visits => three markAllRead writes.
        val viewModel = createViewModel()

        viewModel.markActivityViewed()
        viewModel.markActivityViewed()
        viewModel.markActivityViewed()
        advanceUntilIdle()

        verify(notificationRepository, times(3)).markAllRead(eq("user1"))
        verify(notificationRepository, times(3)).updateLastSeenNotificationsAt(eq("user1"))
    }

    @Test
    fun `markActivityViewed does nothing when user is signed out`() = runTest {
        val signedOutAuth = mock<AuthRepository> {
            on { currentUserId } doReturn null
        }
        val viewModel = NotificationsViewModel(
            notificationRepository = notificationRepository,
            authRepository = signedOutAuth,
            userRepository = userRepository,
            postRepository = postRepository,
            commentLikeChangedEvent = commentLikeChangedEvent,
            engagementManager = engagementManager,
            nowPlayingManager = mock(),
            analyticsService = mock(),
            remoteConfigService = mock(),
            gifRepository = mock(),
            networkMonitor = mock { on { isConnected } doReturn MutableStateFlow(true) },
            context = mock(),
        )

        viewModel.markActivityViewed()
        advanceUntilIdle()

        verify(notificationRepository, never()).markAllRead(any())
        verify(notificationRepository, never()).updateLastSeenNotificationsAt(any())
    }

    // ── Comment-like state syncs from the post detail without a refetch ──
    //
    // Repro of the iOS-reported bug: open a comment notification, like the
    // comment inside the pushed post detail / comments sheet, swipe back — the
    // Activity row heart stayed empty because the Notifications VM keeps its own
    // likedCommentIds cache that only refreshed on a notifications refetch (which
    // a busy account masked via incidental listener fires). The post-detail
    // toggle now emits CommentLikeChangedEvent; the Notifications VM collects it.
    @Test
    fun `comment like event updates likedCommentIds without a notifications refetch`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle() // let the event collector subscribe before we emit

        commentLikeChangedEvent.notifyCommentLikeChanged(postId = "p1", commentId = "c1", isLiked = true)
        advanceUntilIdle()
        assertEquals(setOf("c1"), viewModel.likedCommentIds.value)

        // Unliking it from the post detail clears the row heart too.
        commentLikeChangedEvent.notifyCommentLikeChanged(postId = "p1", commentId = "c1", isLiked = false)
        advanceUntilIdle()
        assertEquals(emptySet<String>(), viewModel.likedCommentIds.value)
    }

    @Test
    fun `loadNotifications is idempotent across multiple calls`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadNotifications()
        viewModel.loadNotifications()
        viewModel.loadNotifications()
        advanceUntilIdle()

        // The list listener is set up only once, matching the `hasStartedLoading`
        // guard — observeNotifications must not be re-subscribed per call.
        verify(notificationRepository, times(1)).observeNotifications(eq("user1"), any())
    }

    @Test
    fun `loadNotifications retries once auth is ready`() = runTest {
        whenever(authRepository.currentUserId).thenReturn(null)
        val viewModel = createViewModel()

        viewModel.loadNotifications()
        advanceUntilIdle()
        verify(notificationRepository, never()).observeNotifications(any(), any())

        whenever(authRepository.currentUserId).thenReturn("user1")
        viewModel.loadNotifications()
        advanceUntilIdle()
        verify(notificationRepository, times(1)).observeNotifications(eq("user1"), any())
    }

    // ── newNotificationIds client-side set ──

    @Test
    fun `flags notifications newer than lastSeen as new`() = runTest {
        val oldNotif = commentNotification(id = "old").copy(timestamp = java.util.Date(1_000L))
        val newNotif = commentNotification(id = "new").copy(timestamp = java.util.Date(5_000L))
        whenever(notificationRepository.fetchLastSeenNotificationsAt(eq("user1"))) doReturn 2_000L
        whenever(notificationRepository.observeNotifications(any(), any())) doReturn
            flowOf(listOf(newNotif, oldNotif))

        val viewModel = createViewModel()
        viewModel.loadNotifications()
        advanceUntilIdle()

        val ids = viewModel.newNotificationIds.first()
        assertEquals(setOf("new"), ids)
    }

    @Test
    fun `does not re-flag a read notification even if its timestamp is newer than cutoff`() = runTest {
        // Regression: after the user taps a notification (persists isRead=true) and
        // relaunches, the notification is still newer than the previous lastSeen
        // cutoff — but it must not be highlighted as "new" again.
        val readButNewer = commentNotification(id = "read-newer")
            .copy(timestamp = java.util.Date(5_000L), isRead = true)
        val unreadNewer = commentNotification(id = "unread-newer")
            .copy(timestamp = java.util.Date(6_000L), isRead = false)
        whenever(notificationRepository.fetchLastSeenNotificationsAt(eq("user1"))) doReturn 2_000L
        whenever(notificationRepository.observeNotifications(any(), any())) doReturn
            flowOf(listOf(unreadNewer, readButNewer))

        val viewModel = createViewModel()
        viewModel.loadNotifications()
        advanceUntilIdle()

        val ids = viewModel.newNotificationIds.first()
        assertEquals(setOf("unread-newer"), ids)
    }

    @Test
    fun `falls back to isRead flag when no prior lastSeen`() = runTest {
        val unread = commentNotification(id = "unread").copy(isRead = false)
        val read = commentNotification(id = "read").copy(isRead = true)
        whenever(notificationRepository.fetchLastSeenNotificationsAt(eq("user1"))) doReturn null
        whenever(notificationRepository.observeNotifications(any(), any())) doReturn
            flowOf(listOf(unread, read))

        val viewModel = createViewModel()
        viewModel.loadNotifications()
        advanceUntilIdle()

        val ids = viewModel.newNotificationIds.first()
        assertEquals(setOf("unread"), ids)
    }

    @Test
    fun `markNotificationTapped removes id from new set and persists isRead`() = runTest {
        val n = commentNotification(id = "n1").copy(isRead = false)
        whenever(notificationRepository.fetchLastSeenNotificationsAt(any())) doReturn null
        whenever(notificationRepository.observeNotifications(any(), any())) doReturn
            flowOf(listOf(n))

        val viewModel = createViewModel()
        viewModel.loadNotifications()
        advanceUntilIdle()
        assertTrue(viewModel.newNotificationIds.first().contains("n1"))

        viewModel.markNotificationTapped("n1")
        advanceUntilIdle()

        assertTrue(!viewModel.newNotificationIds.first().contains("n1"))
        verify(notificationRepository).markNotificationRead(eq("n1"))
    }

    // ── Comment like / reply actions on notification rows ──

    @Test
    fun `toggleCommentLike optimistically adds id and calls likeComment`() = runTest {
        val viewModel = createViewModel()
        val notif = commentNotification()

        viewModel.toggleCommentLike(notif)
        assertTrue(viewModel.likedCommentIds.value.contains("c1"))

        advanceUntilIdle()

        verify(postRepository).likeComment(eq("user1"), eq("p1"), eq("c1"))
        verify(postRepository, never()).unlikeComment(any(), any(), any())
    }

    @Test
    fun `toggleCommentLike on already-liked comment calls unlikeComment and clears id`() = runTest {
        val viewModel = createViewModel()
        val notif = commentNotification()

        viewModel.toggleCommentLike(notif)
        advanceUntilIdle()
        viewModel.toggleCommentLike(notif)
        advanceUntilIdle()

        assertTrue(!viewModel.likedCommentIds.value.contains("c1"))
        verify(postRepository).unlikeComment(eq("user1"), eq("p1"), eq("c1"))
    }

    @Test
    fun `setReplyingToNotification updates flow`() = runTest {
        val viewModel = createViewModel()
        val notif = commentNotification()

        viewModel.setReplyingToNotification(notif)
        assertEquals("n1", viewModel.replyingToNotification.value?.id)

        viewModel.setReplyingToNotification(null)
        assertEquals(null, viewModel.replyingToNotification.value)
    }

    @Test
    fun `sendReply posts a comment with parentCommentId and replyToUserId then clears replyingTo`() = runTest {
        val viewModel = createViewModel()
        val notif = commentNotification()
        whenever(
            postRepository.addComment(
                postId = any(),
                userId = any(),
                text = any(),
                parentCommentId = anyOrNull(),
                replyToUserId = anyOrNull(),
                gifURL = anyOrNull(),
                attachedSong = anyOrNull(),
                attachedFilm = anyOrNull(),
                attachedArtist = anyOrNull(),
                attachedAlbum = anyOrNull(),
                attachedDirector = anyOrNull(),
            )
        ).thenReturn("newId")

        viewModel.setReplyingToNotification(notif)
        viewModel.sendReply("hello")
        advanceUntilIdle()

        verify(postRepository).addComment(
            postId = eq("p1"),
            userId = eq("user1"),
            text = eq("hello"),
            parentCommentId = eq("c1"),
            replyToUserId = eq("u2"),
            gifURL = eq(null),
            attachedSong = eq(null),
            attachedFilm = eq(null),
            attachedArtist = eq(null),
            attachedAlbum = eq(null),
            attachedDirector = eq(null),
        )
        verify(engagementManager).incrementCommentCount(eq("p1"))
        assertEquals(null, viewModel.replyingToNotification.value)
    }

    @Test
    fun `sendReply ignores blank text`() = runTest {
        val viewModel = createViewModel()
        viewModel.setReplyingToNotification(commentNotification())

        viewModel.sendReply("   ")
        advanceUntilIdle()

        verify(postRepository, never()).addComment(
            postId = any(),
            userId = any(),
            text = any(),
            parentCommentId = anyOrNull(),
            replyToUserId = anyOrNull(),
            gifURL = anyOrNull(),
            attachedSong = anyOrNull(),
            attachedFilm = anyOrNull(),
            attachedArtist = anyOrNull(),
            attachedAlbum = anyOrNull(),
            attachedDirector = anyOrNull(),
        )
    }
}
