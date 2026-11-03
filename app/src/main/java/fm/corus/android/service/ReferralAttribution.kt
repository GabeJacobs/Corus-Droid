package fm.corus.android.service

import android.app.Activity
import android.app.Application
import android.content.Intent
import com.appsflyer.AppsFlyerLib
import com.appsflyer.share.attribution.AppsFlyerRequestListener
import com.appsflyer.share.deeplink.DeepLinkResult
import com.google.firebase.auth.FirebaseAuth
import fm.corus.android.BuildConfig
import java.lang.ref.WeakReference
import kotlinx.coroutines.*

/** AppsFlyer 7.0.4: session readiness is latched independently of login/flag refresh. */
object ReferralAttribution {
    private lateinit var app: Application
    private var initialized = false
    @Volatile private var enabled = false
    private var launcher: WeakReference<Activity>? = null
    private var launcherCollected = false
    private val readiness = ReferralSDKReadiness()
    private val registrationsInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var pendingURL: String? = null
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    fun install(application: Application) {
        app = application
        ReferralService.install(app)
    }

    fun updateEnabled(value: Boolean) {
        if (!::app.isInitialized) return
        enabled =
            value &&
                BuildConfig.APPSFLYER_DEV_KEY.isNotBlank() &&
                !fm.corus.android.TestEnvironment.isActive
        if (!enabled) {
            if (initialized) AppsFlyerLib.getInstance().stop(true, app)
            return
        }
        val sdk = AppsFlyerLib.getInstance()
        if (!initialized) {
            initialized = true
            sdk.setDebugLog(false)
            sdk.setDisableAdvertisingIdentifiers(true)
            sdk.setCollectAndroidID(false)
            sdk.disableAppSetId()
            sdk.setDisableNetworkData(true)
            sdk.setAppInviteOneLink("fgUi")
            sdk.subscribeForDeepLink { result ->
                if (!enabled) return@subscribeForDeepLink
                val link = result.deepLink
                val code =
                    if (result.status == DeepLinkResult.Status.FOUND && link != null)
                        ReferralLinks.attribution(
                            link.deepLinkValue,
                            link.getStringValue("deep_link_sub1"),
                            link.getStringValue("af_dp"),
                        )
                    else null
                val outcome =
                    if (code != null) "recovered"
                    else if (result.status == DeepLinkResult.Status.ERROR) "error"
                    else if (result.status == DeepLinkResult.Status.FOUND) "invalid" else "not_found"
                scope.launch {
                    if (!enabled) return@launch
                    if (code != null) ReferralService.capture(app, code)
                    ReferralService.recordAttribution(app, outcome, link?.isDeferred == true)
                    if (FirebaseAuth.getInstance().currentUser != null)
                        scope.launch { ReferralService.session(app) }
                    if (
                        code != null &&
                            ReferralService.onboardingCompletedUid ==
                                FirebaseAuth.getInstance().currentUser?.uid
                    )
                        ReferralService.completePending(app, afterOnboarding = true)
                }
            }
            sdk.init(BuildConfig.APPSFLYER_DEV_KEY, null, app)
            sdk.registerSessionReadyListener {
                scope.launch {
                    readiness.ready()
                    startIfReady()
                }
            }
        }
        launcher?.get()?.let {
            sdk.collectDataFromLauncherActivity(it)
            launcherCollected = true
        }
        sdk.stop(false, app)
        startIfReady()
        pendingURL?.let {
            sdk.performDeepLinking(it, true)
            pendingURL = null
        }
        flushRegistration()
    }

    private fun startIfReady() {
        if (!readiness.takeStart(enabled && launcherCollected)) return
        AppsFlyerLib.getInstance().start()
    }

    fun collectLauncher(activity: Activity) {
        launcher = WeakReference(activity)
        if (initialized) {
            AppsFlyerLib.getInstance().collectDataFromLauncherActivity(activity)
            launcherCollected = true
            startIfReady()
        }
    }

    fun handle(intent: Intent?) {
        val raw = intent?.data?.toString() ?: return
        if (ReferralLinks.parse(raw) == null) return
        pendingURL = raw
        if (initialized && enabled) {
            AppsFlyerLib.getInstance().performDeepLinking(raw, true)
            pendingURL = null
        }
    }

    fun clearIdentity() {
        if (initialized) AppsFlyerLib.getInstance().setCustomerUserId("")
    }

    fun recordQualifiedSignup(uid: String) {
        if (!::app.isInitialized || FirebaseAuth.getInstance().currentUser?.uid != uid) return
        val prefs = app.getSharedPreferences("corus_referral_attribution", 0)
        if (!prefs.getBoolean("registered_$uid", false))
            prefs.edit().putBoolean("pending_$uid", true).apply()
        flushRegistration()
    }

    fun flushRegistration() {
        if (!enabled || !initialized) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = app.getSharedPreferences("corus_referral_attribution", 0)
        if (!prefs.getBoolean("pending_$uid", false) || !registrationsInFlight.add(uid)) return
        val sdk = AppsFlyerLib.getInstance()
        sdk.setCustomerUserId(uid)
        sdk.logEvent(
            app,
            "af_complete_registration",
            emptyMap(),
            object : AppsFlyerRequestListener {
                override fun onSuccess() {
                    prefs.edit().putBoolean("registered_$uid", true).remove("pending_$uid").apply()
                    registrationsInFlight.remove(uid)
                }

                override fun onError(code: Int, description: String) {
                    registrationsInFlight.remove(uid)
                }
            },
        )
    }
}
