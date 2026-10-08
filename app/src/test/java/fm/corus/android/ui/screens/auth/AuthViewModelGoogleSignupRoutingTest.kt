package fm.corus.android.ui.screens.auth

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.service.AppCheckTokenSource
import fm.corus.android.service.NetworkMonitor
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AuthViewModelGoogleSignupRoutingTest {
    private val dispatcher = StandardTestDispatcher()
    private val firebase = mock<FirebaseAuth>()
    private val user = mock<FirebaseUser> { on { uid } doReturn "google-user" }
    private val auth = mock<AuthRepository>()
    private val subscriptions = mock<SubscriptionRepository>()
    private val remote = mock<RemoteConfigService>()
    private lateinit var listener: FirebaseAuth.AuthStateListener

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        runTest(dispatcher) { whenever(auth.checkIfUserIsBanned("google-user")).thenReturn(false) }
        doAnswer { listener = it.getArgument(0); null }
            .whenever(firebase).addAuthStateListener(any())
    }
    @After fun teardown() { Dispatchers.resetMain() }

    private suspend fun viewModel(isNewUser: Boolean, notifyBeforeResult: Boolean = true): AuthViewModel {
        val appCheck = mock<AppCheckTokenSource> {
            onBlocking { withToken<Boolean>(any()) } doSuspendableAnswer {
                it.getArgument<suspend () -> Boolean>(0).invoke()
            }
        }
        val network = mock<NetworkMonitor> {
            on { isConnected } doReturn MutableStateFlow(true)
        }
        val vm = AuthViewModel(
            authRepository = auth, userRepository = mock(), subscriptionRepository = subscriptions,
            exploreRepository = mock(), engagementManager = mock(), remoteConfigService = remote,
            forYouPrototype = mock(), analyticsService = mock(), firebaseAuth = firebase,
            unreadCountsRepository = mock(), musicServicePreference = mock(), nowPlayingManager = mock(),
            networkMonitor = network, onboardingLocalStore = mock(), preferencesDataStore = mock(),
            feedSwitchHintManager = mock(), appCheckTokenSource = appCheck,
            context = RuntimeEnvironment.getApplication(),
        )
        vm.observeAuthState()
        whenever(auth.signInWithGoogleCredential("test-token")).doSuspendableAnswer {
            whenever(firebase.currentUser).thenReturn(user)
            if (notifyBeforeResult) listener.onAuthStateChanged(firebase)
            isNewUser
        }
        return vm
    }

    @Test fun `new Google user opens setup while ban config and subscription requests are pending`() = runTest(dispatcher) {
        val ban = CompletableDeferred<Boolean>()
        val subscription = CompletableDeferred<Unit>()
        val config = CompletableDeferred<Unit>()
        whenever(auth.checkIfUserIsBanned("google-user")).doSuspendableAnswer { ban.await() }
        whenever(subscriptions.loginUser("google-user")).doSuspendableAnswer { subscription.await() }
        whenever(remote.fetchAndActivate(any())).doSuspendableAnswer { config.await() }
        val vm = viewModel(isNewUser = true)
        vm.signInWithGoogle("test-token")
        runCurrent()

        assertEquals(AuthViewModel.AuthState.NeedsOnboarding, vm.authState.value)
        assertFalse(vm.isLoading.value)
        verify(auth, never()).checkNeedsOnboarding()
        verify(subscriptions).loginUser("google-user")

        ban.complete(false)
        subscription.complete(Unit)
        config.complete(Unit)
        runCurrent()
        assertEquals(AuthViewModel.AuthState.NeedsOnboarding, vm.authState.value)
    }

    @Test fun `late Firebase callback cannot send a confirmed new user back to the logo`() = runTest(dispatcher) {
        val vm = viewModel(isNewUser = true, notifyBeforeResult = false)
        vm.signInWithGoogle("test-token")
        runCurrent()
        listener.onAuthStateChanged(firebase)
        runCurrent()

        assertEquals(AuthViewModel.AuthState.NeedsOnboarding, vm.authState.value)
        verify(auth, never()).checkNeedsOnboarding()
    }

    @Test fun `existing Google account keeps the sign in button loading during the profile check`() = runTest(dispatcher) {
        val profile = CompletableDeferred<Boolean>()
        val subscription = CompletableDeferred<Unit>()
        whenever(auth.checkNeedsOnboarding()).doSuspendableAnswer { profile.await() }
        whenever(subscriptions.loginUser("google-user")).doSuspendableAnswer { subscription.await() }
        val vm = viewModel(isNewUser = false)
        vm.signInWithGoogle("test-token")
        runCurrent()

        assertEquals(AuthViewModel.AuthState.SignedOut, vm.authState.value)
        assertTrue("Sign-in stopped early: ${vm.error.value}", vm.isLoading.value)
        assertEquals("google", vm.busyProvider.value)
        profile.complete(true)
        runCurrent()
        assertEquals(AuthViewModel.AuthState.NeedsOnboarding, vm.authState.value)
        assertFalse(vm.isLoading.value)
        subscription.complete(Unit)
        runCurrent()
    }

    @Test fun `transient background failure leaves a new user in setup`() = runTest(dispatcher) {
        whenever(auth.checkIfUserIsBanned("google-user")).thenThrow(IllegalStateException("offline"))
        val vm = viewModel(isNewUser = true)
        vm.signInWithGoogle("test-token")
        runCurrent()

        assertEquals(AuthViewModel.AuthState.NeedsOnboarding, vm.authState.value)
        verify(auth, never()).signOut()
    }

    @Test fun `a delayed ban result for a previous account cannot sign out the current account`() = runTest(dispatcher) {
        val ban = CompletableDeferred<Boolean>()
        whenever(auth.checkIfUserIsBanned("google-user")).doSuspendableAnswer { ban.await() }
        val vm = viewModel(isNewUser = true)
        vm.signInWithGoogle("test-token")
        runCurrent()
        val otherUser = mock<FirebaseUser> { on { uid } doReturn "other-user" }
        whenever(firebase.currentUser).thenReturn(otherUser)
        ban.complete(true)
        runCurrent()

        verify(auth, never()).signOut()
        assertEquals(AuthViewModel.AuthState.NeedsOnboarding, vm.authState.value)
    }

    @Test fun `background ban check still rejects a banned new account`() = runTest(dispatcher) {
        whenever(auth.checkIfUserIsBanned("google-user")).thenReturn(true)
        val vm = viewModel(isNewUser = true)
        vm.signInWithGoogle("test-token")
        runCurrent()

        verify(auth).signOut()
        assertEquals(AuthViewModel.AuthState.SignedOut, vm.authState.value)
    }
}
