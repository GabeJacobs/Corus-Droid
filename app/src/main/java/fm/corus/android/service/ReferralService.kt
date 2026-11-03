package fm.corus.android.service

import android.content.Context
import android.net.Uri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

data class ReferralStatus(
    val enabled: Boolean = false,
    val code: String = "",
    val url: String = "",
    val count: Int = 0,
    val eligible: Boolean = false,
    val earned: Boolean = false,
    val rewardStatus: String = "locked",
    val canAcceptInvite: Boolean = false,
    val shortCode: String = "",
    val rewardEndAtMs: Long? = null,
)

data class ReferralFriend(val uid: String, val displayName: String)

data class ReferralWelcome(
    val inviterId: String,
    val name: String,
    val giftStatus: String,
    val giftEndAtMs: Long? = null,
) {
    val isActive
        get() = giftStatus == "applied"

    val waiting
        get() = giftStatus == "waiting_for_subscription"

    val pending
        get() = !isActive && !waiting && giftStatus != "expired"
}

data class ReferralReceipt(
    val redeemed: Boolean,
    val giftStatus: String? = null,
    val giftEndAtMs: Long? = null,
    val inviterLabel: String? = null,
    val appliedCode: String? = null,
)

data class ReferralInvitation(val code: String, val name: String)

enum class ReferralCompletionIssue {
    AlreadyRedeemed,
    TemporaryFailure,
    InvalidInvitation,
}

enum class ReferralClaimOutcome {
    Applied,
    Saved,
    Pending,
    Failed,
}

object ReferralService {
    private val gate = ReferralClientGate()
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private var app: Context? = null
    private val completionLock = Mutex()
    private val eventsLock = Mutex()
    private val mutableEnabled = MutableStateFlow(false)
    val enabled = mutableEnabled.asStateFlow()
    private val mutableStatus = MutableStateFlow(ReferralStatus())
    val status = mutableStatus.asStateFlow()
    val storeSubscribed = MutableStateFlow<Boolean?>(null)
    val welcome = MutableStateFlow<ReferralWelcome?>(null)
    val receipt = MutableStateFlow<ReferralReceipt?>(null)
    val invitation = MutableStateFlow<ReferralInvitation?>(null)
    val completionIssue = MutableStateFlow<ReferralCompletionIssue?>(null)
    val failed = MutableStateFlow(false)
    val loaded = MutableStateFlow(false)
    val presentingInvite = MutableStateFlow(false)
    var presentingRedemption = false
    var inviteSource = "unknown"
    private var statusUid: String? = null
    var onboardingCompletedUid: String? = null
        private set

    private val isEnabled
        get() = mutableEnabled.value && gate.enabledFor(FirebaseAuth.getInstance().currentUser?.uid)

    fun install(context: Context) {
        app = context.applicationContext
    }

    fun setEnabled(value: Boolean, uid: String?) {
        gate.resolve(value, uid)
        mutableEnabled.value = value && uid != null
        if (!mutableEnabled.value) {
            completionIssue.value = null
            invitation.value = null
            welcome.value = null
            presentingInvite.value = false
        }
    }

    fun reset() {
        presentingInvite.value = false
        presentingRedemption = false
        storeSubscribed.value = null
        ReferralAttribution.clearIdentity()
        gate.resolve(false, null)
        mutableEnabled.value = false
        mutableStatus.value = ReferralStatus()
        welcome.value = null
        receipt.value = null
        invitation.value = null
        completionIssue.value = null
        statusUid = null
        loaded.value = false
        failed.value = false
        onboardingCompletedUid = null
    }

    fun didFinishOnboarding() {
        onboardingCompletedUid = FirebaseAuth.getInstance().currentUser?.uid
    }

