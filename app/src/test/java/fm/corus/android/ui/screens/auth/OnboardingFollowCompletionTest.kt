package fm.corus.android.ui.screens.auth

import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFollowCompletionTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `completion waits for a failed pending follow and logs zero`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val users = mock<UserRepository> {
            onBlocking { followUser("viewer", "target") } doSuspendableAnswer {
                gate.await()
                throw IllegalStateException("test write failure")
            }
        }
        val analytics = mock<AnalyticsService>()
        val auth = mock<AuthRepository> { on { currentUserId } doReturn "viewer" }
        val vm = SocialSetupViewModel(authRepository = auth, userRepository = users,
            postRepository = mock(), cloudFunctions = mock(), firestoreDataSource = mock(),
            nowPlayingManager = mock(), musicServicePreference = mock(), preferencesDataStore = mock(),
            remoteConfigService = mock(), musicSearchRepository = mock(), tmdbRepository = mock(),
            exploreRepository = mock(), feedSwitchHintManager = mock(), analyticsService = analytics)
        vm.toggleFollow("target")
        runCurrent()
        vm.logFollowFriendsOnboardingCompleted()
        runCurrent()
        verify(analytics, never()).logFollowFriendsOnboardingCompleted(any())
        gate.complete(Unit)
        runCurrent()
        verify(analytics).logFollowFriendsOnboardingCompleted(0)
    }
}
