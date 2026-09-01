package fm.corus.android.ui.screens.profile

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.SubscriptionRepository
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
import org.junit.Assert.assertEquals
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
class ProfileOptimisticMergeTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cloudFunctions: CloudFunctionsDataSource

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cloudFunctions = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun user(id: String = "me") = CymbalUser(id = id, username = "u", displayName = "U")

    private fun post(id: String, ageDays: Int, author: CymbalUser = user()): CymbalPost {
        val dayMs = 86_400_000L
        return CymbalPost(
            id = id,
            user = author,
            track = CymbalTrack(id = "t-$id", name = "T $id", artistName = "A", albumName = "Al"),
            timestamp = Date(2_000_000_000_000L - ageDays * dayMs),
            mediaType = MediaType.TRACK,
        )
    }

    private fun createViewModel(): ProfileViewModel = ProfileViewModel(
        context = mock(),
        authRepository = mock {
            on { currentUserId } doReturn "me"
            on { userProfile } doReturn MutableStateFlow<CymbalUser?>(user())
        },
        cloudFunctions = cloudFunctions,
        userRepository = mock(),
        messageRepository = mock(),
        subscriptionRepository = mock {
            on { isClubMember } doReturn MutableStateFlow(false)
            on { hasFullAccessFlow } doReturn MutableStateFlow(false)
        },
        nowPlayingManager = mock(),
        engagementManager = mock {
            on { states } doReturn MutableStateFlow(emptyMap())
        },
        postCreationEvent = mock {
            on { events } doReturn MutableSharedFlow()
        },
        postDeletionEvent = mock {
            on { events } doReturn MutableSharedFlow()
        },
        commentEditedEvent = fm.corus.android.domain.CommentEditedEvent(),
        commentDeletedEvent = fm.corus.android.domain.CommentDeletedEvent(),
        saveChangedEvent = fm.corus.android.domain.SaveChangedEvent(),
        analyticsService = mock(),
        musicServicePreference = mock(),
        remoteConfigService = mock(),
        networkMonitor = mock { on { isConnected } doReturn MutableStateFlow(true) },
        ownProfileLaunchCache = OwnProfileLaunchCache(cloudFunctions),
    )

    @Test
    fun emptyLocalKeepsIncomingPage() {
        val incoming = listOf(post("old", 1))
        assertEquals(
            listOf("old"),
            mergeProfilePostsPreservingOptimistic(incoming, emptyList()).map { it.id },
        )
    }

    @Test
    fun newerLocalPostLandsInFrontOfLaunchSnapshot() {
        val incoming = listOf(post("old", 1), post("older", 2))
        val local = listOf(post("new", 0))
        assertEquals(
            listOf("new", "old", "older"),
            mergeProfilePostsPreservingOptimistic(incoming, local).map { it.id },
        )
    }

    @Test
    fun duplicateIdDoesNotDoubleThePost() {
        val incoming = listOf(post("new", 0), post("old", 1))
        val local = listOf(post("new", 0))
        assertEquals(
            listOf("new", "old"),
            mergeProfilePostsPreservingOptimistic(incoming, local).map { it.id },
        )
    }

    @Test
    fun olderLocalLeftoversAreNotPrepended() {
        val incoming = listOf(post("old", 1))
        val local = listOf(post("ancient", 30))
        assertEquals(
            listOf("old"),
            mergeProfilePostsPreservingOptimistic(incoming, local).map { it.id },
        )
    }

    @Test
    fun launchCacheStoreAfterPrependKeepsTheNewPost() {
        val cache = OwnProfileLaunchCache(cloudFunctions)
        val me = user()
        cache.prependOptimistic(post("new", 0, me))
        cache.store("me", listOf(post("old", 1, me)))
        assertEquals(listOf("new", "old"), cache.peek("me")?.map { it.id })
    }

    @Test
    fun launchCachePrependAfterStoreKeepsTheNewPost() {
        val cache = OwnProfileLaunchCache(cloudFunctions)
        val me = user()
        cache.store("me", listOf(post("old", 1, me)))
        cache.prependOptimistic(post("new", 0, me))
        assertEquals(listOf("new", "old"), cache.peek("me")?.map { it.id })
    }

    @Test
    fun insertThenFirstLoadKeepsTheOptimisticFeaturedPost() = runTest {
        val me = user()
        whenever(
            cloudFunctions.getProfilePosts(eq("me"), eq("me"), any(), eq(null), eq(null)),
        ).thenReturn(listOf(post("old", 1, me)))
        whenever(cloudFunctions.getLinkedArtistForUser(any())).thenReturn(null)

        val vm = createViewModel()
        vm.insertOptimisticPost(post("new", 0, me))
        vm.loadProfile()
        advanceUntilIdle()

        assertEquals(listOf("new", "old"), vm.posts.value.map { it.id })
    }
}
