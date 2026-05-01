package fm.corus.android.data.repository

import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import android.content.SharedPreferences
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class PurchaseOutcome {
    data object Success : PurchaseOutcome()
    data object Cancelled : PurchaseOutcome()
    data class Failed(val error: String) : PurchaseOutcome()
}

@Singleton
class SubscriptionRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    private val remoteConfig: RemoteConfigService,
    private val analyticsService: AnalyticsService,
    private val prefs: SharedPreferences,
) : UpdatedCustomerInfoListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CLUB_ENTITLEMENT_ID = "corus_club"
        /**
         * Fallback used until the server tells us the current limit. The number
         * of truth lives in Remote Config; this value only matters offline /
         * before the first `checkCanPost` response.
         */
        const val DEFAULT_DAILY_POST_LIMIT = 3
        const val DAILY_POST_LIMIT_HARD = 400
        private const val PREF_IS_CLUB_MEMBER = "cached_isClubMember"
        private const val PREF_IS_VERIFIED = "cached_isVerified"
        private const val PREF_DAILY_POST_LIMIT = "cached_dailyPostLimit"
    }

    private val _isClubMember = MutableStateFlow(prefs.getBoolean(PREF_IS_CLUB_MEMBER, false))
    val isClubMember: StateFlow<Boolean> = _isClubMember.asStateFlow()

    private val _isVerified = MutableStateFlow(prefs.getBoolean(PREF_IS_VERIFIED, false))
    val isVerified: StateFlow<Boolean> = _isVerified.asStateFlow()

    private val _packages = MutableStateFlow<List<Package>>(emptyList())
    val packages: StateFlow<List<Package>> = _packages.asStateFlow()

    // Posts created in the rolling 24h window (mirrors iOS SubscriptionService).
    private val _recentPostCount = MutableStateFlow(0)
    val recentPostCount: StateFlow<Int> = _recentPostCount.asStateFlow()

    private val _totalPostCount = MutableStateFlow(0)
    val totalPostCount: StateFlow<Int> = _totalPostCount.asStateFlow()

    // True once we've successfully fetched recentPostCount from the server.
    // While false, callers should treat canPost as unverified and ask the server before posting.
    private val _postCountLoaded = MutableStateFlow(false)
    val postCountLoaded: StateFlow<Boolean> = _postCountLoaded.asStateFlow()

    // Rolling 24h post limit returned by the server (Remote Config-driven).
    // Cached in SharedPreferences so the gate behaves consistently across
    // launches before the next `checkCanPost` round trip. Defaults to
    // [DEFAULT_DAILY_POST_LIMIT] when nothing has been received yet.
    private val _dailyPostLimit = MutableStateFlow(
        prefs.getInt(PREF_DAILY_POST_LIMIT, DEFAULT_DAILY_POST_LIMIT).coerceAtLeast(1)
    )
    val dailyPostLimit: StateFlow<Int> = _dailyPostLimit.asStateFlow()

    val hasFullAccessFlow: StateFlow<Boolean> = combine(_isClubMember, _isVerified) { club, verified ->
        club || verified
    }.stateIn(scope, SharingStarted.Eagerly, _isClubMember.value || _isVerified.value)

    val hasFullAccess: Boolean
        get() = _isClubMember.value || _isVerified.value

    val canPost: Boolean
        get() {
            if (!remoteConfig.corusClubEnabled) return true
            if (_recentPostCount.value >= DAILY_POST_LIMIT_HARD) return false
            return hasFullAccess || _recentPostCount.value < _dailyPostLimit.value
        }

    val isHardCapped: Boolean
        get() = _recentPostCount.value >= DAILY_POST_LIMIT_HARD

    /** Mirror of users_v2/{uid}.savesCount. Hydrated at profile load and updated after each save/unsave. */
    private val _savesCount = MutableStateFlow(0)
    val savesCount: StateFlow<Int> = _savesCount.asStateFlow()

    fun setSavesCount(count: Int) {
        _savesCount.value = count.coerceAtLeast(0)
    }

    /** Local cap pre-check. Mirrors backend `shouldRejectSave`. */
    fun shouldRejectSave(): Boolean {
        if (!remoteConfig.saveCapEnforced) return false
        if (hasFullAccess) return false
        return _savesCount.value >= remoteConfig.saveCapLimit
    }

    fun updateVerifiedStatus(isVerified: Boolean) {
        _isVerified.value = isVerified
        prefs.edit().putBoolean(PREF_IS_VERIFIED, isVerified).apply()
    }

    fun incrementPostCount() {
        _recentPostCount.value++
        _totalPostCount.value++
    }

    /** Ask the server for the rolling 24h post count and cache the result. */
    suspend fun refreshPostLimit() {
        try {
            val result = cloudFunctions.checkCanPost()
            _recentPostCount.value = result.recentCount
            applyDailyLimit(result.dailyLimit)
            _postCountLoaded.value = true
        } catch (_: Exception) { }
    }

    /**
     * Ask the server right now whether the user can post. Called at submit time when
     * [postCountLoaded] is false. Fails open — the server-side trigger in
     * backend/functions/index.js is the final safety net.
     */
    suspend fun checkCanPostFromServer(): Boolean {
        return try {
            val result = cloudFunctions.checkCanPost()
            _recentPostCount.value = result.recentCount
            applyDailyLimit(result.dailyLimit)
            _postCountLoaded.value = true
            result.canPost
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Persist the server-returned dailyLimit. Skipped when null/non-positive so
     * the cached value is preserved across malformed responses.
     */
    private fun applyDailyLimit(limit: Int?) {
        if (limit == null || limit <= 0) return
        _dailyPostLimit.value = limit
        prefs.edit().putInt(PREF_DAILY_POST_LIMIT, limit).apply()
    }

    fun setTotalPostCount(count: Int) {
        _totalPostCount.value = count
    }

    suspend fun loginUser(uid: String) {
        Purchases.sharedInstance.updatedCustomerInfoListener = this
        try {
            val customerInfo = suspendCancellableCoroutine { cont ->
                Purchases.sharedInstance.logIn(
                    uid,
                    object : com.revenuecat.purchases.interfaces.LogInCallback {
                        override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                            cont.resume(customerInfo)
                        }
                        override fun onError(error: PurchasesError) {
                            cont.resume(null)
                        }
                    }
                )
            }
            if (customerInfo != null) {
                updateClubStatus(customerInfo)
                syncClubStatusToFirestore()
            }
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionRepo", "loginUser failed", e)
        }
    }

    override fun onReceived(customerInfo: CustomerInfo) {
        val wasClubMember = _isClubMember.value
        updateClubStatus(customerInfo)
        syncClubStatusToFirestore()
        if (wasClubMember && !_isClubMember.value) {
            analyticsService.logSubscriptionExpired()
        }
    }

    fun logoutUser() {
        _isClubMember.value = false
        _isVerified.value = false
        _recentPostCount.value = 0
        _totalPostCount.value = 0
        _postCountLoaded.value = false
        Purchases.sharedInstance.updatedCustomerInfoListener = null
        try { Purchases.sharedInstance.logOut() } catch (_: Exception) { }
    }

    fun fetchOfferings() {
        Purchases.sharedInstance.getOfferingsWith(
            onError = { },
            onSuccess = { offerings ->
                _packages.value = offerings.current?.availablePackages ?: emptyList()
            },
        )
    }

    fun purchase(activity: android.app.Activity, pkg: Package, onResult: (PurchaseOutcome) -> Unit) {
        Purchases.sharedInstance.purchaseWith(
            purchaseParams = com.revenuecat.purchases.PurchaseParams.Builder(activity, pkg).build(),
            onError = { error, userCancelled ->
                if (userCancelled) {
                    onResult(PurchaseOutcome.Cancelled)
                } else {
                    onResult(PurchaseOutcome.Failed(error.message))
                }
            },
            onSuccess = { storeTransaction, customerInfo ->
                updateClubStatus(customerInfo)
                if (_isClubMember.value) {
                    val userId = Purchases.sharedInstance.appUserID
                    scope.launch {
                        try {
                            cloudFunctions.syncClubMemberStatus(userId, isClubMember = true)
                        } catch (_: Exception) { }
                    }
                }
                onResult(PurchaseOutcome.Success)
            },
        )
    }

    fun restorePurchases(onResult: (Boolean) -> Unit) {
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { onResult(false) },
            onSuccess = { customerInfo ->
                updateClubStatus(customerInfo)
                if (_isClubMember.value) {
                    val userId = Purchases.sharedInstance.appUserID
                    scope.launch {
                        try {
                            cloudFunctions.syncClubMemberStatus(userId, isClubMember = true)
                        } catch (_: Exception) { }
                    }
                }
                onResult(_isClubMember.value)
            },
        )
    }

    suspend fun checkStatus() {
        try {
            val customerInfo = suspendCancellableCoroutine { cont ->
                Purchases.sharedInstance.getCustomerInfo(
                    callback = object : com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback {
                        override fun onReceived(customerInfo: CustomerInfo) {
                            cont.resume(customerInfo)
                        }
                        override fun onError(error: PurchasesError) {
                            cont.resume(null)
                        }
                    }
                )
            }
            if (customerInfo != null) {
                updateClubStatus(customerInfo)
            }
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionRepo", "checkStatus failed", e)
        }
    }

    private fun updateClubStatus(customerInfo: CustomerInfo) {
        val isActive = customerInfo.entitlements[CLUB_ENTITLEMENT_ID]?.isActive == true
        _isClubMember.value = isActive
        prefs.edit().putBoolean(PREF_IS_CLUB_MEMBER, isActive).apply()
    }

    private fun syncClubStatusToFirestore() {
        val userId = Purchases.sharedInstance.appUserID
        scope.launch {
            try {
                cloudFunctions.syncClubMemberStatus(userId, isClubMember = _isClubMember.value)
            } catch (_: Exception) { }
        }
    }
}
