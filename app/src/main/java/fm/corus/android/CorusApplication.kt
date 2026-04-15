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
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CorusApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var imageLoader: ImageLoader

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        initAppCheck()
        initRevenueCat()
        createNotificationChannels()
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

        manager.createNotificationChannels(listOf(general, social, messages))
    }
}
