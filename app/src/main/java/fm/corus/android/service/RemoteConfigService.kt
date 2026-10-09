package fm.corus.android.service

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.remoteconfig.CustomSignals
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.BuildConfig
import fm.corus.android.domain.FeedModeOrder
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigService @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    private val _initialFetchComplete = MutableStateFlow(false)
    /** Search waits for this instead of composing a partial flag-gated home. */
    val initialFetchComplete = _initialFetchComplete.asStateFlow()

    init {
        // Apply in-app defaults the moment this service is constructed. This is a
        // purely local operation (no network), so unlike fetchAndActivate() it is
        // never delayed by a slow/failing fetch or App Check token minting. Without
        // this, getBoolean() returns the Boolean type-default (false) for any flag
        // until the network fetch's setDefaultsAsync lands, which on a fresh signup
        // silently dropped the flag-gated TIDAL/Deezer cards from the player picker.
        remoteConfig.setDefaultsAsync(DEFAULTS)
        remoteConfig.addOnConfigUpdateListener(object : com.google.firebase.remoteconfig.ConfigUpdateListener {
            override fun onUpdate(update: com.google.firebase.remoteconfig.ConfigUpdate) {
                remoteConfig.activate().addOnSuccessListener {
                    cacheFeedFlags()
                    auth.currentUser?.uid?.takeIf { it == newTabResolvedUid }?.let { cacheFeedTabPresentation(it) }
                    _revision.value += 1
                }
            }
            override fun onError(error: com.google.firebase.remoteconfig.FirebaseRemoteConfigException) {
                Log.w("RemoteConfig", "Realtime update unavailable; keeping activated config", error)
            }
        })
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
        debugOverride(key)?.let { return it }
        presentedFeedValue(key)?.let { return it.toBoolean() }
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
        debugOverride(key)?.let { return it }
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
        presentedFeedValue(key)?.let { return it }
        val value = remoteConfig.getValue(key)
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
            value.asString()
        } else {
            flagCache.getString(key, value.asString()) ?: value.asString()
        }
    }
    val forYouDefaultMode: fm.corus.android.domain.ForYouTuningMode
        get() = fm.corus.android.domain.ForYouTuningMode.configured(feedString("for_you_default_mode"))

    // Existing flags
    val movieModeEnabled: Boolean
        get() = booleanFlag("movie_mode")

    val maintenanceMode: Boolean
        get() = booleanFlag("maintenance_mode")

    // New flags (matching iOS RemoteConfigService)
    val postToInstagramV2: Boolean
        get() = booleanFlag("post_to_instagram_v2")

    val instagramShareEnabled: Boolean
        get() = booleanFlag("instagram_share_enabled")

    val corusClubEnabled: Boolean
        get() = booleanFlag("corus_club_enabled")

    val vinylFlipEnabled: Boolean
        get() = booleanFlag("vinyl_flip_enabled")

    val reviewPromptEnabled: Boolean
        get() = booleanFlag("review_prompt_enabled")

    val maintenanceMessage: String
        get() = remoteConfig.getString("maintenance_message")

    /**
     * Public, short-lived MapKit JS credential for Android's Apple Maps
     * WebView. Remote Config lets us rotate it before expiry without an app
     * release. Debug may use a developer-local token instead.
     */
    val mapKitJsToken: String
        get() = BuildConfig.MAPKIT_JS_TOKEN.ifBlank {
            remoteConfig.getString("mapkit_js_token")
        }

    val dailyPostLimitEnabled: Boolean
        get() = booleanFlag("daily_post_limit_enabled")

    val paywallDefaultYearly: Boolean
        get() = booleanFlag("paywall_default_yearly")

    val gifSupport: Boolean
        get() = booleanFlag("gif_support")

    /** Instagram-style group messaging gate (create/expand). Default false; on
     *  per cohort via the user_id allowlist condition. */
    val groupMessagingEnabled: Boolean
        get() = booleanFlag("group_messaging_enabled")

    /** In-thread typing indicators. Default on; flip `typing_indicators_enabled`
     *  off to kill the feature with no app release. Mirrors iOS/web. */
    val typingIndicatorsEnabled: Boolean
        get() = booleanFlag("typing_indicators_enabled")

    /** DM video attachments. Default off; ON for @gabe + @clifton via RC.
     *  Shares `dm_video_enabled` with iOS/web. */
    val dmVideoEnabled: Boolean
        get() = booleanFlag("dm_video_enabled")

    val serverNotificationsEnabled: Boolean
        get() = booleanFlag("server_notifications_enabled")

    /// Master gate for the per-post save COUNT shown next to the bookmark.
    /// When false the bookmark renders exactly as before (no number); the
    /// server-side saveCount is maintained regardless, so flipping this on
    /// reveals the already-accumulated counts with no rebuild. Keep OFF until
    /// the backend is deployed + backfilled and all clients have shipped.
    val saveCountEnabled: Boolean
        get() = booleanFlag("save_count_enabled")

    val fullPlayerSaveButtonEnabled: Boolean
        get() = booleanFlag("full_player_save_button_enabled")

    val saveCapEnforced: Boolean
        get() = booleanFlag("save_cap_enforced")

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
        get() = booleanFlag("favorite_people_cap_enforced")

    val favoritePeopleCapLimit: Int
        get() {
            val v = remoteConfig.getLong("favorite_people_cap_limit").toInt()
            return if (v > 0) v else 3
        }

    val soundcloudEnabled: Boolean
        get() = booleanFlag("soundcloud_enabled")

    /**
     * Client gate for Bandcamp catalog search. Default ON (launched).
     * Tester UIDs stay on if RC is killed. Existing `bc:` posts still render.
     */
    val bandcampEnabled: Boolean
        get() = isBandcampEnabled(
            viewerUid = auth.currentUser?.uid,
            viewerUsername = auth.currentUser?.displayName,
        )

    fun isBandcampEnabled(viewerUid: String?, viewerUsername: String? = null): Boolean {
        debugOverride("bandcamp_enabled")?.let { return it }
        if (flagWithDefault("bandcamp_enabled", true)) return true
        if (viewerUid != null && viewerUid in BANDCAMP_TESTER_UIDS) return true
        val name = viewerUsername?.trim()?.lowercase().orEmpty()
        return name in BANDCAMP_TESTER_USERNAMES
    }

    /// Master gate for the TIDAL music-service integration (onboarding + settings
    /// service picker). Keep OFF until web + iOS + Android all ship — otherwise a
    /// user could pick TIDAL on one client with no support on another. Mirrors
    /// iOS/web `tidal_enabled`.
    val tidalEnabled: Boolean
        get() = flagWithDefault("tidal_enabled", true)

    /// Gate for streaming full TIDAL tracks in-app instead of 30s preview +
    /// link-out. Nested under `tidalEnabled`: both must be true. Mirrors iOS/web
    /// `tidal_full_playback_enabled`. Defaults false until TIDAL grants streaming.
    val tidalFullPlaybackEnabled: Boolean
        get() = flagWithDefault("tidal_full_playback_enabled", false)

    /// Master gate for the YouTube Music link-out service (onboarding + settings
    /// picker + post link-out). Link-out only: playback stays on the preview and
    /// playlist export falls back to Spotify, exactly like Deezer. Defaults to
    /// FALSE so a build that ships before launch shows no change to any user until
    /// the key is flipped on in Remote Config. Mirrors iOS/web `youtube_music_enabled`.
    val audiomackStreamingEnabled: Boolean get() = flagWithDefault("audiomack_streaming_enabled", false)
    val mapEnabled: Boolean get() = flagWithDefault("map_enabled", false)
    val artistMerchEnabled: Boolean get() = flagWithDefault("artist_merch_enabled", false)
    val trophyCaseDisabled: Boolean get() = flagWithDefault("trophy_case_disabled", false)
    val profileCollectionEnabled: Boolean get() = flagWithDefault("profile_collection_enabled", false)
    val profileHeaderStyleEnabled: Boolean get() = flagWithDefault("profile_header_style_enabled", false)

    val youtubeMusicIntegrationEnabled: Boolean
        get() = flagWithDefault("youtube_music_integration_enabled", false)

    val youtubeMusicEnabled: Boolean
        get() = flagWithDefault("youtube_music_enabled", false)

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
        get() = booleanFlag("new_release_filter_club_only")

    val stylePack1Enabled: Boolean
        get() = booleanFlag("style_pack_1_enabled")

    /// Gate for who may pick the staff-only "Corus" profile flair. Default false
    /// (today's behavior) restricts the picker option to staff, plus existing
    /// holders who keep seeing it so their selection isn't blanked during the
    /// phase-out; flipping to true reopens it to everyone. Defaults false so the
    /// restrictive state is the fallback before the first RC fetch. Mirrors
    /// web/iOS `corus_flair_open`. Display/rendering of the flair is unaffected.
    val corusFlairOpen: Boolean
        get() = booleanFlag("corus_flair_open")

    /// Gate for the "Trending" feed mode — the ranked `getForYouFeed` callable
    /// scoped to the whole app's most-engaged posts (not just your follows).
    /// Shares the `trending_feed_enabled` RC key with iOS.
    private var newTabResolvedUid: String? = null
    val feedNewTabEnabled: Boolean
        get() = debugOverride("feed_new_tab_enabled") ?: (auth.currentUser?.uid != null &&
            (presentedFeedValue("feed_new_tab_enabled")?.toBoolean()
                ?: (newTabResolvedUid == auth.currentUser?.uid && remoteConfig.getBoolean("feed_new_tab_enabled"))))

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

    /// When false (default): free users hit the Club paywall on Taste Matches.
    /// When true: preview (<8 posts) + 7-day trial (≥8 posts) with banner.
    /// Shares `taste_matches_free_trial` with iOS/web; server enforces too.
    val tasteMatchesFreeTrial: Boolean
        get() = feedFlag("taste_matches_free_trial")

    /// Tab-row + swipe feed-mode switcher under the wordmark. Launched on;
    /// console can still kill-switch. Shares `feed_mode_tabs_enabled` with iOS/web.
    val feedModeTabsEnabled: Boolean
        get() = feedFlag("feed_mode_tabs_enabled")

    /// Gate for the artist / album / director destination pages: search rows,
    /// tappable artist+director names, and the pages themselves. Shares the
    /// `artist_pages_enabled` RC key with iOS/web so one console flip reverts
    /// every client. Flag off = the app behaves byte-identically to today.
    /// Uses the init-race-safe feedFlag path (cached across launches) so the
    /// gated search tabs/rows render correctly from the first frame.
    val artistPagesEnabled: Boolean
        get() = feedFlag("artist_pages_enabled")

    /** Calendar remains independently gated while provider review is pending. */
    val concertCalendarEnabled: Boolean
        get() = concertsEnabled && feedFlag("concert_calendar_enabled")

    /** Shared concert switch. Remote Config scopes the first public Android
     *  rollout to the minimum supported app version. */
    val concertsEnabled: Boolean
        get() = feedFlag("concerts_enabled")

    /** Remote Config controls the Android release audience (1.5.9+). */
    val giftsEnabledForCurrentUser: Boolean
        get() = feedFlag("gifts_enabled") && auth.currentUser != null

    /** The backend accepts all accounts; clients control release visibility. */
    fun canSendGiftTo(recipientId: String): Boolean {
        val senderId = auth.currentUser?.uid ?: return false
        return giftsEnabledForCurrentUser && recipientId.isNotBlank() && senderId != recipientId
    }

    /** Option B gate for pre-release album destination pages. OFF = Option A only. */
    val prereleaseAlbumPagesEnabled: Boolean
        get() = feedFlag("prerelease_album_pages_enabled")

    /// Send-side gate for sharing an artist / album / director (the "..." Share
    /// menu on those destination pages + the Artist/Album/Director items in the
    /// DM composer "+" menu). Launch-dark: receiving/rendering those DMs is always
    /// on in an updated client; flip TRUE once enough clients can render them.
    /// Shares `entity_share_enabled` with iOS/web. Init-race-safe feedFlag path.
    val entityShareEnabled: Boolean
        get() = feedFlag("entity_share_enabled")

    /// PROTOTYPE gate for the immersive artist-page header: a full-bleed hero
    /// image with a frosted floating top bar that collapses into a solid title
    /// bar on scroll, in place of the solid white TopAppBar. Purely cosmetic,
    /// one screen. DEBUG builds default it ON so the look can be evaluated on a
    /// device without any Remote Config change; release reads the
    /// `immersive_artist_header_enabled` RC key, now defaulted ON (in-code default
    /// true + RC value true, set 2026-07-24). A debug build can still force either
    /// state via the corus_dev_flags override (see [commentControlsOnPosts]).
    val immersiveArtistHeaderEnabled: Boolean
        get() {
            if (BuildConfig.DEBUG) {
                return debugOverride("immersive_artist_header_enabled") ?: true
            }
            return feedFlag("immersive_artist_header_enabled")
        }

    /// Send-side gate for the unified comment attach picker: the comment
    /// composer's "+" menu reads GIF / Music / Film, where Music searches songs,
    /// artists and albums and Film searches films and directors. OFF = today's
    /// GIF / Song / Film menu, byte-identical. Launch-dark: receiving/rendering
    /// artist/album/director comment attachments is always on in an updated
    /// client; flip TRUE once enough clients can render them. Shares
    /// `comment_entity_attachments_enabled` with iOS/web. Init-race-safe feedFlag path.
    val commentEntityAttachmentsEnabled: Boolean
        get() = feedFlag("comment_entity_attachments_enabled")

    /// Send-side gate for sharing a user's *profile* (the "Share Profile" action
    /// on a profile screen opens the in-app Corus share sheet → DM instead of the
    /// native Android share sheet). Launch-dark: receiving/rendering a
    /// shared-profile DM is always on in an updated client; flip TRUE once enough
    /// clients can render them. Shares `profile_share_enabled` with iOS/web.
    val profileShareEnabled: Boolean
        get() = feedFlag("profile_share_enabled")

    /** Preview the vertical Instagram Story card in the own-profile share
     * sheet. OFF preserves the existing horizontal OG-link preview exactly. */
    val profileSharingV2: Boolean
        get() = feedFlag("profile_sharing_v2")

    /// Unified search: blended zero-state discovery feed + All/Users/Music/
    /// Film/Hashtags filter chips instead of the pre-segmented tabs. Shares
    /// the key with web (already released there). Uses the init-race-safe
    /// feedFlag path so the tabless layout renders correctly from the first
    /// frame on cold start.
    val unifiedSearchEnabled: Boolean
        get() = feedFlag("unified_search_enabled")

    /// Segmented Search idle browse (Users / Music / Film / Hashtags tabs +
    /// swipe). Typed query still uses unified All + filter chips. Launched
    /// on; console can still kill-switch. Shares `segmented_search_enabled`
    /// with iOS/web.
    val segmentedSearchEnabled: Boolean
        get() = feedFlag("segmented_search_enabled")

    /// Search zero-state rail of admin-curated artists who use Corus.
    /// Shares `artists_on_corus_section_enabled` with iOS/web.
    val artistsOnCorusSectionEnabled: Boolean
        get() = feedFlag("artists_on_corus_section_enabled")

    /// Search zero-state rail: trending artists (below Artists and Labels). Shares
    /// `trending_artists_section_enabled` with iOS/web.
    val trendingArtistsSectionEnabled: Boolean
        get() = feedFlag("trending_artists_section_enabled")
    val trendingSongsPreviewContextEnabled: Boolean
        get() = flagWithDefault("trending_songs_preview_context_enabled", true)

    /// Profile → artist-page card ("View artist page" + optional taste-match
    /// row). Default OFF. @gabe is hardcoded on in [isProfileArtistLinkEnabled]
    /// so the owner can evaluate without an RC condition. Shares
    /// `profile_artist_link_enabled` with iOS.
    val profileArtistLinkEnabled: Boolean
        get() = feedFlag("profile_artist_link_enabled")

    fun isProfileArtistLinkEnabled(viewerUsername: String?): Boolean =
        (debugOverride("profile_artist_link_enabled")?.let { it && artistPagesEnabled }) ?: fm.corus.android.domain.ProfileArtistLinkGate.isEnabled(
            flag = profileArtistLinkEnabled,
            artistPagesEnabled = artistPagesEnabled,
            viewerUsername = viewerUsername,
        )

    /// Unified compose picker: one search field over songs AND films with
    /// All/Songs/Films chips and a blended zero state (recently saved +
    /// trending, last-posted medium leading) instead of the Songs/Films
    /// segmented toggle. OFF = today's picker, byte-identical. Shares
    /// `compose_unified_search_enabled` with iOS. Uses the init-race-safe
    /// feedFlag path: the picker is the first thing compose renders, so the
    /// layout must be right on its very first frame after a cold launch.
    val composeUnifiedSearchEnabled: Boolean
        get() = feedFlag("compose_unified_search_enabled")

    /// Post-success sheet listing other people who already posted the same
    /// song/film, with Follow. OFF = today's compose dismiss, byte-identical.
    /// DEBUG builds force it ON so a fresh local signup can see the sheet
    /// without an RC allowlist. Release reads the console key (default false).
    val postSuccessOthersEnabled: Boolean
        get() = debugOverride("post_success_others_enabled")
            ?: (BuildConfig.DEBUG || booleanFlag("post_success_others_enabled"))

    /// Instagram-style Activity filter chips. Launched ON; RC false hides
    /// them with no app update. Off = today's unfiltered list.
    val notificationFiltersEnabled: Boolean
        get() = feedFlag("notification_filters_enabled")

    /// Master gate for Books as a third medium. Shares `books_enabled` with iOS.
    val booksEnabled: Boolean
        get() = feedFlag("books_enabled")

    /// Volume gate for showing the chips (paired with minTypes). Default 8.
    val notificationFiltersMinCount: Int
        get() {
            val v = remoteConfig.getLong("notification_filters_min_count").toInt()
            return if (v > 0) v else 8
        }

    /// Distinct-type gate for showing the chips (paired with minCount). Default 3.
    val notificationFiltersMinTypes: Int
        get() {
            val v = remoteConfig.getLong("notification_filters_min_types").toInt()
            return if (v > 0) v else 3
        }

    /// Gate for the taste-match onboarding flow (music-service step second,
    /// taste quiz → venn interstitial → taste-matched suggestions → head-start
    /// posts). OFF = the existing three-step social setup, byte-identical.
    /// Shares `onboarding_taste_match_enabled` with web (already live there);
    /// the server template default is true with an Android app-id condition
    /// forcing FALSE, so this stays dark here until that condition flips.
    /// Init-race-safe feedFlag path: a fresh signup reaches SocialSetupFlow
    /// right after install, and the flow must branch correctly on its very
    /// first frame.
    val onboardingClubOfferEnabled: Boolean
        get() = feedFlag("onboarding_club_offer_enabled")

    private val clubOfferState get() = context.getSharedPreferences("onboarding_club_offer", Context.MODE_PRIVATE)

    fun claimThirdPostOffer(total: Int): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val key = "third_post.$uid"
        if (!onboardingClubOfferEnabled || total < 3 || clubOfferState.getBoolean(key, false)) return false
        clubOfferState.edit().putBoolean(key, true).apply()
        return true
    }

    /// Same visual experiment as iOS; local Debug builds can inspect it.
    val revisedOnboardingTasteMatches: Boolean
        get() = debugOverride("Revised_onboarding_taste_matches")
            ?: (BuildConfig.DEBUG || feedFlag("Revised_onboarding_taste_matches"))

    val onboardingMinimumFollows: Int
        get() = feedString("onboarding_minimum_follows").trim().toIntOrNull()?.coerceAtLeast(0) ?: 0

    val onboardingTasteMatchEnabled: Boolean
        get() = feedFlag("onboarding_taste_match_enabled")

    /// One-time "feed switch hint" discovery coachmark (the bubble under the
    /// Corus logo teaching that the logo switches feed modes). Master gate;
    /// ships dark. Shares `feed_switch_hint_enabled` with iOS/web. Uses the
    /// init-race-safe feedFlag path so a fresh signup doesn't briefly read the
    /// wrong value. Default min session is 1 (first feed visit).
    val feedSwitchHintEnabled: Boolean
        get() = feedFlag("feed_switch_hint_enabled")

    /// App opens required before the hint can appear. Mirrors
    /// `feed_switch_hint_min_session`. Default 1 so it can show on the first
    /// post-signup feed visit.
    val feedSwitchHintMinSession: Int
        get() {
            val v = remoteConfig.getLong("feed_switch_hint_min_session").toInt()
            return if (v > 0) v else 1
        }

    /// Lifetime cap on how many times the hint is shown. Mirrors
    /// `feed_switch_hint_max_impressions`.
    val feedSwitchHintMaxImpressions: Int
        get() {
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
        get() = booleanFlag("favorites_push_enabled")

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
            debugOverride("comment_controls_on_posts")?.let { return it }
            return booleanFlag("comment_controls_on_posts")
        }

    /// Master gate for the "who reposted this" list: long-press the repost count
    /// on a post to open a sheet of the people who reposted it, each row tapping
    /// through to that person's repost. OFF = the repost button only opens
    /// compose, byte-identical to today; the getReposters read is unused. Shares
    /// `reposters_list_enabled` with iOS/web. Not first-frame-critical (the
    /// affordance only matters once feed content is on screen), so a plain
    /// getBoolean is fine.
    val repostersListEnabled: Boolean
        get() = booleanFlag("reposters_list_enabled")

    val feedEnergyFilterEnabled: Boolean
        get() = booleanFlag("feed_energy_filter_enabled")

    val feedDecadeFilterEnabled: Boolean
        get() = feedFlag("feed_decade_filter_enabled")

    /// Master gate for the "Add Saved Songs to Library" Spotify Web API opt-in:
    /// when the user turns the settings toggle on (granting `user-library-modify`
    /// via a separate OAuth + PKCE consent — NOT the App Remote token above), every
    /// subsequent song save also PUTs the track into the user's Spotify "Liked
    /// Songs" library. OFF = zero behavior change. Default false; server template
    /// is already live (per-uid test condition, no platform restriction), so no
    /// template change is needed here. Shares `spotify_library_save_enabled` with
    /// iOS/web.
    val spotifyLibrarySaveEnabled: Boolean
        get() = flagWithDefault("spotify_library_save_enabled", false)

    /// Spotify first-time playback experiment for new users. In-app default is
    /// `b` (Always Full + first-play chooser) so a late or failed fetch still
    /// prompts. Live RC experiment still assigns `a` / `b`; `off` remains a
    /// kill switch. Clients never randomize.
    val spotifyFtueVariant: String
        get() {
            if (BuildConfig.DEBUG) {
                debugSpotifyFtueVariantOverride
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it == "a" || it == "b" || it == "off" }
                    ?.let { return it }
            }
            val raw = remoteConfig.getString(SPOTIFY_FTUE_VARIANT_KEY).trim().lowercase()
            return if (raw == "a" || raw == "b") raw else "off"
        }

    /// Passwordless email OTP login. Native/web auth UI no longer gates the
    /// Continue with Email button on this flag (always shown); server
    /// `email_otp_auth_enabled` remains the kill switch. Kept for tooling /
    /// any remaining readers.
    val emailOtpAuthEnabled: Boolean
        get() = feedFlag("email_otp_auth_enabled")


    private fun booleanFlag(key: String): Boolean =
        debugOverride(key) ?: remoteConfig.getBoolean(key)

    fun debugOverride(key: String): Boolean? {
        if (!BuildConfig.DEBUG || !devPrefs.contains(key)) return null
        return devPrefs.getBoolean(key, false)
    }

    private var debugServerValues = emptyMap<String, String>()
    private val debugForYouKey = "for_you_prototype_enabled"
    private val debugOverrideKeys: Set<String>
        get() = registeredDebugFeatureFlags.map { it.key }.toSet() + debugForYouKey
    private var debugServerUid: String? = null
    var debugServerCatalogMessage: String? = null
        private set

    class DebugFeatureFlag(
        val key: String,
        val rawValue: String? = null,
        val namespace: String = "client",
        val allowsLocalOverride: Boolean = true,
        val effectiveValue: () -> Boolean,
    ) {
        val rowId: String get() = "$namespace:$key"
        val title: String get() = if (key == "for_you_prototype_enabled") "Your Mix / For You" else key.replace('_', ' ')
    }

    private val registeredDebugFeatureFlags: List<DebugFeatureFlag>
        get() = if (!BuildConfig.DEBUG) emptyList() else listOf(
            DebugFeatureFlag("audiomack_streaming_enabled") { audiomackStreamingEnabled },
            DebugFeatureFlag("map_enabled") { mapEnabled },
            DebugFeatureFlag("artist_pages_enabled") { artistPagesEnabled },
            DebugFeatureFlag("artists_on_corus_section_enabled") { artistsOnCorusSectionEnabled },
            DebugFeatureFlag("bandcamp_enabled") { bandcampEnabled },
            DebugFeatureFlag("books_enabled") { booksEnabled },
            DebugFeatureFlag("comment_controls_on_posts") { commentControlsOnPosts },
            DebugFeatureFlag("comment_entity_attachments_enabled") { commentEntityAttachmentsEnabled },
            DebugFeatureFlag("compose_unified_search_enabled") { composeUnifiedSearchEnabled },
            DebugFeatureFlag("concert_calendar_enabled") { concertCalendarEnabled },
            DebugFeatureFlag("concerts_enabled") { concertsEnabled },
            DebugFeatureFlag("corus_club_enabled") { corusClubEnabled },
            DebugFeatureFlag("corus_flair_open") { corusFlairOpen },
            DebugFeatureFlag("daily_post_limit_enabled") { dailyPostLimitEnabled },
            DebugFeatureFlag("deezer_enabled") { deezerEnabled },
            DebugFeatureFlag("dm_video_enabled") { dmVideoEnabled },
            DebugFeatureFlag("email_otp_auth_enabled") { emailOtpAuthEnabled },
            DebugFeatureFlag("entity_share_enabled") { entityShareEnabled },
            DebugFeatureFlag("favorite_people_cap_enforced") { favoritePeopleCapEnforced },
            DebugFeatureFlag("favorites_enabled") { favoritesEnabled },
            DebugFeatureFlag("favorites_push_enabled") { favoritesPushEnabled },
            DebugFeatureFlag("feed_decade_filter_enabled") { feedDecadeFilterEnabled },
            DebugFeatureFlag("feed_energy_filter_enabled") { feedEnergyFilterEnabled },
            DebugFeatureFlag("feed_mode_tabs_enabled") { feedModeTabsEnabled },
            DebugFeatureFlag("feed_new_tab_enabled") { feedNewTabEnabled },
            DebugFeatureFlag("feed_switch_hint_enabled") { feedSwitchHintEnabled },
            DebugFeatureFlag("following_denorm_reads_enabled") { followingDenormReadsEnabled },
            DebugFeatureFlag("full_player_save_button_enabled") { fullPlayerSaveButtonEnabled },
            DebugFeatureFlag("gif_support") { gifSupport },
            DebugFeatureFlag("gifts_enabled") { giftsEnabledForCurrentUser },
            DebugFeatureFlag("group_messaging_enabled") { groupMessagingEnabled },
            DebugFeatureFlag("immersive_artist_header_enabled") { immersiveArtistHeaderEnabled },
            DebugFeatureFlag("instagram_share_enabled") { instagramShareEnabled },
            DebugFeatureFlag("maintenance_mode") { maintenanceMode },
            DebugFeatureFlag("movie_mode") { movieModeEnabled },
            DebugFeatureFlag("new_release_filter_club_only") { newReleaseFilterClubOnly },
            DebugFeatureFlag("notification_filters_enabled") { notificationFiltersEnabled },
            DebugFeatureFlag("onboarding_club_offer_enabled") { onboardingClubOfferEnabled },
            DebugFeatureFlag("onboarding_taste_match_enabled") { onboardingTasteMatchEnabled },
            DebugFeatureFlag("Revised_onboarding_taste_matches") { revisedOnboardingTasteMatches },
            DebugFeatureFlag("paywall_default_yearly") { paywallDefaultYearly },
            DebugFeatureFlag("play_milestone_enabled") { playMilestoneEnabled },
            DebugFeatureFlag("post_success_others_enabled") { postSuccessOthersEnabled },
            DebugFeatureFlag("post_to_instagram_v2") { postToInstagramV2 },
            DebugFeatureFlag("prerelease_album_pages_enabled") { prereleaseAlbumPagesEnabled },
            DebugFeatureFlag("profile_artist_link_enabled") { profileArtistLinkEnabled },
            DebugFeatureFlag("profile_share_enabled") { profileShareEnabled },
            DebugFeatureFlag("profile_sharing_v2") { profileSharingV2 },
            DebugFeatureFlag("reposters_list_enabled") { repostersListEnabled },
            DebugFeatureFlag("review_prompt_enabled") { reviewPromptEnabled },
            DebugFeatureFlag("profile_collection_enabled") { profileCollectionEnabled },
            DebugFeatureFlag("profile_header_style_enabled") { profileHeaderStyleEnabled },
            DebugFeatureFlag("save_cap_enforced") { saveCapEnforced },
            DebugFeatureFlag("save_count_enabled") { saveCountEnabled },
            DebugFeatureFlag("segmented_search_enabled") { segmentedSearchEnabled },
            DebugFeatureFlag("server_notifications_enabled") { serverNotificationsEnabled },
            DebugFeatureFlag("soundcloud_enabled") { soundcloudEnabled },
            DebugFeatureFlag("spotify_library_save_enabled") { spotifyLibrarySaveEnabled },
            DebugFeatureFlag("style_pack_1_enabled") { stylePack1Enabled },
            DebugFeatureFlag("taste_matches_enabled") { tasteMatchesEnabled },
            DebugFeatureFlag("taste_matches_free_trial") { tasteMatchesFreeTrial },
            DebugFeatureFlag("taste_matches_tester") { tasteMatchesTester },
            DebugFeatureFlag("tidal_enabled") { tidalEnabled },
            DebugFeatureFlag("tidal_full_playback_enabled") { tidalFullPlaybackEnabled },
            DebugFeatureFlag("trending_artists_section_enabled") { trendingArtistsSectionEnabled },
            DebugFeatureFlag("trending_feed_enabled") { trendingFeedEnabled },
            DebugFeatureFlag("trending_songs_preview_context_enabled") { trendingSongsPreviewContextEnabled },
            DebugFeatureFlag("typing_indicators_enabled") { typingIndicatorsEnabled },
            DebugFeatureFlag("unified_search_enabled") { unifiedSearchEnabled },
            DebugFeatureFlag("vinyl_flip_enabled") { vinylFlipEnabled },
            DebugFeatureFlag("youtube_music_enabled") { youtubeMusicEnabled },
            DebugFeatureFlag("youtube_music_integration_enabled") { youtubeMusicIntegrationEnabled },
        )

    val debugFeatureFlags: List<DebugFeatureFlag>
        get() {
            if (!BuildConfig.DEBUG) return emptyList()
            val registered = registeredDebugFeatureFlags.associateBy { it.key }
            val client = remoteConfig.all
            val flags = (registered.keys + client.keys).map { key ->
                registered[key] ?: DebugFeatureFlag(key, rawValue = client[key]?.asString(),
                    allowsLocalOverride = false) { remoteConfig.getBoolean(key) }
            }
            val server = if (debugServerUid == auth.currentUser?.uid) debugServerValues else emptyMap()
            val serverKeys = server.keys + "for_you_prototype_enabled"
            return (flags + serverKeys.map { key ->
                DebugFeatureFlag(key, rawValue = server[key], namespace = "server", allowsLocalOverride = key == debugForYouKey) {
                    (if (key == debugForYouKey) debugOverride(key) else null) ?: (server[key] == "true")
                }
            }).sortedWith(compareBy<DebugFeatureFlag> { it.key != debugForYouKey }.thenBy { it.rowId.lowercase() })
        }

    val debugOverrideCount: Int
        get() = if (BuildConfig.DEBUG) debugOverrideKeys.count { debugOverride(it) != null } else 0

    fun debugRemoteValue(key: String): Boolean = remoteConfig.getBoolean(key)

    fun debugSource(key: String): String = when (remoteConfig.getValue(key).source) {
        FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> "Remote Config"
        FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> "In-app default"
        else -> "Not fetched"
    }

    fun setDebugOverride(key: String, value: Boolean?) {
        if (!BuildConfig.DEBUG || key !in debugOverrideKeys) return
        val edit = devPrefs.edit()
        if (value == null) edit.remove(key) else edit.putBoolean(key, value)
        edit.apply()
        _revision.value += 1
    }

    fun resetDebugOverrides() {
        if (!BuildConfig.DEBUG) return
        val edit = devPrefs.edit()
        debugOverrideKeys.forEach { edit.remove(it) }
        edit.apply()
        _revision.value += 1
    }

    suspend fun refreshDebugFeatureFlags() {
        if (!BuildConfig.DEBUG) return
        fetchAndActivate(forceFresh = true)
        val uid = auth.currentUser?.uid
        if (uid == null) {
            debugServerValues = emptyMap()
            debugServerUid = null
            debugServerCatalogMessage = "Sign in to check backend flags."
            _revision.value += 1
            return
        }
        var values = emptyMap<String, String>()
        var message: String? = null
        val functions = FirebaseFunctions.getInstance("us-central1")
        try {
            val callable = functions.getHttpsCallable("getDebugFeatureFlags")
            callable.setTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            val data = callable.call().await().getData() as? Map<*, *>
            val flags = data?.get("flags") as? Map<*, *> ?: error("Invalid flag catalog")
            check(flags.keys.all { it is String } && flags.values.all { it is String })
            values = flags.entries.associate { it.key as String to it.value as String }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            message = "Backend catalog unavailable. Showing Your Mix access only."
            try {
                val callable = functions.getHttpsCallable("getForYouPrototypeAccess")
                callable.setTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                val data = callable.call().await().getData() as? Map<*, *>
                (data?.get("enabled") as? Boolean)?.let { values = mapOf("for_you_prototype_enabled" to it.toString()) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "Couldn’t refresh backend flags. Try again."
            }
        }
        if (auth.currentUser?.uid != uid) return
        debugServerValues = values
        debugServerUid = uid
        debugServerCatalogMessage = message
        _revision.value += 1
    }

    data class FeedTabPresentation(
        val uid: String?,
        val hasPresentation: Boolean,
        val values: Map<String, String> = emptyMap(),
        val generation: Int = 0,
    )

    private val tabBooleanKeys = listOf("feed_new_tab_enabled", "feed_mode_tabs_enabled",
        "trending_feed_enabled", "favorites_enabled", "taste_matches_enabled", "taste_matches_tester")
    private val tabStringKeys = listOf("feed_mode_order", "for_you_default_mode")
    private val legacyTabBooleanKeys = setOf("feed_mode_tabs_enabled", "trending_feed_enabled",
        "favorites_enabled", "taste_matches_enabled")
    private val tabPrefs = context.getSharedPreferences("corus_feed_tab_presentation", Context.MODE_PRIVATE)
    private val tabLock = Any()
    private val _feedTabPresentation = MutableStateFlow(cachedTabPresentation(auth.currentUser?.uid))
    val feedTabPresentation = _feedTabPresentation.asStateFlow()
    val isResolvingFeedTabs: Boolean
        get() = auth.currentUser?.uid?.let {
            _feedTabPresentation.value.uid != it || !_feedTabPresentation.value.hasPresentation
        } ?: false

    init {
        auth.addAuthStateListener { beginFeedTabSession(it.currentUser?.uid) }
    }

    private fun cachedTabPresentation(uid: String?): FeedTabPresentation = FeedTabPresentation(
        uid = uid,
        hasPresentation = uid == null,
        values = if (uid == null) emptyMap() else (tabBooleanKeys + tabStringKeys).associateWith { key ->
            // On upgrade, preserve ordinary released tabs from the existing cache.
            // Pilot and tester access can only come from this account's confirmed cache.
            tabPrefs.getString("$uid:$key", null) ?: when {
                key in legacyTabBooleanKeys -> flagCache.getBoolean(key, DEFAULTS[key] as? Boolean ?: false).toString()
                key in tabStringKeys -> flagCache.getString(key, DEFAULTS[key]?.toString() ?: "") ?: ""
                else -> DEFAULTS[key]?.toString() ?: ""
            }
        },
    )

    private fun beginFeedTabSession(uid: String?) = synchronized(tabLock) {
        if (_feedTabPresentation.value.uid != uid) {
            _feedTabPresentation.value = cachedTabPresentation(uid).copy(generation = _feedTabPresentation.value.generation + 1)
        }
    }

    private fun presentedFeedValue(key: String): String? = _feedTabPresentation.value.let {
        if (it.hasPresentation && it.uid != null && it.uid == auth.currentUser?.uid) it.values[key] else null
    }

    /** The feed waits here, independently of the background fetch. Late results warm the next launch. */
    suspend fun awaitFeedTabPresentation() {
        val uid = auth.currentUser?.uid ?: return
        beginFeedTabSession(uid)
        val generation = _feedTabPresentation.value.generation
        withTimeoutOrNull(1_000) {
            feedTabPresentation.first { it.generation != generation || it.hasPresentation }
        }
        finishFeedTabPresentation(uid, generation)
    }

    private fun finishFeedTabPresentation(uid: String?, generation: Int, values: Map<String, String>? = null) = synchronized(tabLock) {
        val current = _feedTabPresentation.value
        if (current.uid == uid && current.generation == generation && auth.currentUser?.uid == uid && !current.hasPresentation) {
            _feedTabPresentation.value = current.copy(hasPresentation = true, values = values ?: current.values)
            _revision.value += 1
        }
    }

    private fun cacheFeedTabPresentation(uid: String): Map<String, String> {
        val values = tabBooleanKeys.associateWith { remoteConfig.getBoolean(it).toString() } +
            tabStringKeys.associateWith { remoteConfig.getString(it) }
        tabPrefs.edit().also { edit -> values.forEach { (key, value) -> edit.putString("$uid:$key", value) } }.apply()
        return values
    }

    // Tracks the UID last pushed as the `user_id` signal so we can tell when it
    // changes (login / account switch) and force a fresh fetch. Null-vs-unset is
    // distinguished by [hasAppliedUserSignal] so the first apply always counts.
    @Volatile private var lastAppliedUserSignal: String? = null
    @Volatile private var hasAppliedUserSignal = false
    private val fetchMutex = Mutex()
    private val initialFetchGate = CompletableDeferred<Unit>()

    /// Pushes the current user's UID into Remote Config as a custom signal so
    /// per-user targeting conditions (e.g. `app.customSignal['user_id']`) can
    /// resolve. Mirrors iOS (RemoteConfigService.setCurrentUserSignal). Safe to
    /// call repeatedly — last write wins. Must run *before* a fetch so the
    /// signal is evaluated against the returned values. Passing null clears the
    /// signal, matching the desired behavior on sign-out. Returns true when the
    /// applied UID differs from the previously applied one (login / switch),
    /// meaning any cached config was evaluated for a different user.
    suspend fun setCurrentUserSignal(uid: String?): Boolean {
        if (newTabResolvedUid != uid) { newTabResolvedUid = null; _revision.value += 1 }
        if (BuildConfig.DEBUG && debugServerUid != uid) {
            debugServerValues = emptyMap()
            debugServerUid = null
            debugServerCatalogMessage = null
            _revision.value += 1
        }
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

    suspend fun fetchAndActivate(forceFresh: Boolean = false) {
        fetchMutex.withLock {
            val requestedUid = auth.currentUser?.uid
            beginFeedTabSession(requestedUid)
            val generation = _feedTabPresentation.value.generation
            try {
                // Make sure the user-targeting custom signal is in place before
                // fetching so conditional values resolve correctly on the very
                // first response. Mirrors iOS.
                val signalChanged = setCurrentUserSignal(requestedUid)
                // When the signed-in user changes, the cached config was fetched and
                // evaluated against a *different* user_id signal. The normal 1h
                // throttle would serve that stale per-user result for up to an hour,
                // so per-user flags (e.g. favorites_enabled) wouldn't light up until
                // then. Bypass the throttle on a signal change so this user's
                // conditions resolve on this fetch. DEBUG builds mirror iOS (always 0).
                val minIntervalSeconds = when {
                    BuildConfig.DEBUG -> 0L
                    forceFresh || signalChanged -> 0L
                    else -> 3600L
                }
                val settings = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(minIntervalSeconds)
                    .build()
                remoteConfig.setConfigSettingsAsync(settings).await()
                remoteConfig.setDefaultsAsync(DEFAULTS).await()
                val activated = if (forceFresh || signalChanged || BuildConfig.DEBUG) {
                    remoteConfig.fetch().await()
                    remoteConfig.activate().await()
                } else {
                    remoteConfig.fetchAndActivate().await()
                }
                if (auth.currentUser?.uid != requestedUid || _feedTabPresentation.value.generation != generation) return@withLock
                newTabResolvedUid = requestedUid
                cacheFeedFlags()
                val presentation = requestedUid?.let { cacheFeedTabPresentation(it) }
                finishFeedTabPresentation(requestedUid, generation, presentation)
                _revision.value += 1
                logValues(activated)
            } catch (e: Exception) {
                Log.w("RemoteConfig", "fetchAndActivate failed", e)
                finishFeedTabPresentation(requestedUid, generation)
            } finally {
                if (!initialFetchGate.isCompleted) {
                    initialFetchGate.complete(Unit)
                }
                _initialFetchComplete.value = true
            }
        }
    }

    /** Wait until the first Remote Config fetch this process finishes (or fails). */
    suspend fun awaitInitialFetch() {
        if (!initialFetchGate.isCompleted) {
            fetchAndActivate(forceFresh = true)
        }
        initialFetchGate.await()
    }

    /// Persist the feed flags so the next cold launch renders the chevron /
    /// resolves the default mode correctly from the first frame (see feedFlag).
    /// Reads straight from Remote Config — by this point the fetch has activated.
    private fun cacheFeedFlags() {
        flagCache.edit()
            .putString("onboarding_minimum_follows", remoteConfig.getString("onboarding_minimum_follows"))
            .putBoolean("concerts_enabled", remoteConfig.getBoolean("concerts_enabled"))
            .putBoolean("concert_calendar_enabled", remoteConfig.getBoolean("concert_calendar_enabled"))
            .putBoolean("trending_feed_enabled", remoteConfig.getBoolean("trending_feed_enabled"))
            .putBoolean("favorites_enabled", remoteConfig.getBoolean("favorites_enabled"))
            .putBoolean("play_milestone_enabled", remoteConfig.getBoolean("play_milestone_enabled"))
            .putBoolean("artist_pages_enabled", remoteConfig.getBoolean("artist_pages_enabled"))
            .putBoolean("entity_share_enabled", remoteConfig.getBoolean("entity_share_enabled"))
            .putBoolean("comment_entity_attachments_enabled", remoteConfig.getBoolean("comment_entity_attachments_enabled"))
            .putBoolean("profile_share_enabled", remoteConfig.getBoolean("profile_share_enabled"))
            .putBoolean("profile_sharing_v2", remoteConfig.getBoolean("profile_sharing_v2"))
            .putBoolean("unified_search_enabled", remoteConfig.getBoolean("unified_search_enabled"))
            .putBoolean("segmented_search_enabled", remoteConfig.getBoolean("segmented_search_enabled"))
            .putBoolean("artists_on_corus_section_enabled", remoteConfig.getBoolean("artists_on_corus_section_enabled"))
            .putBoolean("trending_artists_section_enabled", remoteConfig.getBoolean("trending_artists_section_enabled"))
            .putBoolean("trending_songs_preview_context_enabled", remoteConfig.getBoolean("trending_songs_preview_context_enabled"))
            .putBoolean("profile_artist_link_enabled", remoteConfig.getBoolean("profile_artist_link_enabled"))
            .putBoolean("compose_unified_search_enabled", remoteConfig.getBoolean("compose_unified_search_enabled"))
            .putBoolean("notification_filters_enabled", remoteConfig.getBoolean("notification_filters_enabled"))
            .putBoolean("books_enabled", remoteConfig.getBoolean("books_enabled"))
            .putBoolean("feed_switch_hint_enabled", remoteConfig.getBoolean("feed_switch_hint_enabled"))
            .putBoolean("onboarding_club_offer_enabled", remoteConfig.getBoolean("onboarding_club_offer_enabled"))
            .putBoolean("onboarding_taste_match_enabled", remoteConfig.getBoolean("onboarding_taste_match_enabled"))
            .putBoolean("Revised_onboarding_taste_matches", remoteConfig.getBoolean("Revised_onboarding_taste_matches"))
            .putBoolean("taste_matches_enabled", remoteConfig.getBoolean("taste_matches_enabled"))
            .putBoolean("taste_matches_tester", remoteConfig.getBoolean("taste_matches_tester"))
            .putBoolean("taste_matches_free_trial", remoteConfig.getBoolean("taste_matches_free_trial"))
            .putBoolean("feed_decade_filter_enabled", remoteConfig.getBoolean("feed_decade_filter_enabled"))
            .putBoolean("email_otp_auth_enabled", remoteConfig.getBoolean("email_otp_auth_enabled"))
            .putString("feed_mode_order", remoteConfig.getString("feed_mode_order"))
            .putString("for_you_default_mode", remoteConfig.getString("for_you_default_mode"))
            .putBoolean("feed_mode_tabs_enabled", remoteConfig.getBoolean("feed_mode_tabs_enabled"))
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
                "trending_feed_enabled=${booleanFlag("trending_feed_enabled")} " +
                "favorites_enabled=${booleanFlag("favorites_enabled")} " +
                "unified_search_enabled=$unifiedSearchEnabled " +
                "segmented_search_enabled=$segmentedSearchEnabled " +
                "compose_unified_search_enabled=$composeUnifiedSearchEnabled " +
                "uid=${auth.currentUser?.uid}"
        )
    }

    companion object {
        const val SPOTIFY_FTUE_VARIANT_KEY = "spotify_ftue_variant"

        /// Device QA for a fresh signup. Set to `"a"` or `"b"`, uninstall the app,
        /// then sign up as a new user with Spotify installed. `null` reads Remote Config.
        /// Debug builds only — [spotifyFtueVariant] ignores this in release.
        val debugSpotifyFtueVariantOverride: String? = null

        /** Matches iOS `BandcampGate` and backend `BANDCAMP_SEARCH_TEST_UIDS`. */
        val BANDCAMP_TESTER_UIDS = setOf(
            "FUQZIrZR08T2Ux2vYpPzWx7B1rv1", // @gabe
            "u3UmswvOg5c2r9zYlOidJYFzqbp2", // @clifton
        )
        val BANDCAMP_TESTER_USERNAMES = setOf("gabe", "clifton")

        /// In-app Remote Config defaults. Applied locally in init() (so flag-gated
        /// UI is correct before any network fetch) and re-applied in
        /// fetchAndActivate(). Single source of truth — keep in sync with the
        /// server template and the iOS/web defaults.
        private val DEFAULTS: Map<String, Any> = mapOf(
            "onboarding_minimum_follows" to 0L,
            "concerts_enabled" to false,
            "concert_calendar_enabled" to false,
            "gifts_enabled" to false,
            "map_enabled" to false,
            "mapkit_js_token" to "",
            "artist_merch_enabled" to false,
            "trophy_case_disabled" to false,
            "profile_collection_enabled" to false,
            "profile_header_style_enabled" to false,
            "movie_mode" to true,
            "maintenance_mode" to false,
            "instagram_share_enabled" to true,
            "post_to_instagram_v2" to false,
            "corus_club_enabled" to true,
            "vinyl_flip_enabled" to true,
            "review_prompt_enabled" to true,
            "maintenance_message" to "",
            "daily_post_limit_enabled" to true,
            "paywall_default_yearly" to false,
            "gif_support" to false,
            "group_messaging_enabled" to false,
            "typing_indicators_enabled" to true,
            "dm_video_enabled" to false,
            "server_notifications_enabled" to true,
            "save_count_enabled" to true,
            "full_player_save_button_enabled" to false,
            "save_cap_enforced" to true,
            "save_cap_limit" to 20L,
            "save_cap_warning_at" to 17L,
            "favorite_people_cap_enforced" to true,
            "favorite_people_cap_limit" to 4L,
            "soundcloud_enabled" to false,
            "bandcamp_enabled" to true,
            "tidal_enabled" to true,
            "tidal_full_playback_enabled" to false,
            "youtube_music_enabled" to false,
            "deezer_enabled" to true,
            "following_denorm_reads_enabled" to true,
            "comment_controls_on_posts" to true,
            "new_release_filter_club_only" to false,
            "style_pack_1_enabled" to true,
            "corus_flair_open" to false,
            "trending_feed_enabled" to true,
            "favorites_enabled" to true,
            "favorites_push_enabled" to true,
            "play_milestone_enabled" to false,
            "taste_matches_enabled" to false,
            "for_you_default_mode" to "balanced",
            "taste_matches_tester" to false,
            "taste_matches_free_trial" to false,
            // Default FALSE in code — the server template currently sends true;
            // flipping the console key off must revert every client.
            "artist_pages_enabled" to false,
            "prerelease_album_pages_enabled" to false,
            "entity_share_enabled" to false,
            "immersive_artist_header_enabled" to true,
            "comment_entity_attachments_enabled" to false,
            "profile_share_enabled" to false,
            "profile_sharing_v2" to false,
            "unified_search_enabled" to false,
            "segmented_search_enabled" to true,
            "artists_on_corus_section_enabled" to false,
            "trending_artists_section_enabled" to false,
            "trending_songs_preview_context_enabled" to true,
            "profile_artist_link_enabled" to false,
            "compose_unified_search_enabled" to false,
            "post_success_others_enabled" to false,
            "notification_filters_enabled" to true,
            "books_enabled" to false,
            "notification_filters_min_count" to 8L,
            "notification_filters_min_types" to 3L,
            "feed_switch_hint_enabled" to false,
            // Default FALSE in code — the server-side param defaults true (web
            // is live) with an Android app-id condition forcing false; the
            // in-code default keeps the flow dark even before the first fetch.
            "onboarding_club_offer_enabled" to false,
            "onboarding_taste_match_enabled" to false,
            "Revised_onboarding_taste_matches" to false,
            "email_otp_auth_enabled" to false,
            "feed_switch_hint_min_session" to 1L,
            "feed_switch_hint_max_impressions" to 3L,
            "reposters_list_enabled" to false,
            "feed_energy_filter_enabled" to false,
            "feed_decade_filter_enabled" to false,
            "spotify_library_save_enabled" to false,
            "spotify_ftue_variant" to "b",
            "feed_mode_order" to FeedModeOrder.DEFAULT_RAW,
            "feed_mode_tabs_enabled" to true,
            "feed_new_tab_enabled" to false,
        )
    }
}
