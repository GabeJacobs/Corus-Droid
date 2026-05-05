package fm.corus.android.service

import android.content.Context
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.BuildConfig
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigService @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    @ApplicationContext private val context: Context,
) {
    /// Dev override store. SharedPreferences-backed so toggles persist
    /// across app restarts. Reads only happen in DEBUG builds — see
    /// `commentControlsOnPosts` getter. Never read in release.
    private val devPrefs by lazy {
        context.getSharedPreferences("corus_dev_flags", Context.MODE_PRIVATE)
    }
    // Existing flags
    val movieModeEnabled: Boolean
        get() = remoteConfig.getBoolean("movie_mode")

    val maintenanceMode: Boolean
        get() = remoteConfig.getBoolean("maintenance_mode")

    // New flags (matching iOS RemoteConfigService)
    val instagramShareEnabled: Boolean
        get() = remoteConfig.getBoolean("instagram_share_enabled")

    val corusClubEnabled: Boolean
        get() = remoteConfig.getBoolean("corus_club_enabled")

    val vinylFlipEnabled: Boolean
        get() = remoteConfig.getBoolean("vinyl_flip_enabled")

    val reviewPromptEnabled: Boolean
        get() = remoteConfig.getBoolean("review_prompt_enabled")

    val maintenanceMessage: String
        get() = remoteConfig.getString("maintenance_message")

    val dailyPostLimitEnabled: Boolean
        get() = remoteConfig.getBoolean("daily_post_limit_enabled")

    val filterForClubMembersOnly: Boolean
        get() = remoteConfig.getBoolean("filter_for_club_members_only")

    val paywallDefaultYearly: Boolean
        get() = remoteConfig.getBoolean("paywall_default_yearly")

    val gifSupport: Boolean
        get() = remoteConfig.getBoolean("gif_support")

    val serverNotificationsEnabled: Boolean
        get() = remoteConfig.getBoolean("server_notifications_enabled")

    val saveCapEnforced: Boolean
        get() = remoteConfig.getBoolean("save_cap_enforced")

    val saveCapLimit: Int
        get() {
            val v = remoteConfig.getLong("save_cap_limit").toInt()
            return if (v > 0) v else 25
        }

    val saveCapWarningAt: Int
        get() {
            val v = remoteConfig.getLong("save_cap_warning_at").toInt()
            return if (v > 0) v else 23
        }

    val soundcloudEnabled: Boolean
        get() = remoteConfig.getBoolean("soundcloud_enabled")

    /**
     * Per-post comments-audience picker (Everyone / Followers / Off).
     * Keep this OFF until web + iOS + Android all ship the gate — otherwise
     * users on a client without the UI will tap Comment on a restricted
     * post and hit a permission-denied error. Server rules enforce
     * regardless of this flag.
     *
     * Dev override (DEBUG builds only): set via adb. The override is
     * persisted in the `corus_dev_flags` SharedPreferences file under key
     * `comment_controls_on_posts`. Quickest way to flip from a terminal:
     *
     *   adb shell run-as fm.corus.android.debug sh -c \\
     *     "echo '<?xml version=\\"1.0\\" encoding=\\"utf-8\\" standalone=\\"yes\\" ?>
     *      <map><boolean name=\\"comment_controls_on_posts\\" value=\\"true\\" /></map>' \\
     *      > /data/data/fm.corus.android.debug/shared_prefs/corus_dev_flags.xml"
     *
     * Then force-stop the app and relaunch. Stripped from release builds.
     */
    val commentControlsOnPosts: Boolean
        get() {
            if (BuildConfig.DEBUG && devPrefs.contains("comment_controls_on_posts")) {
                return devPrefs.getBoolean("comment_controls_on_posts", false)
            }
            return remoteConfig.getBoolean("comment_controls_on_posts")
        }

    suspend fun fetchAndActivate() {
        try {
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build()
            remoteConfig.setConfigSettingsAsync(settings).await()
            remoteConfig.setDefaultsAsync(
                mapOf(
                    "movie_mode" to false,
                    "maintenance_mode" to false,
                    "instagram_share_enabled" to false,
                    "corus_club_enabled" to false,
                    "vinyl_flip_enabled" to false,
                    "review_prompt_enabled" to false,
                    "maintenance_message" to "",
                    "daily_post_limit_enabled" to true,
                    "filter_for_club_members_only" to false,
                    "paywall_default_yearly" to false,
                    "gif_support" to false,
                    "server_notifications_enabled" to false,
                    "save_cap_enforced" to false,
                    "save_cap_limit" to 25L,
                    "save_cap_warning_at" to 23L,
                    "soundcloud_enabled" to false,
                    "comment_controls_on_posts" to false,
                )
            ).await()
            val activated = remoteConfig.fetchAndActivate().await()
            logValues(activated)
        } catch (e: Exception) {
            Log.w("RemoteConfig", "fetchAndActivate failed", e)
        }
    }

    private fun logValues(activated: Boolean) {
        Log.i(
            "RemoteConfig",
            "fetchAndActivate activated=$activated " +
                "soundcloud_enabled=$soundcloudEnabled " +
                "movie_mode=$movieModeEnabled " +
                "maintenance_mode=$maintenanceMode " +
                "instagram_share_enabled=$instagramShareEnabled " +
                "corus_club_enabled=$corusClubEnabled " +
                "vinyl_flip_enabled=$vinylFlipEnabled " +
                "review_prompt_enabled=$reviewPromptEnabled " +
                "daily_post_limit_enabled=$dailyPostLimitEnabled " +
                "filter_for_club_members_only=$filterForClubMembersOnly " +
                "paywall_default_yearly=$paywallDefaultYearly " +
                "gif_support=$gifSupport " +
                "server_notifications_enabled=$serverNotificationsEnabled " +
                "save_cap_enforced=$saveCapEnforced " +
                "save_cap_limit=$saveCapLimit " +
                "save_cap_warning_at=$saveCapWarningAt"
        )
    }
}
