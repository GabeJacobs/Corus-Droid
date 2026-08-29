package fm.corus.android.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.local.OnboardingLocalStore
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestoreDataSource: FirestoreDataSource,
    private val storageDataSource: FirebaseStorageDataSource,
    private val messaging: FirebaseMessaging,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val onboardingLocalStore: OnboardingLocalStore,
    private val subscriptionRepository: SubscriptionRepository,
) {
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _userProfile = MutableStateFlow<CymbalUser?>(null)
    val userProfile: StateFlow<CymbalUser?> = _userProfile.asStateFlow()

    private val _isClubMember = MutableStateFlow(false)
    val isClubMember: StateFlow<Boolean> = _isClubMember.asStateFlow()

    val currentUserId: String? get() = auth.currentUser?.uid
    val isAuthenticated: Boolean get() = auth.currentUser != null

    // Phone auth state
    var verificationId: String? = null
        private set
    var lastVerificationSentAt: Long? = null
        private set

    val verificationResendSecondsRemaining: Int
        get() {
            val sentAt = lastVerificationSentAt ?: return 0
            val elapsed = (System.currentTimeMillis() - sentAt) / 1000
            return maxOf(0, (60 - elapsed).toInt())
        }

    // ── Phone Auth ──

    fun sendVerificationCode(
        phoneNumber: String,
        countryCode: String = "+1",
        activity: android.app.Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks,
    ) {
        val fullNumber = "$countryCode$phoneNumber"
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(fullNumber)
            .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        lastVerificationSentAt = System.currentTimeMillis()
    }

    suspend fun verifyCode(code: String): Boolean {
        val vid = verificationId ?: throw IllegalStateException("No verification in progress")
        val credential = PhoneAuthProvider.getCredential(vid, code)
        val result = auth.signInWithCredential(credential).await()
        _currentUser.value = result.user
        verificationId = null
        return result.additionalUserInfo?.isNewUser ?: false
    }

    fun setVerificationId(id: String) {
        verificationId = id
    }

    // ── Email OTP Auth ──

    var pendingEmailOtpAddress: String? = null
        private set

    suspend fun sendEmailOtpCode(email: String) {
        val trimmed = email.trim()
        cloudFunctions.sendEmailOtpCode(trimmed)
        pendingEmailOtpAddress = trimmed
        lastVerificationSentAt = System.currentTimeMillis()
    }

    /** Returns whether Firebase reported a brand-new Auth user. */
    suspend fun verifyEmailOtpCode(code: String): Boolean {
        val email = pendingEmailOtpAddress
            ?: throw IllegalStateException("No email verification in progress")
        val digits = code.filter { it.isDigit() }
        require(digits.length == 6) { "Invalid code" }
        val result = cloudFunctions.verifyEmailOtpCode(email, digits)
        val authResult = auth.signInWithCustomToken(result.token).await()
        _currentUser.value = authResult.user
        pendingEmailOtpAddress = null
        return result.isNewUser
    }

    fun clearPendingEmailOtp() {
        pendingEmailOtpAddress = null
    }

    // ── Google Sign-In ──

    suspend fun signInWithGoogleCredential(idToken: String): Boolean {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        _currentUser.value = result.user
        return result.additionalUserInfo?.isNewUser ?: false
    }

    // ── Apple Sign-In ──

    suspend fun signInWithApple(activity: Activity): Boolean {
        val provider = OAuthProvider.newBuilder("apple.com").apply {
            scopes = listOf("email", "name")
        }.build()
        // If a previous attempt was interrupted (e.g. activity recreated),
        // resume it instead of starting a new flow.
        val pending = auth.pendingAuthResult
        val result = (pending ?: auth.startActivityForSignInWithProvider(activity, provider)).await()
        _currentUser.value = result.user
        return result.additionalUserInfo?.isNewUser ?: false
    }

    // ── Ban Check ──

    suspend fun checkIfUserIsBanned(uid: String): Boolean {
        return firestoreDataSource.checkIfUserIsBanned(uid)
    }

    // ── Profile Check ──

    suspend fun checkNeedsOnboarding(): Boolean {
        val uid = auth.currentUser?.uid ?: return true
        val profile = firestoreDataSource.fetchUserProfile(uid)
        _userProfile.value = profile
        if (profile != null) {
            _isClubMember.value = profile.isClubMember
            hydrateFavoritesFromProfile(profile)
        }
        return profile == null
    }

    suspend fun refreshUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        val profile = firestoreDataSource.fetchUserProfile(uid)
        _userProfile.value = profile
        if (profile != null) {
            _isClubMember.value = profile.isClubMember
            hydrateFavoritesFromProfile(profile)
        }
    }

    /** iOS `setFavoritesCount` on `users_v2` hydration — drives the Favorites tab. */
    private fun hydrateFavoritesFromProfile(profile: CymbalUser) {
        subscriptionRepository.setSavesCount(profile.savesCount)
        subscriptionRepository.setFavoritesCount(
            profile.favoritesCount,
            allowZero = profile.favoritesCountSpecified,
        )
        subscriptionRepository.setPlaylistTrialUsed(profile.playlistTrialUsed)
    }

    fun bumpCymbalCount(delta: Int) {
        val current = _userProfile.value ?: return
        val next = (current.cymbalCount + delta).coerceAtLeast(0)
        _userProfile.value = current.copy(cymbalCount = next)
    }

    // ── Onboarding ──

    suspend fun completeOnboarding(username: String, displayName: String, avatarData: ByteArray?) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("No signed-in user")
        val phone = auth.currentUser?.phoneNumber ?: ""

        // `email` stays empty for phone-auth signups — the dedicated
        // `phoneNumber` field now carries the phone (see contact-sync plan).
        firestoreDataSource.createUserProfile(
            uid = uid,
            username = username,
            displayName = displayName,
            email = "",
            phoneNumber = phone,
        )

        // A profile now exists for this uid — record onboarding completion locally
        // so subsequent cold launches fast-path straight to the feed.
        onboardingLocalStore.markCompletedOnboarding(uid)

        if (avatarData != null) {
            val url = storageDataSource.uploadAvatar(uid, avatarData)
            firestoreDataSource.updateUserProfile(uid, mapOf("avatarURL" to url))
        } else {
            firestoreDataSource.updateUserProfile(uid, mapOf("avatarSkipped" to true))
        }

        refreshUserProfile()
    }

    // ── FCM ──

    suspend fun registerFCMToken() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val token = messaging.token.await()
            firestoreDataSource.updateFCMToken(uid, token)
        } catch (_: Exception) { }
    }

    // ── Sign Out ──

    suspend fun signOut() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            try { firestoreDataSource.removeFCMToken(uid) } catch (_: Exception) { }
        }
        auth.signOut()
        // Clear Google's cached account selection so the account picker shows on next sign-in
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        try { GoogleSignIn.getClient(context, gso).signOut().await() } catch (_: Exception) { }
        _currentUser.value = null
        _userProfile.value = null
        _isClubMember.value = false
    }

    // ── Re-authentication ──

    suspend fun reauthenticateWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.currentUser?.reauthenticate(credential)?.await()
    }

    suspend fun reauthenticateWithPhone(verificationId: String, code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        auth.currentUser?.reauthenticate(credential)?.await()
    }

    /**
     * Sends an SMS verification code to the currently signed-in user's phone number
     * for re-authentication (e.g. before account deletion). Uses [auth.currentUser.phoneNumber],
     * which is stored in E.164 format.
     */
    fun sendReauthCodeToCurrentUser(
        activity: android.app.Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks,
    ) {
        val number = auth.currentUser?.phoneNumber
            ?: throw IllegalStateException("No phone number on current user")
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(number)
            .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // ── Delete Account ──

    suspend fun deleteAccount() {
        try {
            cloudFunctions.deleteAllUserData()
        } catch (e: Exception) {
            Log.e("AuthRepository", "deleteAllUserData failed", e)
        }
        auth.currentUser?.delete()?.await()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        try {
            GoogleSignIn.getClient(context, gso).revokeAccess().await()
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google revokeAccess failed", e)
        }
        _currentUser.value = null
        _userProfile.value = null
        _isClubMember.value = false
    }
}
