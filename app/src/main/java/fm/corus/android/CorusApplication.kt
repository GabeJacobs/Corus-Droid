package fm.corus.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.hilt.android.HiltAndroidApp
import fm.corus.android.data.local.AlwaysPlayFullSongsDefaultMigration
import fm.corus.android.data.local.AppearanceDefaultMigration
import fm.corus.android.data.local.PlayFullSongsPostsModelMigration
import fm.corus.android.data.local.PlaybackModePromptRolloutMigration
import fm.corus.android.service.AppCheckTokenSource
import fm.corus.android.service.FeedSwitchHintManager
import javax.inject.Inject

@HiltAndroidApp
class CorusApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var feedSwitchHintManager: FeedSwitchHintManager
    @Inject lateinit var appCheckTokenSource: AppCheckTokenSource

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // Freeze the theme default (Light for existing installs, System for fresh
        // ones) at process start, before onboarding can run, so a brand-new user is
        // never mistaken for an existing one.
        AppearanceDefaultMigration.unsetThemeDefault(this)
        PlayFullSongsPostsModelMigration.runIfNeeded(this)
        AlwaysPlayFullSongsDefaultMigration.runIfNeeded(this)
        PlaybackModePromptRolloutMigration.runIfNeeded(this)
        if (TestEnvironment.isActive) {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
        }
        initAppCheck()
        initRevenueCat()
        createNotificationChannels()
        // Count this launch toward the feed-switch-hint session gate (device-local).
        feedSwitchHintManager.recordSession()
    }

    private fun initAppCheck() {
        val factory = if (BuildConfig.DEBUG) {
            // Pre-seed a fixed debug token so it's the same across emulator instances.
            // Register this token once in Firebase Console > App Check > Manage debug tokens.
            val token = BuildConfig.APP_CHECK_DEBUG_TOKEN
            if (token.isNotEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                getSharedPreferences(
                    "com.google.firebase.appcheck.debug.store.$persistenceKey",
                    MODE_PRIVATE
                ).edit().putString(
                    "com.google.firebase.appcheck.debug.DEBUG_SECRET", token
                ).apply()
            }
            try {
                val clazz = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                clazz.getMethod("getInstance").invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
            } catch (e: Exception) {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
        // Same as iOS: mint a token as soon as the provider is installed so
        // signup / first Firestore reads don't race a lazy first fetch.
        appCheckTokenSource.warmup()
    }

    private fun initRevenueCat() {
        Purchases.logLevel = LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(this, "goog_nkYdtPqaMcdBzvPVaQNRqjdIHAz").build()
        )
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val general = NotificationChannel(
            "corus_general",
            "General",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General notifications"
        }

        val social = NotificationChannel(
            "corus_social",
            "Social",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Likes, comments, follows, and mentions"
        }

        val messages = NotificationChannel(
            "corus_messages",
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Direct messages"
        }

        // Silent low-importance channel for the playback foreground-service
        // placeholder notification. media3 replaces it with the rich media
        // notification once the session attaches; the channel just has to
        // exist so we can post the placeholder without surprising the user.
        val playback = NotificationChannel(
            "corus_playback",
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Now playing controls"
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(general, social, messages, playback))
    }
}
