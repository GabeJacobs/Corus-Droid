package fm.corus.android.service

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.CustomSignals
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.BuildConfig
import fm.corus.android.domain.FeedModeOrder
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigService @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    init {
        // Apply in-app defaults the moment this service is constructed. This is a
        // purely local operation (no network), so unlike fetchAndActivate() it is
        // never delayed by a slow/failing fetch or App Check token minting. Without
        // this, getBoolean() returns the Boolean type-default (false) for any flag
        // until the network fetch's setDefaultsAsync lands, which on a fresh signup
        // silently dropped the flag-gated TIDAL/Deezer cards from the player picker.
        remoteConfig.setDefaultsAsync(DEFAULTS)
    }

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

    /// Returns the live/activated value, falling back to [default] while Remote
    /// Config has no value at all for [key] (source == STATIC). That STATIC
    /// window happens on a fresh signup before the in-app defaults are applied —
    /// without this fallback getBoolean returns the Boolean type-default (false),
    /// which silently drops flag-gated UI. Defaults are applied locally in init(),
    /// so the window is small, but the picker must be correct from its first frame.
    private fun flagWithDefault(key: String, default: Boolean): Boolean {
        val value = remoteConfig.getValue(key)
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
            default
        } else {
            value.asBoolean()
        }
    }

    /// String mirror of [feedFlag]: returns the live activated value when Remote
    /// Config has one this process, otherwise the last value we persisted (so
    /// feed-gated UI renders correctly before the disk-cached config loads).
    private fun feedString(key: String): String {
        val value = remoteConfig.getValue(key)
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
            value.asString()
        } else {
            flagCache.getString(key, value.asString()) ?: value.asString()
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

    /** Instagram-style group messaging gate (create/expand). Default false; on
     *  per cohort via the user_id allowlist condition. */
    val groupMessagingEnabled: Boolean
        get() = remoteConfig.getBoolean("group_messaging_enabled")

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
        get() = flagWithDefault("tidal_enabled", true)

    /// Master gate for the Deezer link-out integration (onboarding + settings
    /// service picker + post link-out). Mirrors `tidalEnabled` / iOS+web
    /// `deezer_enabled`. Keep OFF until web + iOS + Android all ship.
    val deezerEnabled: Boolean
        get() = flagWithDefault("deezer_enabled", true)

    /// Client parity for the /following read-cost optimization: when true (default),
    /// list readers fetch the denormalized users_v2/{uid}/aggregates/following doc
    /// (one read) instead of scanning the whole /following subcollection, falling
    /// back to the scan when that doc is missing or oversize. Pure read-path switch:
    /// flipping it OFF in the console instantly reverts every client to the
    /// subcollection scan with no rebuild. Mirrors iOS `following_denorm_reads_enabled`.
    val followingDenormReadsEnabled: Boolean
        get() = flagWithDefault("following_denorm_reads_enabled", true)

    val newReleaseFilterClubOnly: Boolean
        get() = remoteConfig.getBoolean("new_release_filter_club_only")

    val stylePack1Enabled: Boolean
        get() = remoteConfig.getBoolean("style_pack_1_enabled")

    /// Gate for who may pick the staff-only "Corus" profile flair. Default false
    /// (today's behavior) restricts the picker option to staff, plus existing
    /// holders who keep seeing it so their selection isn't blanked during the
    /// phase-out; flipping to true reopens it to everyone. Defaults false so the
    /// restrictive state is the fallback before the first RC fetch. Mirrors
    /// web/iOS `corus_flair_open`. Display/rendering of the flair is unaffected.
    val corusFlairOpen: Boolean
        get() = remoteConfig.getBoolean("corus_flair_open")

    /// Gate for the "Trending" feed mode — the ranked `getForYouFeed` callable
    /// scoped to the whole app's most-engaged posts (not just your follows).
    /// Shares the `trending_feed_enabled` RC key with iOS.
    val trendingFeedEnabled: Boolean
        get() = feedFlag("trending_feed_enabled")

    /// Gate for the entire Favorites feature: star button on profiles + the
    /// Favorites feed mode. Shares the `favorites_enabled` RC key with iOS.
    val favoritesEnabled: Boolean
        get() = feedFlag("favorites_enabled")

    /// Gate for the premium "Taste Matches" feed mode (Club-gated curator-first
    /// discovery). Shares the `taste_matches_enabled` RC key with iOS/web. Off by
    /// default → zero UI change. `tasteMatchesTester` comps internal testers so
    /// they can see the feed (and bypass the paywall) while it's dark.
    val tasteMatchesEnabled: Boolean
        get() = feedFlag("taste_matches_enabled")

    val tasteMatchesTester: Boolean
        get() = feedFlag("taste_matches_tester")

    /// Gate for the artist / album / director destination pages: search rows,
    /// tappable artist+director names, and the pages themselves. Shares the
    /// `artist_pages_enabled` RC key with iOS/web so one console flip reverts
    /// every client. Flag off = the app behaves byte-identically to today.
    /// Uses the init-race-safe feedFlag path (cached across launches) so the
    /// gated search tabs/rows render correctly from the first frame.
    val artistPagesEnabled: Boolean
        get() = feedFlag("artist_pages_enabled")

    /// Send-side gate for sharing an artist / album / director (the "..." Share
    /// menu on those destination pages + the Artist/Album/Director items in the
    /// DM composer "+" menu). Launch-dark: receiving/rendering those DMs is always
    /// on in an updated client; flip TRUE once enough clients can render them.
    /// Shares `entity_share_enabled` with iOS/web. Init-race-safe feedFlag path.
    val entityShareEnabled: Boolean
        get() = feedFlag("entity_share_enabled")

    /// Send-side gate for sharing a user's *profile* (the "Share Profile" action
    /// on a profile screen opens the in-app Corus share sheet → DM instead of the
    /// native Android share sheet). Launch-dark: receiving/rendering a
    /// shared-profile DM is always on in an updated client; flip TRUE once enough
    /// clients can render them. Shares `profile_share_enabled` with iOS/web.
    val profileShareEnabled: Boolean
        get() = feedFlag("profile_share_enabled")

    /// Unified search: blended zero-state discovery feed + All/Users/Music/
    /// Film/Hashtags filter chips instead of the pre-segmented tabs. Shares
    /// the key with web (already released there). Uses the init-race-safe
    /// feedFlag path so the tabless layout renders correctly from the first
    /// frame on cold start.
    val unifiedSearchEnabled: Boolean
        get() = feedFlag("unified_search_enabled")

    /// One-time "feed switch hint" discovery coachmark (the bubble under the
    /// Corus logo teaching that the logo switches feed modes). Master gate;
    /// ships dark. Shares `feed_switch_hint_enabled` with iOS/web. Uses the
    /// init-race-safe feedFlag path so a fresh signup doesn't briefly read the
    /// wrong value, though the hint only appears after several sessions anyway.
    val feedSwitchHintEnabled: Boolean
        // Dev override (DEBUG only) via the `corus_dev_flags` prefs — same recipe
        // as commentControlsOnPosts below. Lets local testing force the hint on
        // without touching the RC console; release + unit tests read the flag.
        get() {
            if (BuildConfig.DEBUG && devPrefs.contains("feed_switch_hint_enabled")) {
                return devPrefs.getBoolean("feed_switch_hint_enabled", false)
            }
            return feedFlag("feed_switch_hint_enabled")
        }

    /// App opens required before the hint can appear. Mirrors
    /// `feed_switch_hint_min_session`.
    val feedSwitchHintMinSession: Int
        get() {
            if (BuildConfig.DEBUG && devPrefs.contains("feed_switch_hint_min_session")) {
                return devPrefs.getInt("feed_switch_hint_min_session", 3)
            }
            val v = remoteConfig.getLong("feed_switch_hint_min_session").toInt()
            return if (v > 0) v else 3
        }

    /// Lifetime cap on how many times the hint is shown. Mirrors
    /// `feed_switch_hint_max_impressions`.
    val feedSwitchHintMaxImpressions: Int
        get() {
            if (BuildConfig.DEBUG && devPrefs.contains("feed_switch_hint_max_impressions")) {
                return devPrefs.getInt("feed_switch_hint_max_impressions", 3)
            }
            val v = remoteConfig.getLong("feed_switch_hint_max_impressions").toInt()
            return if (v > 0) v else 3
        }

    /// Order of the feed switcher menu, driven by the `feed_mode_order` Remote
    /// Config string (comma-separated camelCase tokens, e.g.
    /// "following,trending,tasteMatches,favorites"). Parsed leniently — see
    /// [FeedModeOrder.parse]. Shares the key with iOS/web so a single console
    /// change reorders every client. Per-mode availability gates still apply on
    /// top, so this only reorders the rows that are already eligible to show.
    val feedModeOrder: List<String>
        get() = FeedModeOrder.parse(feedString("feed_mode_order"))

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
            remoteConfig.setDefaultsAsync(DEFAULTS).await()
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
            .putBoolean("trending_feed_enabled", remoteConfig.getBoolean("trending_feed_enabled"))
            .putBoolean("favorites_enabled", remoteConfig.getBoolean("favorites_enabled"))
            .putBoolean("play_milestone_enabled", remoteConfig.getBoolean("play_milestone_enabled"))
            .putBoolean("artist_pages_enabled", remoteConfig.getBoolean("artist_pages_enabled"))
            .putBoolean("entity_share_enabled", remoteConfig.getBoolean("entity_share_enabled"))
            .putBoolean("profile_share_enabled", remoteConfig.getBoolean("profile_share_enabled"))
            .putBoolean("unified_search_enabled", remoteConfig.getBoolean("unified_search_enabled"))
            .putBoolean("feed_switch_hint_enabled", remoteConfig.getBoolean("feed_switch_hint_enabled"))
            .putString("feed_mode_order", remoteConfig.getString("feed_mode_order"))
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
                "corus_flair_open=$corusFlairOpen " +
                "trending_feed_enabled=${remoteConfig.getBoolean("trending_feed_enabled")} " +
                "favorites_enabled=${remoteConfig.getBoolean("favorites_enabled")} " +
                "uid=${auth.currentUser?.uid}"
        )
    }

    companion object {
        /// In-app Remote Config defaults. Applied locally in init() (so flag-gated
        /// UI is correct before any network fetch) and re-applied in
        /// fetchAndActivate(). Single source of truth — keep in sync with the
        /// server template and the iOS/web defaults.
        private val DEFAULTS: Map<String, Any> = mapOf(
            "movie_mode" to true,
            "maintenance_mode" to false,
            "instagram_share_enabled" to true,
            "corus_club_enabled" to true,
            "vinyl_flip_enabled" to true,
            "review_prompt_enabled" to true,
            "maintenance_message" to "",
            "daily_post_limit_enabled" to true,
            "paywall_default_yearly" to false,
            "gif_support" to false,
            "group_messaging_enabled" to false,
            "server_notifications_enabled" to true,
            "save_count_enabled" to true,
            "save_cap_enforced" to true,
            "save_cap_limit" to 20L,
            "save_cap_warning_at" to 17L,
            "favorite_people_cap_enforced" to true,
            "favorite_people_cap_limit" to 4L,
            "soundcloud_enabled" to false,
            "tidal_enabled" to true,
            "deezer_enabled" to true,
            "following_denorm_reads_enabled" to true,
            "comment_controls_on_posts" to true,
            "new_release_filter_club_only" to false,
            "style_pack_1_enabled" to false,
            "corus_flair_open" to false,
            "trending_feed_enabled" to true,
            "favorites_enabled" to true,
            "favorites_push_enabled" to true,
            "play_milestone_enabled" to false,
            // Default FALSE in code — the server template currently sends true;
            // flipping the console key off must revert every client.
            "artist_pages_enabled" to false,
            "entity_share_enabled" to false,
            "profile_share_enabled" to false,
            "unified_search_enabled" to false,
            "feed_switch_hint_enabled" to false,
            "feed_switch_hint_min_session" to 3L,
            "feed_switch_hint_max_impressions" to 3L,
            "feed_mode_order" to FeedModeOrder.DEFAULT_RAW,
        )
    }
}
