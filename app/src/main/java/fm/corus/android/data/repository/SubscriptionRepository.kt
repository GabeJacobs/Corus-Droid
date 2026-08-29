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
import fm.corus.android.TestEnvironment
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.domain.PlaylistGatingUX
import fm.corus.android.domain.PlaylistTrialField
import fm.corus.android.domain.PlaylistTrialUsed
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
import java.util.Date
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
        /** Absolute ceiling on posts in a rolling 6h window — applies to everyone,
         *  including subscribers. Kept in sync with backend `DAILY_POST_LIMIT_HARD`. */
        const val DAILY_POST_LIMIT_HARD = 400
        /** Threshold at which the "approaching cap" warning popup fires. Kept in
         *  sync with backend `DAILY_POST_LIMIT_WARN_AT`. */
        const val DAILY_POST_LIMIT_WARN_AT = 390
        const val ROLLING_WINDOW_MS = 24L * 60L * 60L * 1000L
        /** Length of the hard-cap window. Kept in sync with backend `HARD_CAP_WINDOW_MS`. */
        const val HARD_CAP_WINDOW_MS = 6L * 60L * 60L * 1000L
        /**
         * Minimum gap between server-driven post-limit refreshes from the
         * foreground hook. Throttles `refreshPostLimitIfNeeded` so rapid
         * background/foreground flips don't spam `checkCanPost`. Long enough
         * that a single tab-switch doesn't matter, short enough that the
         * rolling 24h window catches up after a real wait.
         */
        const val POST_LIMIT_REFRESH_THROTTLE_MS = 5L * 60L * 1000L
        private const val PREF_IS_CLUB_MEMBER = "cached_isClubMember"
        private const val PREF_IS_VERIFIED = "cached_isVerified"
        private const val PREF_FAVORITES_TAB_UNLOCKED = "cached_favoritesTabUnlocked"
        private const val PREF_FAVORITES_COUNT = "cached_favoritesCount"
        private const val PREF_DAILY_POST_LIMIT = "cached_dailyPostLimit"
        private const val PREF_LAST_APPROACHING_CAP_WARNING_AT = "lastApproachingCapWarningAt"

        // 5h throttle (< the 6h hard-cap window) so the warning re-fires the
        // next time the user crosses the threshold in a fresh window.
        private const val APPROACHING_CAP_WARNING_THROTTLE_MS = 5L * 60L * 60L * 1000L
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

    // Posts created in the rolling 6h hard-cap window. Only meaningful for
    // subscribers — free-tier users can't reach the hard cap.
    private val _recentPostCountHard = MutableStateFlow(0)
    val recentPostCountHard: StateFlow<Int> = _recentPostCountHard.asStateFlow()

    private val _totalPostCount = MutableStateFlow(0)
    val totalPostCount: StateFlow<Int> = _totalPostCount.asStateFlow()

    // True once we've successfully fetched recentPostCount from the server.
    // While false, callers should treat canPost as unverified and ask the server before posting.
    private val _postCountLoaded = MutableStateFlow(false)
    val postCountLoaded: StateFlow<Boolean> = _postCountLoaded.asStateFlow()

    // Wall-clock time of the last successful checkCanPost round trip.
    // In-memory only — fine because we always refresh on the next compose
    // entry / app restart anyway.
    private var lastPostLimitRefreshAt: Long = 0L

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
            if (_recentPostCountHard.value >= DAILY_POST_LIMIT_HARD) return false
            return hasFullAccess || _recentPostCount.value < _dailyPostLimit.value
        }

    val isHardCapped: Boolean
        get() = _recentPostCountHard.value >= DAILY_POST_LIMIT_HARD

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

    /**
     * Uid whose favorites cache we may write. Set by [restoreFavoritesForUser],
     * cleared on logout. [persistFavorites] must never stamp this session's
     * latch onto a different account (iOS uid-guards the persisted snapshot).
     */
    private var favoritesOwnerUid: String? = null

    /** Mirror of users_v2/{uid}.favoritesCount. Hydrated at profile load and updated after each favorite/unfavorite. */
    private val _favoritesCount = MutableStateFlow(0)
    val favoritesCount: StateFlow<Int> = _favoritesCount.asStateFlow()

    private val _favoritesTabUnlocked = MutableStateFlow(false)
    val favoritesTabUnlocked: StateFlow<Boolean> = _favoritesTabUnlocked.asStateFlow()

    init {
        currentUid()?.let { restoreFavoritesForUser(it) }
    }

    /**
     * @param allowZero false for cache / missing-field hydration so a flicker
     * to 0 cannot hide the Favorites tab once it has been unlocked.
     */
    fun setFavoritesCount(count: Int, allowZero: Boolean = true) {
        val applied = fm.corus.android.domain.FavoritesTabGate.apply(
            incoming = count,
            current = _favoritesCount.value,
            unlocked = _favoritesTabUnlocked.value,
            allowZero = allowZero,
        )
        if (applied.second != _favoritesTabUnlocked.value) {
            _favoritesTabUnlocked.value = applied.second
        }
        if (_favoritesCount.value != applied.first) {
            _favoritesCount.value = applied.first
        }
        persistFavorites(applied.first, applied.second)
    }

    /**
     * Apply this uid's last known favorites count before the feed draws.
     * Unlock follows the count — a leftover `unlocked` flag from another
     * account (or a previous visit) must not show Favorites on a 0-count user.
     * In-session latch still lives in [favoritesTabUnlocked] after a real
     * favorite / unfavorite. iOS uid-matches its persisted profile snapshot.
     */
    fun restoreFavoritesForUser(uid: String) {
        favoritesOwnerUid = uid
        val count = prefs.getInt(favoritesCountKey(uid), 0).coerceAtLeast(0)
        val unlocked = count > 0
        _favoritesCount.value = count
        _favoritesTabUnlocked.value = unlocked
        persistFavorites(count, unlocked)
    }

    private fun persistFavorites(count: Int, unlocked: Boolean) {
        val editor = prefs.edit()
            .putBoolean(PREF_FAVORITES_TAB_UNLOCKED, unlocked)
            .putInt(PREF_FAVORITES_COUNT, count)
        favoritesOwnerUid?.let { uid ->
            editor.putBoolean(favoritesUnlockedKey(uid), unlocked)
            editor.putInt(favoritesCountKey(uid), count)
        }
        editor.apply()
    }

    private fun currentUid(): String? =
        runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }
            .getOrNull()

    private fun favoritesCountKey(uid: String) = "${PREF_FAVORITES_COUNT}_$uid"

    private fun favoritesUnlockedKey(uid: String) = "${PREF_FAVORITES_TAB_UNLOCKED}_$uid"

    /** Local favorite-people cap pre-check. Mirrors backend `shouldRejectFavorite`. */
    fun shouldRejectFavorite(): Boolean {
        if (!remoteConfig.favoritePeopleCapEnforced) return false
        if (hasFullAccess) return false
        return _favoritesCount.value >= remoteConfig.favoritePeopleCapLimit
    }

    /** Mirror of users_v2/{uid}.playlistTrialUsed — hydrated from profile reads. */
    private val _playlistTrialUsed = MutableStateFlow(PlaylistTrialUsed())
    val playlistTrialUsed: StateFlow<PlaylistTrialUsed> = _playlistTrialUsed.asStateFlow()

    fun setPlaylistTrialUsed(used: PlaylistTrialUsed) {
        _playlistTrialUsed.value = used
    }

    fun markPlaylistTrialUsed(field: PlaylistTrialField) {
        _playlistTrialUsed.value = _playlistTrialUsed.value.markUsed(field)
    }

    fun shouldPaywallPlaylist(field: PlaylistTrialField): Boolean =
        PlaylistGatingUX.shouldPaywallPlaylist(_playlistTrialUsed.value, field, hasFullAccess)

    fun updateVerifiedStatus(isVerified: Boolean) {
        _isVerified.value = isVerified
        prefs.edit().putBoolean(PREF_IS_VERIFIED, isVerified).apply()
    }

    fun incrementPostCount() {
        _recentPostCount.value++
        _recentPostCountHard.value++
        _totalPostCount.value++
    }

    /**
     * Whether to show the "approaching cap" warning popup right now. Returns
     * true at most once per [APPROACHING_CAP_WARNING_THROTTLE_MS] — marks the
     * SharedPreferences timestamp atomically so a caller can simply act on the
     * boolean without juggling its own state. Only meaningful for users who
     * can actually reach the hard cap (subscribers / full-access). The caller
     * is responsible for the hasFullAccess gate.
     */
    fun shouldShowApproachingCapWarning(now: Long = System.currentTimeMillis()): Boolean {
        val count = _recentPostCountHard.value
        if (count < DAILY_POST_LIMIT_WARN_AT || count >= DAILY_POST_LIMIT_HARD) return false
        val last = prefs.getLong(PREF_LAST_APPROACHING_CAP_WARNING_AT, 0L)
        if (now - last < APPROACHING_CAP_WARNING_THROTTLE_MS) return false
        prefs.edit().putLong(PREF_LAST_APPROACHING_CAP_WARNING_AT, now).apply()
        return true
    }

    /** Posts remaining before the hard cap, clamped to ≥ 0. */
    val approachingCapRemaining: Int
        get() = (DAILY_POST_LIMIT_HARD - _recentPostCountHard.value).coerceAtLeast(0)

    /**
     * Optimistically decrement after a successful delete so the paywall frees
     * up a posting slot immediately. Posts older than the 24h rolling window
     * aren't counted by the server, so deleting them must not decrement
     * [recentPostCount] — only [totalPostCount] always drops. Server is still
     * the source of truth; [refreshPostLimit] reconciles on next round trip.
     */
    fun decrementPostCount(postTimestamp: Date?, now: Long = System.currentTimeMillis()) {
        _totalPostCount.value = (_totalPostCount.value - 1).coerceAtLeast(0)
        if (postTimestamp == null) return
        val age = now - postTimestamp.time
        if (age in 0..ROLLING_WINDOW_MS) {
            _recentPostCount.value = (_recentPostCount.value - 1).coerceAtLeast(0)
        }
        if (age in 0..HARD_CAP_WINDOW_MS) {
            _recentPostCountHard.value = (_recentPostCountHard.value - 1).coerceAtLeast(0)
        }
    }

    /** Ask the server for the rolling 24h post count and cache the result. */
    suspend fun refreshPostLimit() {
        try {
            val result = cloudFunctions.checkCanPost()
            _recentPostCount.value = result.recentCount
            _recentPostCountHard.value = result.recentCountHard
            applyDailyLimit(result.dailyLimit)
            _postCountLoaded.value = true
            lastPostLimitRefreshAt = System.currentTimeMillis()
        } catch (_: Exception) { }
    }

    /**
     * Foreground-friendly variant: skip when the user has full access (the gate
     * doesn't apply to them) and when we've refreshed recently. Keeps the
     * on-resume hook from spamming `checkCanPost` for paid users or rapid
     * app-switch flips.
     */
    suspend fun refreshPostLimitIfNeeded(now: Long = System.currentTimeMillis()) {
        if (hasFullAccess) return
        if (now - lastPostLimitRefreshAt < POST_LIMIT_REFRESH_THROTTLE_MS) return
        refreshPostLimit()
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
            _recentPostCountHard.value = result.recentCountHard
            applyDailyLimit(result.dailyLimit)
            _postCountLoaded.value = true
            lastPostLimitRefreshAt = System.currentTimeMillis()
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
            val customerInfo = awaitLogIn(uid)
            if (customerInfo != null) {
                updateClubStatus(customerInfo)
                syncClubStatusToFirestore()
            }
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionRepo", "loginUser failed", e)
        }
    }

    /** Suspend wrapper around `Purchases.logIn`. Resolves null on error. */
    private suspend fun awaitLogIn(uid: String): CustomerInfo? =
        if (TestEnvironment.isActive) null else suspendCancellableCoroutine { cont ->
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

    /**
     * Guarantee the RevenueCat SDK is aliased to the current Firebase UID before
     * a purchase. On the launch fast path the alias ([loginUser]) is backgrounded,
     * so a user can reach the paywall before it lands; a purchase made while the
     * SDK is still on its anonymous id makes the RevenueCat webhook skip the
     * INITIAL_PURCHASE (anti-fraud), leaving `users_v2/{uid}.isClubMember` unset
     * until the next sign-in's TRANSFER — i.e. the user stays blocked at the
     * post limit until they restart. Awaiting the alias here closes that window.
     * No-op when already aliased.
     */
    suspend fun ensureIdentified() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val current = runCatching { Purchases.sharedInstance.appUserID }.getOrNull()
        if (current == uid) return
        Purchases.sharedInstance.updatedCustomerInfoListener = this
        repeat(2) {
            val info = awaitLogIn(uid)
            if (info != null) {
                updateClubStatus(info)
                syncClubStatusToFirestore()
                return
            }
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
        _playlistTrialUsed.value = PlaylistTrialUsed()
        favoritesOwnerUid = null
        _favoritesTabUnlocked.value = false
        _favoritesCount.value = 0
        prefs.edit()
            .putBoolean(PREF_FAVORITES_TAB_UNLOCKED, false)
            .putInt(PREF_FAVORITES_COUNT, 0)
            .apply()
        lastPostLimitRefreshAt = 0L
        try {
            Purchases.sharedInstance.updatedCustomerInfoListener = null
            Purchases.sharedInstance.logOut()
        } catch (_: Exception) { }
    }

    fun fetchOfferings() {
        Purchases.sharedInstance.getOfferingsWith(
            onError = { },
            onSuccess = { offerings ->
                _packages.value = offerings.current?.availablePackages ?: emptyList()
            },
        )
    }

    fun purchase(
        activity: android.app.Activity,
        pkg: Package,
        paywallSource: String,
        onResult: (PurchaseOutcome) -> Unit,
    ) {
        // Attribute the sale to the paywall that drove it, so the subscription
        // record (not just the GA4 event) carries the source. Fail-quiet — an
        // attribute hiccup must never block the purchase.
        try {
            Purchases.sharedInstance.setAttributes(mapOf("paywall_source" to paywallSource))
        } catch (_: Exception) { }
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
                    scope.launch {
                        // Self-heal: if the purchase still fired against an
                        // anonymous RC id (the pre-purchase alias couldn't
                        // complete), aliasing now triggers a TRANSFER so the
                        // webhook back-fills isClubMember without a restart.
                        ensureIdentified()
                        val userId = Purchases.sharedInstance.appUserID
                        try {
                            cloudFunctions.syncClubMemberStatus(userId, isClubMember = true)
                        } catch (_: Exception) { }
                        // Report success only after the sync attempt (matches
                        // iOS `purchase(package:)`): success handlers fire
                        // server-gated requests right away — e.g. the feed
                        // switching to Taste Matches — and the server reads
                        // membership from Firestore, so the sync must land
                        // first. Sync failure still reports Success: the local
                        // entitlement is real and the webhook backfills.
                        onResult(PurchaseOutcome.Success)
                    }
                } else {
                    onResult(PurchaseOutcome.Success)
                }
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
