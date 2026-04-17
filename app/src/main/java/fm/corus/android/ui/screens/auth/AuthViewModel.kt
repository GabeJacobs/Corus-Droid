package fm.corus.android.ui.screens.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val exploreRepository: ExploreRepository,
    private val engagementManager: PostEngagementManager,
    private val remoteConfigService: RemoteConfigService,
    private val analyticsService: AnalyticsService,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    sealed class AuthState {
        data object Loading : AuthState()
        data object SignedOut : AuthState()
        data object NeedsOnboarding : AuthState()
        data object NeedsSocialSetup : AuthState()
        data object SignedIn : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _verificationSent = MutableStateFlow(false)
    val verificationSent: StateFlow<Boolean> = _verificationSent.asStateFlow()

    // Whether this session created a new sign-in (vs app relaunch)
    private var didSignInThisSession = false

    /** Display name from the OAuth provider (Google), if any. Used to conditionally show name field. */
    val oauthDisplayName: String?
        get() = firebaseAuth.currentUser?.displayName?.takeIf { it.isNotBlank() }

    private var authStateListener: AuthStateListener? = null

    fun observeAuthState() {
        val listener = AuthStateListener { auth ->
            viewModelScope.launch {
                // Suppress auth-state changes while account deletion is in progress
                if (_isDeletingAccount.value) return@launch

                val user = auth.currentUser
                if (user == null) {
                    _authState.value = AuthState.SignedOut
                    return@launch
                }

                _authState.value = AuthState.Loading
                try {
                    // Check if user is banned before proceeding
                    val isBanned = authRepository.checkIfUserIsBanned(user.uid)
                    if (isBanned) {
                        authRepository.signOut()
                        _error.value = "Your account has been suspended. Please contact support if you believe this is a mistake."
                        _authState.value = AuthState.SignedOut
                        return@launch
                    }

                    val needsOnboarding = authRepository.checkNeedsOnboarding()
                    if (needsOnboarding && !didSignInThisSession) {
                        // Stale auth with no profile — sign out silently
                        authRepository.signOut()
                        _authState.value = AuthState.SignedOut
                        return@launch
                    }

                    if (needsOnboarding) {
                        _authState.value = AuthState.NeedsOnboarding
                    } else {
                        // Set verified status from the profile already fetched
                        // by checkNeedsOnboarding() — must happen before authState
                        // becomes SignedIn so canPost is correct immediately.
                        val profile = authRepository.userProfile.value
                        if (profile != null) {
                            subscriptionRepository.updateVerifiedStatus(profile.isVerified)
                            subscriptionRepository.setTotalPostCount(profile.cymbalCount)
                        }

                        // Prefetch in parallel
                        launch { userRepository.prefetchFollowingSet(user.uid) }
                        launch { userRepository.prefetchBlockedSet(user.uid) }
                        launch { userRepository.prefetchMutedSet(user.uid) }
                        launch { userRepository.prefetchSuggestedMatches(user.uid) }
                        launch { authRepository.registerFCMToken() }
                        launch { remoteConfigService.fetchAndActivate() }
                        launch { subscriptionRepository.refreshTodayPostCount(user.uid) }
                        subscriptionRepository.loginUser(user.uid)
                        analyticsService.setUserId(user.uid)
                        _authState.value = AuthState.SignedIn
                    }
                } catch (e: Exception) {
                    authRepository.signOut()
                    _authState.value = AuthState.SignedOut
                }
            }
        }
        authStateListener = listener
        firebaseAuth.addAuthStateListener(listener)
    }

    override fun onCleared() {
        super.onCleared()
        authStateListener?.let { firebaseAuth.removeAuthStateListener(it) }
    }

    // ── Phone Auth ──

    fun sendVerificationCode(phoneNumber: String, countryCode: String = "+1", activity: Activity) {
        _error.value = null
        _isLoading.value = true
        didSignInThisSession = true

        authRepository.sendVerificationCode(
            phoneNumber = phoneNumber,
            countryCode = countryCode,
            activity = activity,
            callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-verification on some devices
                    viewModelScope.launch {
                        try {
                            firebaseAuth.signInWithCredential(credential)
                        } catch (e: Exception) {
                            _error.value = "Verification failed. Please try again."
                        }
                        _isLoading.value = false
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    _error.value = "Could not send verification code. Please try again."
                    _isLoading.value = false
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    authRepository.setVerificationId(verificationId)
                    _verificationSent.value = true
                    _isLoading.value = false
                }
            }
        )
    }

    fun verifyCode(code: String) {
        viewModelScope.launch {
            _error.value = null
            _isLoading.value = true
            try {
                val isNewUser = authRepository.verifyCode(code)
                if (isNewUser) {
                    analyticsService.logSignUp("phone")
                } else {
                    analyticsService.logSignIn("phone")
                }
            } catch (e: Exception) {
                _error.value = "Invalid verification code. Please try again."
            }
            _isLoading.value = false
        }
    }

    // ── Google Sign-In ──

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _error.value = null
            _isLoading.value = true
            didSignInThisSession = true
            try {
                val isNewUser = authRepository.signInWithGoogleCredential(idToken)
                if (isNewUser) {
                    analyticsService.logSignUp("google")
                } else {
                    analyticsService.logSignIn("google")
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Google sign-in failed", e)
                _error.value = "Google sign-in failed: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    // ── Onboarding ──

    suspend fun checkUsernameAvailable(username: String): Boolean {
        return userRepository.checkUsernameAvailable(username)
    }

    fun completeOnboarding(username: String, displayName: String, avatarData: ByteArray?) {
        viewModelScope.launch {
            _error.value = null
            _isLoading.value = true
            try {
                authRepository.completeOnboarding(username, displayName, avatarData)
                analyticsService.logOnboardingCompleted()
                _authState.value = AuthState.NeedsSocialSetup
            } catch (e: Exception) {
                _error.value = "Something went wrong. Please try again."
            }
            _isLoading.value = false
        }
    }

    fun finishSocialSetup() {
        _authState.value = AuthState.SignedIn
    }

    fun resetVerification() {
        _verificationSent.value = false
        _error.value = null
    }

    // ── Sign Out / Delete ──

    fun signOut() {
        viewModelScope.launch {
            analyticsService.logSignOut()
            subscriptionRepository.logoutUser()
            authRepository.signOut()
            userRepository.clearCaches()
            exploreRepository.clearCaches()
            engagementManager.clearAll()
            didSignInThisSession = false
        }
    }

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    private val _accountDeleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val accountDeleted: SharedFlow<Unit> = _accountDeleted.asSharedFlow()

    /** Emits the provider ID (e.g. "google.com", "phone") when re-auth is needed before deletion. */
    private val _needsReauth = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val needsReauth: SharedFlow<String> = _needsReauth.asSharedFlow()

    fun deleteAccount() {
        viewModelScope.launch {
            _isDeletingAccount.value = true
            try {
                analyticsService.logDeleteAccount()
                subscriptionRepository.logoutUser()
                authRepository.deleteAccount()
                completeAccountDeletion()
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                Log.w("AuthViewModel", "deleteAccount needs re-auth", e)
                _isDeletingAccount.value = false
                val providerId = firebaseAuth.currentUser?.providerData
                    ?.firstOrNull { it.providerId != "firebase" }?.providerId
                if (providerId != null) {
                    _needsReauth.tryEmit(providerId)
                } else {
                    _error.value = "Please sign out and sign back in, then try again."
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "deleteAccount failed", e)
                _isDeletingAccount.value = false
                _error.value = "Couldn't delete your account. Please try again."
            }
        }
    }

    fun reauthenticateAndDelete(googleIdToken: String) {
        viewModelScope.launch {
            _isDeletingAccount.value = true
            try {
                authRepository.reauthenticateWithGoogle(googleIdToken)
                authRepository.deleteAccount()
                completeAccountDeletion()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "reauthenticateAndDelete failed", e)
                _isDeletingAccount.value = false
                _error.value = "Couldn't delete your account. Please try again."
            }
        }
    }

    private fun completeAccountDeletion() {
        userRepository.clearCaches()
        exploreRepository.clearCaches()
        engagementManager.clearAll()
        didSignInThisSession = false
        _isDeletingAccount.value = false
        _authState.value = AuthState.SignedOut
        _accountDeleted.tryEmit(Unit)
    }

    fun setError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }
}
