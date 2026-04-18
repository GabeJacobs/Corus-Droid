package fm.corus.android.ui.screens.profile

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var userRepository: UserRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var engagementManager: PostEngagementManager
    private lateinit var postCreationEvent: PostCreationEvent

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn MutableStateFlow<CymbalUser?>(null)
        }
        cloudFunctions = mock()
        userRepository = mock()
        subscriptionRepository = mock {
            on { isClubMember } doReturn MutableStateFlow(false)
            on { hasFullAccessFlow } doReturn MutableStateFlow(false)
        }
        nowPlayingManager = mock()
        engagementManager = mock()
        postCreationEvent = mock {
            on { events } doReturn MutableSharedFlow()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProfileViewModel = ProfileViewModel(
        authRepository = authRepository,
        cloudFunctions = cloudFunctions,
        userRepository = userRepository,
        subscriptionRepository = subscriptionRepository,
        nowPlayingManager = nowPlayingManager,
        engagementManager = engagementManager,
        postCreationEvent = postCreationEvent,
    )

    @Test
    fun `uploadAvatar exposes pending bytes synchronously before the upload runs`() = runTest {
        whenever(userRepository.uploadAvatar(any(), any())).thenReturn("https://example.com/a.jpg")
        val viewModel = createViewModel()
        val bytes = byteArrayOf(1, 2, 3, 4)

        // StandardTestDispatcher defers the launched body until advance, so this asserts
        // the state observable on the same frame as the user's tap-to-confirm.
        viewModel.uploadAvatar(bytes)
        assertArrayEquals(bytes, viewModel.pendingAvatarBytes.value)

        advanceUntilIdle()
        assertNull(viewModel.pendingAvatarBytes.value)
    }

}
