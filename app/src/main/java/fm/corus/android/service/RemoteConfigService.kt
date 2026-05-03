package fm.corus.android.service

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigService @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) {
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