    fun openInvite(source: String) {
        inviteSource = source
        presentingInvite.value = true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("corus_referrals_v1", Context.MODE_PRIVATE)

    fun code(raw: String) = ReferralLinks.parse(raw)

    fun pendingCode(context: Context): String? {
        val saved = prefs(context)
        val owner = saved.getString("uid", null)
        if (owner != null && owner != FirebaseAuth.getInstance().currentUser?.uid) return null
        if (System.currentTimeMillis() - saved.getLong("capturedAt", 0) !in 0 until 7 * 86400000L)
            return null
        return saved.getString("code", null)
    }

    fun capture(context: Context, raw: String): Boolean {
        val value = code(raw) ?: return false
        val saved = prefs(context)
        if (
            saved.getString("code", null) == value &&
                System.currentTimeMillis() - saved.getLong("capturedAt", 0) < 7 * 86400000L
        )
            return true
        saved
            .edit()
            .putString("code", value)
            .putLong("capturedAt", System.currentTimeMillis())
            .remove("uid")
            .remove("submitted")
            .apply()
        invitation.value = null
        scope.launch { refreshInvitation(context) }
        return true
    }

    suspend fun refreshInvitation(context: Context) {
        val value = pendingCode(context) ?: return
        if (!isEnabled || invitation.value?.code == value) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val resolved =
            withContext(Dispatchers.IO) {
                try {
                    val connection =
                        java.net
                            .URL(
                                "https://us-central1-cymbal-fresh.cloudfunctions.net/resolveReferralInvite?code=$value"
                            )
                            .openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    try {
                        if (connection.responseCode == 200)
                            JSONObject(
                                connection.inputStream.bufferedReader().use { it.readText() }
                            )
                        else null
                    } finally {
                        connection.disconnect()
                    }
                } catch (_: Exception) {
                    null
                }
            } ?: return
        if (
            !isEnabled ||
                FirebaseAuth.getInstance().currentUser?.uid != uid ||
                pendingCode(context) != value
        )
            return
        val canonical = resolved.optString("code")
        if (
            !canonical.matches(Regex("[a-f0-9]{24}")) || resolved.optString("displayName").isBlank()
        )
            return
        prefs(context).edit().putString("code", canonical).apply()
        invitation.value = ReferralInvitation(canonical, resolved.getString("displayName"))
        if (uid != null && onboardingCompletedUid == uid)
            completePending(context, afterOnboarding = true)
    }

    suspend fun validateAndCapture(context: Context, raw: String): Boolean {
        val value = code(raw) ?: return false
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        return try {
            val result =
                call(
                    "validateReferralInvite",
                    mapOf("code" to value, "source" to "settings_redeem"),
                )
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) false
            else
                capture(context, result["code"] as? String ?: value).also {
                    if (it) {
                        refreshInvitation(context)
                        scope.launch { track("code_validation_result", source = "settings_redeem", outcome = "accepted") }
                    }
                }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            scope.launch { track("code_validation_result", source = "settings_redeem", outcome = "rejected",
                errorCode = (e as? FirebaseFunctionsException)?.code?.name?.lowercase()?.replace('_', '-') ?: "unknown") }
            false
        }
    }

    private var referrerReading = false

