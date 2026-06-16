package fm.corus.android.service

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.CustomSignals
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
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    /// Dev override store. SharedPreferences-backed so toggles persist
    /// across app restarts. Reads only happen in DEBUG builds — see
    /// `commentControlsOnPosts` getter. Never read in release.
    private val devPrefs by lazy {
        context.getSharedPreferences("corus_dev_flags", Context.MODE_PRIVATE)
    }

    /// Last-known feed-flag values, persisted across launches. Mirrors iOS,
    /// which hydrates these flags from UserDefaults in init() and refreshes
    /// them after every fetch. On a cold launch the Firebase RC getters return
    /// the type default (false) until the disk-cached activated config finishes
    /// loading, which makes flag-gated UI (the feed-mode chevron, the
    /// default-mode resolution) pop in. Reading the cached value during that
    /// window keeps the first frame correct. Refreshed in fetchAndActivate().
    private val flagCache by lazy {
        context.getSharedPreferences("corus_rc_cache", Context.MODE_PRIVATE)
    }

    /// Returns the live activated value when Remote Config has one this process,
    /// otherwise the last value we persisted (so feed-gated UI renders correctly
    /// before the disk-cached config loads / a fetch completes).
    private fun feedFlag(key: String): Boolean {
        val value = remoteConfig.getValue(key)
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
            value.asBoolean()
        } else {
            flagCache.getBoolean(key, value.asBoolean())
        }
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

    val paywallDefaultYearly: Boolean
        get() = remoteConfig.getBoolean("paywall_default_yearly")

    val gifSupport: Boolean
        get() = remoteConfig.getBoolean("gif_support")

    val serverNotificationsEnabled: Boolean
        get() = remoteConfig.getBoolean("server_notifications_enabled")

    /// Master gate for the per-post save COUNT shown next to the bookmark.
    /// When false the bookmark renders exactly as before (no number); the
    /// server-side saveCount is maintained regardless, so flipping this on
    /// reveals the already-accumulated counts with no rebuild. Keep OFF until
    /// the backend is deployed + backfilled and all clients have shipped.
    val saveCountEnabled: Boolean
        get() = remoteConfig.getBoolean("save_count_enabled")

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

    // Favorite-people cap. `favoritePeopleCapEnforced` is the master switch —
    // false means no cap at all. Mirrors the save cap above.
    val favoritePeopleCapEnforced: Boolean
        get() = remoteConfig.getBoolean("favorite_people_cap_enforced")

    val favoritePeopleCapLimit: Int
        get() {
            val v = remoteConfig.getLong("favorite_people_cap_limit").toInt()
            return if (v > 0) v else 3
        }

    val soundcloudEnabled: Boolean
        get() = remoteConfig.getBoolean("soundcloud_enabled")

    /// Master gate for the TIDAL music-service integration (onboarding + settings
    /// service picker). Keep OFF until web + iOS + Android all ship — otherwise a
    /// user could pick TIDAL on one client with no support on another. Mirrors
    /// iOS/web `tidal_enabled`.
    val tidalEnabled: Boolean
        get() {
            // ⚠️ TEMPORARY LOCAL TEST OVERRIDE — REVERT BEFORE MERGE.
            // Forces TIDAL on for local testing before the Remote Config key is
            // created / flipped. Restore the line below for RC control.
            return true
            // return remoteConfig.getBoolean("tidal_enabled")
        }

    /// Master gate for the Deezer link-out integration (onboarding + settings
    /// service picker + post link-out). Mirrors `tidalEnabled` / iOS+web
    /// `deezer_enabled`. Keep OFF until web + iOS + Android all ship.
    val deezerEnabled: Boolean
        get() {
            // ⚠️ TEMPORARY LOCAL TEST OVERRIDE — REVERT BEFORE MERGE.
            // Forces Deezer on for local testing. Restore the line below for RC control.
            return true
            // return remoteConfig.getBoolean("deezer_enabled")
        }

    val newReleaseFilterClubOnly: Boolean
        get() = remoteConfig.getBoolean("new_release_filter_club_only")

    val stylePack1Enabled: Boolean
        get() = remoteConfig.getBoolean("style_pack_1_enabled")

    /// Master gate for the algorithmically-ranked "For You" feed mode.
    /// When false, the chevron toggle next to the Corus wordmark is hidden
    /// and the feed behaves identically to today (Following-only).
    val forYouEnabled: Boolean
        get() = feedFlag("for_you_enabled")

    /// Gate for the "Trending" feed mode — same ranking as For You but the
    /// candidate pool is the whole app, not just your follows. Shares the
    /// `trending_feed_enabled` RC key with iOS.
    val trendingFeedEnabled: Boolean
        get() = feedFlag("trending_feed_enabled")

    /// Gate for the entire Favorites feature: star button on profiles + the
    /// Favorites feed mode. Shares the `favorites_enabled` RC key with iOS.
    val favoritesEnabled: Boolean
        get() = feedFlag("favorites_enabled")

    /// Gates the "Someone added you to their favorites" push + in-app row.
    /// Server-authoritative on the backend; mirrored here for completeness.
    val favoritesPushEnabled: Boolean
        get() = remoteConfig.getBoolean("favorites_push_enabled")

    /// Gate for play-milestone notifications ("N plays on your corus"). Gates
    /// the backend rows + push AND the "Plays" toggle row in notification
    /// settings. Stays off until the play_milestone row has shipped everywhere.
    /// Shares the `play_milestone_enabled` RC key with iOS/web.
    val playMilestoneEnabled: Boolean
        get() = feedFlag("play_milestone_enabled")

    /// When true, users who have never explicitly picked a feed mode open in
    /// For You instead of Recent. Only applies until the user picks a mode.
    val defaultForYouFeedEnabled: Boolean
        get() = feedFlag("default_for_you_feed_enabled")

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
            // ⚠️ TEMPORARY TEST OVERRIDE — REVERT BEFORE MERGE.
            // Forced ON to test the per-post comments-audience gate locally.
            // Restore the block below to flip back to Remote Config control.
            return true
            // if (BuildConfig.DEBUG && devPrefs.contains("comment_controls_on_posts")) {
            //     return devPrefs.getBoolean("comment_controls_on_posts", false)
            // }
            // return remoteConfig.getBoolean("comment_controls_on_posts")
        }

    // Tracks the UID last pushed as the `user_id` signal so we can tell when it
    // changes (login / account switch) and force a fresh fetch. Null-vs-unset is
    // distinguished by [hasAppliedUserSignal] so the first apply always counts.
    @Volatile private var lastAppliedUserSignal: String? = null
    @Volatile private var hasAppliedUserSignal = false

    /// Pushes the current user's UID into Remote Config as a custom signal so
    /// per-user targeting conditions (e.g. `app.customSignal['user_id']`) can
    /// resolve. Mirrors iOS (RemoteConfigService.setCurrentUserSignal). Safe to
    /// call repeatedly — last write wins. Must run *before* a fetch so the
    /// signal is evaluated against the returned values. Passing null clears the
    /// signal, matching the desired behavior on sign-out. Returns true when the
    /// applied UID differs from the previously applied one (login / switch),
    /// meaning any cached config was evaluated for a different user.
    suspend fun setCurrentUserSignal(uid: String?): Boolean {
        return try {
            val signals = CustomSignals.Builder()
                .put("user_id", uid)
                .build()
            remoteConfig.setCustomSignals(signals).await()
            val changed = !hasAppliedUserSignal || uid != lastAppliedUserSignal
            lastAppliedUserSignal = uid
            hasAppliedUserSignal = true
            changed
        } catch (e: Exception) {
            Log.w("RemoteConfig", "setCustomSignals failed", e)
            false
        }
    }

    suspend fun fetchAndActivate() {
        try {
            // Make sure the user-targeting custom signal is in place before
            // fetching so conditional values resolve correctly on the very
            // first response. Mirrors iOS.
            val signalChanged = setCurrentUserSignal(auth.currentUser?.uid)
            // When the signed-in user changes, the cached config was fetched and
            // evaluated against a *different* user_id signal. The normal 1h
            // throttle would serve that stale per-user result for up to an hour,
            // so per-user flags (e.g. favorites_enabled) wouldn't light up until
            // then. Bypass the throttle on a signal change so this user's
            // conditions resolve on this fetch.
            val minIntervalSeconds = if (signalChanged) 0L else 3600L
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(minIntervalSeconds)
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
                    "paywall_default_yearly" to false,
                    "gif_support" to false,
                    "server_notifications_enabled" to false,
                    "save_count_enabled" to false,
                    "save_cap_enforced" to false,
                    "save_cap_limit" to 25L,
                    "save_cap_warning_at" to 23L,
                    "favorite_people_cap_enforced" to false,
                    "favorite_people_cap_limit" to 3L,
                    "soundcloud_enabled" to false,
                    "tidal_enabled" to false,
                    "deezer_enabled" to false,
                    "comment_controls_on_posts" to false,
                    "new_release_filter_club_only" to false,
                    "style_pack_1_enabled" to false,
                    "for_you_enabled" to false,
                    "trending_feed_enabled" to false,
                    "favorites_enabled" to false,
                    "favorites_push_enabled" to false,
                    "play_milestone_enabled" to false,
                    "default_for_you_feed_enabled" to false,
                )
            ).await()
            val activated = remoteConfig.fetchAndActivate().await()
            cacheFeedFlags()
            logValues(activated)
        } catch (e: Exception) {
            Log.w("RemoteConfig", "fetchAndActivate failed", e)
        }
    }

    /// Persist the feed flags so the next cold launch renders the chevron /
    /// resolves the default mode correctly from the first frame (see feedFlag).
    /// Reads straight from Remote Config — by this point the fetch has activated.
    private fun cacheFeedFlags() {
        flagCache.edit()
            .putBoolean("for_you_enabled", remoteConfig.getBoolean("for_you_enabled"))
            .putBoolean("trending_feed_enabled", remoteConfig.getBoolean("trending_feed_enabled"))
            .putBoolean("favorites_enabled", remoteConfig.getBoolean("favorites_enabled"))
            .putBoolean("play_milestone_enabled", remoteConfig.getBoolean("play_milestone_enabled"))
            .putBoolean("default_for_you_feed_enabled", remoteConfig.getBoolean("default_for_you_feed_enabled"))
            .apply()
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
                "paywall_default_yearly=$paywallDefaultYearly " +
                "gif_support=$gifSupport " +
                "server_notifications_enabled=$serverNotificationsEnabled " +
                "save_cap_enforced=$saveCapEnforced " +
                "save_cap_limit=$saveCapLimit " +
                "save_cap_warning_at=$saveCapWarningAt " +
                "new_release_filter_club_only=$newReleaseFilterClubOnly " +
                "style_pack_1_enabled=$stylePack1Enabled " +
                "for_you_enabled=${remoteConfig.getBoolean("for_you_enabled")} " +
                "trending_feed_enabled=${remoteConfig.getBoolean("trending_feed_enabled")} " +
                "favorites_enabled=${remoteConfig.getBoolean("favorites_enabled")} " +
                "uid=${auth.currentUser?.uid}"
        )
    }
}
