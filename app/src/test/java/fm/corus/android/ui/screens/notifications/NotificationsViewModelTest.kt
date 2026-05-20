package fm.corus.android.ui.screens.notifications

import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.NotificationType
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.NotificationRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
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
        }
        postRepository = mock()
        engagementManager = mock()
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

    // ── Auto mark-all-read ──

    @Test
    fun `loadNotifications auto-marks all notifications as read`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadNotifications()
        advanceUntilIdle()

        verify(notificationRepository).markAllRead(eq("user1"))
    }

    @Test
    fun `loadNotifications does not mark-all-read when user is signed out`() = runTest {
        val signedOutAuth = mock<AuthRepository> {
            on { currentUserId } doReturn null
        }
        val viewModel = NotificationsViewModel(
            notificationRepository = notificationRepository,
            authRepository = signedOutAuth,
            userRepository = userRepository,
            postRepository = postRepository,
            engagementManager = engagementManager,
            nowPlayingManager = mock(),
            analyticsService = mock(),
            remoteConfigService = mock(),
            gifRepository = mock(),
            networkMonitor = mock { on { isConnected } doReturn MutableStateFlow(true) },
            context = mock(),
        )

        viewModel.loadNotifications()
        advanceUntilIdle()

        verify(notificationRepository, never()).markAllRead(any())
    }

    @Test
    fun `loadNotifications is idempotent across multiple calls`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadNotifications()
        viewModel.loadNotifications()
        viewModel.loadNotifications()
        advanceUntilIdle()

        // Only the first call should trigger the mark-all-read side effect,
        // matching the `hasStartedLoading` guard.
        verify(notificationRepository, times(1)).markAllRead(eq("user1"))
    }

    // ── lastSeenNotificationsAt stamping ──

    @Test
    fun `loadNotifications stamps lastSeenNotificationsAt`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadNotifications()
        advanceUntilIdle()

        verify(notificationRepository).updateLastSeenNotificationsAt(eq("user1"))
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
                parentCommentId = any(),
                replyToUserId = any(),
                gifURL = any(),
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
            postId = any(), userId = any(), text = any(),
            parentCommentId = any(), replyToUserId = any(), gifURL = any(),
        )
    }
}
