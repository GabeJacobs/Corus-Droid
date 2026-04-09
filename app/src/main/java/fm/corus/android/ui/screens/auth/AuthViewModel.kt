package fm.corus.android.ui.screens.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
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

    fun observeAuthState() {
        firebaseAuth.addAuthStateListener { auth ->
            viewModelScope.launch {
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
                        // Prefetch in parallel
                        launch { userRepository.prefetchFollowingSet(user.uid) }
                        launch { userRepository.prefetchBlockedSet(user.uid) }
                        launch { userRepository.prefetchMutedSet(user.uid) }
                        launch { authRepository.registerFCMToken() }
                        launch { remoteConfigService.fetchAndActivate() }
                        analyticsService.setUserId(user.uid)
                        _authState.value = AuthState.SignedIn
                    }
                } catch (e: Exception) {
                    authRepository.signOut()
                    _authState.value = AuthState.SignedOut
                }
            }
        }
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

    // ── Sign Out ──

    fun signOut() {
        viewModelScope.launch {
            analyticsService.logSignOut()
            authRepository.signOut()
            userRepository.clearCaches()
            didSignInThisSession = false
        }
    }

    fun setError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }
}
