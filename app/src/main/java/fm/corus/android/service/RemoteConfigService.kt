package fm.corus.android.service

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

    val giphySupport: Boolean
        get() = remoteConfig.getBoolean("giphy_support")

    val serverNotificationsEnabled: Boolean
        get() = remoteConfig.getBoolean("server_notifications_enabled")

    val commentAttachmentsEnabled: Boolean
        get() = remoteConfig.getBoolean("comment_attachments_enabled")

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
                    "giphy_support" to false,
                    "server_notifications_enabled" to false,
                    "comment_attachments_enabled" to true,
                    "save_cap_enforced" to false,
                    "save_cap_limit" to 25L,
                    "save_cap_warning_at" to 23L,
                )
            ).await()
            remoteConfig.fetchAndActivate().await()
        } catch (_: Exception) { }
    }
}