    fun readInstallReferrer(context: Context) {
        if (!isEnabled || referrerReading) return
        val saved = prefs(context)
        if (saved.getBoolean("installReferrerRead", false)) return
        val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
        referrerReading = true
        fun close() {
            referrerReading = false
            client.endConnection()
        }
        try {
            client.startConnection(
                object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        try {
                            if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                                val details = client.installReferrer
                                val uri = Uri.parse("https://corus.fm/?${details.installReferrer}")
                                val value =
                                    uri.getQueryParameter("referral_code")
                                        ?: ReferralLinks.attribution(
                                            uri.getQueryParameter("deep_link_value"),
                                            uri.getQueryParameter("deep_link_sub1"),
                                            uri.getQueryParameter("af_dp"),
                                        )
                                if (value != null && saved.getString("code", null) == null)
                                    capture(context, value)
                                saved.edit().putBoolean("installReferrerRead", true).apply()
                                scope.launch { session(context) }
                            } else if (
                                responseCode ==
                                    InstallReferrerClient.InstallReferrerResponse
                                        .FEATURE_NOT_SUPPORTED
                            )
                                saved.edit().putBoolean("installReferrerRead", true).apply()
                        } catch (_: Exception) {
                            /* Transient failures retry. */
                        } finally {
                            close()
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        close()
                    }
                }
            )
        } catch (_: Exception) {
            close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun call(name: String, data: Map<String, Any> = emptyMap()): Map<String, Any?> {
        val retained =
            name in
                listOf(
                    "getReferralStatus",
                    "getReferralReceipt",
                    "claimReferralReward",
                    "getReferralHistory",
                )
        check(
            isEnabled || (app != null && retained && FirebaseAuth.getInstance().currentUser != null)
        ) {
            "Referrals are disabled"
        }
        val payload = data.toMutableMap()
        if (fm.corus.android.BuildConfig.DEBUG) payload["testMode"] = true
        return FirebaseFunctions.getInstance()
            .getHttpsCallable(name)
            .call(payload)
            .await()
            .getData() as? Map<String, Any?> ?: error("Invalid referral response")
    }

    private fun status(value: Map<String, Any?>): ReferralStatus {
        val canonical = value["code"] as? String ?: ""
        return ReferralStatus(
            value["enabled"] == true,
            canonical,
            ReferralLinks.brandedURL(canonical) ?: value["url"] as? String ?: "",
            (value["count"] as? Number)?.toInt() ?: 0,
            value["rewardEligible"] == true,
            value["rewardEarned"] == true,
            value["rewardStatus"] as? String ?: "locked",
            value["canAcceptInvite"] == true,
            value["shortCode"] as? String ?: "",
            (value["rewardEndAtMs"] as? Number)?.toLong(),
        )
    }

    suspend fun reload() {
        if (!mutableEnabled.value && app == null) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (statusUid != uid) {
            mutableStatus.value = ReferralStatus()
            receipt.value = null
            welcome.value = null
            loaded.value = false
            statusUid = uid
        }
        try {
            val value = call("getReferralStatus")
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
            mutableStatus.value = status(value)
            failed.value = false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            failed.value = true
        }
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
        loaded.value = true
        try {
            val value = call("getReferralReceipt")
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
            val inviter = value["inviter"] as? Map<*, *>
            receipt.value =
                ReferralReceipt(
                    value["redeemed"] == true,
                    value["giftStatus"] as? String,
                    (value["giftEndAtMs"] as? Number)?.toLong(),
                    (inviter?.get("username") as? String)?.let { "@$it" }
                        ?: inviter?.get("displayName") as? String,
                    value["appliedCode"] as? String,
                )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    fun recordAttribution(context: Context, outcome: String, deferred: Boolean) {
        ReferralAttributionEventStore(context).capture(outcome, deferred)
        com.google.firebase.analytics.FirebaseAnalytics.getInstance(context).logEvent(
            "referral_attribution_result",
            android.os.Bundle().apply {
                putString("source", "appsflyer")
                putString("outcome", outcome)
                putString("error_code", if (deferred) "deferred" else "direct")
                putBoolean("test_mode", fm.corus.android.BuildConfig.DEBUG)
            },
        )
    }

    /** UI dismissal must not cancel a funnel event waiting for the durable queue. */
    fun trackInBackground(
        type: String,
        eventId: String = UUID.randomUUID().toString(),
        code: String? = null,
        source: String = "unknown",
        outcome: String? = null,
        errorCode: String? = null,
    ) {
        scope.launch { track(type, eventId, code, source, outcome, errorCode) }
    }

    suspend fun track(
        type: String,
        eventId: String = UUID.randomUUID().toString(),
        code: String? = null,
        source: String = "unknown",
        outcome: String? = null,
        errorCode: String? = null,
        recordAnalytics: Boolean = true,
    ) {
        if (!isEnabled) return
        val context = app ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val analytics = referralAnalyticsEvent(type)
        if (recordAnalytics) com.google.firebase.analytics.FirebaseAnalytics.getInstance(context)
            .logEvent(
                analytics.name,
                android.os.Bundle().apply {
                    putString("source", analytics.source ?: source)
                    analytics.action?.let { putString("action", it) }
                    outcome?.let { putString("outcome", it) }
                    errorCode?.let { putString("error_code", it) }
                    putBoolean("test_mode", fm.corus.android.BuildConfig.DEBUG)
                },
            )
        eventsLock.withLock {
            val saved = prefs(context)
            val queue = JSONArray(saved.getString("events", "[]"))
            val data =
                JSONObject()
                    .put("type", type)
                    .put("eventId", eventId)
                    .put("platform", "android")
                    .put("source", source)
            code?.let { data.put("code", it) }
            outcome?.let { data.put("outcome", it) }
            errorCode?.let { data.put("errorCode", it) }
            if (fm.corus.android.BuildConfig.DEBUG) data.put("testMode", true)
            if (
                (0 until queue.length()).none {
                    queue.getJSONObject(it).let { e ->
                        e.optString("id") == eventId && e.optString("uid") == uid
                    }
                }
            )
                queue.put(JSONObject().put("uid", uid).put("id", eventId).put("data", data))
            val capped = JSONArray()
            for (i in maxOf(0, queue.length() - 200) until queue.length()) capped.put(queue.get(i))
            saved.edit().putString("events", capped.toString()).apply()
            for (n in 0 until 30) {
                if (!isEnabled || FirebaseAuth.getInstance().currentUser?.uid != uid) break
                val index =
                    (0 until capped.length()).firstOrNull {
                        capped.getJSONObject(it).optString("uid") == uid
                    } ?: break
                val entry = capped.getJSONObject(index).getJSONObject("data")
                try {
                    call(
                        "trackReferralEvent",
                        entry.keys().asSequence().associateWith { entry.get(it) },
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (
                        (e as? FirebaseFunctionsException)?.code !in
                            listOf(
                                FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                                FirebaseFunctionsException.Code.PERMISSION_DENIED,
                            )
                    )
                        break
                }
                capped.remove(index)
                saved.edit().putString("events", capped.toString()).apply()
            }
        }
    }

    suspend fun completePending(context: Context, afterOnboarding: Boolean = false): Boolean =
        completionLock.withLock {
            if (!isEnabled) return@withLock false
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withLock false
            val value = pendingCode(context) ?: return@withLock false
            val saved = prefs(context)
            if (!afterOnboarding && !saved.getBoolean("submitted", false)) return@withLock false
            completionIssue.value = null
            saved.edit().putString("uid", uid).putBoolean("submitted", true).apply()
            scope.launch { track("first_open", "first_open_$value", value) }
            try {
                val result =
                    call(
                        "completeReferralOnboarding",
                        mapOf("code" to value, "platform" to "android"),
                    )
                if (
                    !isEnabled ||
                        FirebaseAuth.getInstance().currentUser?.uid != uid ||
                        pendingCode(context) != value
                )
                    return@withLock false
                if (result["qualified"] != true) {
                    completionIssue.value = ReferralCompletionIssue.InvalidInvitation
                    scope.launch { track("completion_result", source = if (afterOnboarding) "onboarding" else "session", outcome = "invalidInvitation") }
                    return@withLock false
                }
                val inviter = result["inviter"] as? Map<*, *> ?: error("Missing inviter")
                welcome.value =
                    ReferralWelcome(
                        inviter["uid"] as? String ?: "",
                        inviter["displayName"] as? String ?: "",
                        result["giftStatus"] as? String ?: "pending",
                        (result["giftEndAtMs"] as? Number)?.toLong(),
                    )
                ReferralAttribution.recordQualifiedSignup(uid)
                if (welcome.value?.isActive == true || welcome.value?.giftStatus == "expired") {
                    saved.edit().remove("code").remove("submitted").remove("uid").apply()
                    invitation.value = null
                    if (welcome.value?.isActive == true)
                        com.revenuecat.purchases.Purchases.sharedInstance
                            .invalidateCustomerInfoCache()
                    else welcome.value = null
                }
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!isEnabled || FirebaseAuth.getInstance().currentUser?.uid != uid)
                    return@withLock false
                val code = (e as? FirebaseFunctionsException)?.code
                val permanent =
                    code in
                        listOf(
                            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                            FirebaseFunctionsException.Code.PERMISSION_DENIED,
                            FirebaseFunctionsException.Code.ALREADY_EXISTS,
                            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                            FirebaseFunctionsException.Code.NOT_FOUND,
                        )
                if (permanent) {
                    saved.edit().remove("code").remove("submitted").remove("uid").apply()
                    invitation.value = null
                }
                completionIssue.value =
                    if (!permanent) ReferralCompletionIssue.TemporaryFailure
                    else if (code == FirebaseFunctionsException.Code.ALREADY_EXISTS)
                        ReferralCompletionIssue.AlreadyRedeemed
                    else ReferralCompletionIssue.InvalidInvitation
                scope.launch { track("completion_result", source = if (afterOnboarding) "onboarding" else "session",
                    outcome = when (completionIssue.value) {
                        ReferralCompletionIssue.AlreadyRedeemed -> "alreadyRedeemed"
                        ReferralCompletionIssue.TemporaryFailure -> "temporary_failure"
                        else -> "invalidInvitation"
                    }, errorCode = code?.name?.lowercase()?.replace('_', '-') ?: "unknown") }
                !permanent
            }
        }

    private val sessionLock = Mutex()

    suspend fun session(context: Context) {
        if (!isEnabled || !sessionLock.tryLock()) return
        try {
            ReferralAttribution.flushRegistration()
            refreshInvitation(context)
            completePending(context)
            val owner = FirebaseAuth.getInstance().currentUser?.uid
            if (owner != null) {
                val store = ReferralAttributionEventStore(context)
                store.pending()?.let { event ->
                    track("attribution_result", eventId = event.id, source = "appsflyer", outcome = event.outcome,
                        errorCode = if (event.deferred) "deferred" else "direct", recordAnalytics = false)
                    if (FirebaseAuth.getInstance().currentUser?.uid == owner) store.acknowledge(event.id)
                }
            }
            track("session", "session_${System.currentTimeMillis() / 3600000}")
        } finally {
            sessionLock.unlock()
        }
    }

    suspend fun claim(): ReferralClaimOutcome {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        return try {
            val value = call("claimReferralReward", mapOf("platform" to "android"))
            if (FirebaseAuth.getInstance().currentUser?.uid != uid)
                return ReferralClaimOutcome.Failed
            mutableStatus.value = status(value)
            trackInBackground("claim_result", source = "invite_sheet", outcome = mutableStatus.value.rewardStatus)
            com.revenuecat.purchases.Purchases.sharedInstance.invalidateCustomerInfoCache()
            when (mutableStatus.value.rewardStatus) {
                "active" -> ReferralClaimOutcome.Applied
                "waiting_for_subscription" -> ReferralClaimOutcome.Saved
                "claiming" -> ReferralClaimOutcome.Pending
                else -> ReferralClaimOutcome.Failed
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            failed.value = true
            trackInBackground("claim_result", source = "invite_sheet", outcome = "failed",
                errorCode = (e as? FirebaseFunctionsException)?.code?.name?.lowercase()?.replace('_', '-') ?: "unknown")
            ReferralClaimOutcome.Failed
        }
    }

    fun dismissWelcome() {
        welcome.value = null
        completionIssue.value = null
    }
}
