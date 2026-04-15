package fm.corus.android.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
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
    private val auth: FirebaseAuth,
    private val firestoreDataSource: FirestoreDataSource,
    private val storageDataSource: FirebaseStorageDataSource,
    private val messaging: FirebaseMessaging,
    private val cloudFunctions: CloudFunctionsDataSource,
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

    // ── Google Sign-In ──

    suspend fun signInWithGoogleCredential(idToken: String): Boolean {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
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
        }
        return profile == null
    }

    suspend fun refreshUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        val profile = firestoreDataSource.fetchUserProfile(uid)
        _userProfile.value = profile
        if (profile != null) {
            _isClubMember.value = profile.isClubMember
        }
    }

    // ── Onboarding ──

    suspend fun completeOnboarding(username: String, displayName: String, avatarData: ByteArray?) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("No signed-in user")
        val phone = auth.currentUser?.phoneNumber ?: ""

        firestoreDataSource.createUserProfile(
            uid = uid,
            username = username,
            displayName = displayName,
            email = phone,
        )

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
        val uid = auth.currentUser?.uid ?: run {
            Log.w("FCM", "registerFCMToken: no current user")
            return
        }
        try {
            val token = messaging.token.await()
            Log.d("FCM", "registerFCMToken: got token ${token.take(20)}... for uid=$uid")
            firestoreDataSource.updateFCMToken(uid, token)
            Log.d("FCM", "registerFCMToken: token saved to Firestore")
        } catch (e: Exception) {
            Log.e("FCM", "registerFCMToken failed", e)
        }
    }

    // ── Sign Out ──

    suspend fun signOut() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            try { firestoreDataSource.removeFCMToken(uid) } catch (_: Exception) { }
        }
        auth.signOut()
        _currentUser.value = null
        _userProfile.value = null
        _isClubMember.value = false
    }

    // ── Delete Account ──

    suspend fun deleteAccount() {
        try { cloudFunctions.deleteAllUserData() } catch (_: Exception) { }
        auth.currentUser?.delete()?.await()
        _currentUser.value = null
        _userProfile.value = null
        _isClubMember.value = false
    }
}
