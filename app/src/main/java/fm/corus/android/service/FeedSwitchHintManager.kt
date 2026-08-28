package fm.corus.android.service

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.local.PreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the one-time "feed switch hint" discovery coachmark — the bubble under
 * the top-of-feed Corus logo teaching users that the logo switches feed modes.
 * Shown on the first eligible feed visit (minSession default 1) so new users
 * learn the switcher exists. Zero-follow users auto-landed on Trending wait
 * until they follow someone — then the hint teaches them how to switch back.
 * Users who followed during onboarding see it immediately so they can find
 * Trending.
 *
 * Device-local by design: all state lives in a private SharedPreferences file
 * (mirrors the web feature's localStorage and iOS's UserDefaults). Gating
 * mirrors web `shouldShowHint` — see Corus-Web `lib/feed/switch-hint.ts`. Ships
 * dark behind the RC flag. Modeled on [ReviewPromptManager].
 */
@Singleton
class FeedSwitchHintManager @Inject constructor(
    @ApplicationContext context: Context,
    private val remoteConfigService: RemoteConfigService,
    private val analyticsService: AnalyticsService,
    private val preferencesDataStore: PreferencesDataStore,
) {
    private val prefs = context.getSharedPreferences("feed_switch_hint", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Per-process guards: count one session per launch, show the hint at most
    // once per launch, and never queue two reveals. Reset on a fresh process
    // (mirrors web's per-tab sessionStorage guards).
    private var sessionCounted = false
    private var shownThisSession = false
    private var revealScheduled = false

    private val _shouldShow = MutableStateFlow(false)
    val shouldShow: StateFlow<Boolean> = _shouldShow.asStateFlow()

    private var hasOpenedSwitcher: Boolean
        get() = prefs.getBoolean(KEY_OPENED, false)
        set(v) = prefs.edit { putBoolean(KEY_OPENED, v) }
    private var hasDismissedHint: Boolean
        get() = prefs.getBoolean(KEY_DISMISSED, false)
        set(v) = prefs.edit { putBoolean(KEY_DISMISSED, v) }
    private var shownCount: Int
        get() = prefs.getInt(KEY_SHOWN_COUNT, 0)
        set(v) = prefs.edit { putInt(KEY_SHOWN_COUNT, v) }
    private var sessionCount: Int
        get() = prefs.getInt(KEY_SESSION_COUNT, 0)
        set(v) = prefs.edit { putInt(KEY_SESSION_COUNT, v) }
    private var wasAutoDefaultedToTrending: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DEFAULTED, false)
        set(v) = prefs.edit { putBoolean(KEY_AUTO_DEFAULTED, v) }
    private var hasFollowedSomeone: Boolean
        get() = prefs.getBoolean(KEY_HAS_FOLLOWED, false)
        set(v) = prefs.edit { putBoolean(KEY_HAS_FOLLOWED, v) }

    /** Count this app launch as one session, once per process. */
    fun recordSession() {
        if (sessionCounted) return
        sessionCounted = true
        sessionCount += 1
    }

    /**
     * Evaluate the gate and, if eligible, reveal the hint after a short delay so
     * the feed settles first. Safe to call repeatedly — the feed calls it on
     * appear and whenever the RC flag activates (it reads false on the first
     * cold-launch frame, before the async fetch resolves).
     */
    fun evaluate() {
        // Tabs-on: never reveal, and hide a bubble that was already up
        // (e.g. the flag flipped mid-session).
        if (remoteConfigService.feedModeTabsEnabled) {
            revealScheduled = false
            _shouldShow.value = false
            return
        }
        if (_shouldShow.value || revealScheduled) return
        if (!currentlyEligible()) return
        revealScheduled = true
        scope.launch {
            delay(REVEAL_DELAY_MS)
            revealScheduled = false
            // Re-check: the user may have opened/dismissed during the delay.
            if (!currentlyEligible() || _shouldShow.value) return@launch
            shownThisSession = true
            shownCount += 1
            analyticsService.logFeedSwitchHintShown()
            _shouldShow.value = true
        }
    }

    /**
     * After onboarding: land zero-follow users on Trending without treating that
     * as "explored other feeds", and withhold the switcher coachmark until they
     * follow someone. When they already followed during onboarding, mark that
     * so the hint can show immediately (they need it to find Trending).
     * Writes feed mode silently — does NOT call [noteSwitcherUsed]. The sync
     * seed is updated immediately so the feed's first frame resolves to Trending.
     */
    fun applyPostOnboardingFeedDefault(followedCount: Int) {
        if (followedCount > 0) {
            hasFollowedSomeone = true
            return
        }
        if (!remoteConfigService.trendingFeedEnabled) return
        wasAutoDefaultedToTrending = true
        preferencesDataStore.setFeedModeImmediate("trending", scope)
    }

    /**
     * The user followed someone. Unblocks the hint for zero-follow users who
     * were auto-landed on Trending, and re-evaluates so it can appear now.
     */
    fun noteFollowedSomeone() {
        if (!hasFollowedSomeone) hasFollowedSomeone = true
        evaluate()
    }

    /**
     * The switcher was opened (the menu was tapped). Baseline discovery signal —
     * logs `feed_switcher_opened` on every open regardless of the RC flag and
     * permanently retires the hint.
     */
    fun markSwitcherOpened() {
        retire()
        analyticsService.logFeedSwitcherOpened()
    }

    /**
     * A feed mode was selected. Retires the hint (the user clearly found the
     * switcher) WITHOUT re-logging `feed_switcher_opened` — the open already
     * logged it, and web fires the event only on open, not on select.
     */
    fun noteSwitcherUsed() {
        retire()
    }

    /** The user tapped the bubble to dismiss it — retires the hint for good. */
    fun dismiss() {
        hasDismissedHint = true
        _shouldShow.value = false
        analyticsService.logFeedSwitchHintDismissed()
    }

    /** Clears the post-onboarding auto-default / first-follow flags (e.g. on sign-out). */
    fun clearAutoDefaultedToTrending() {
        wasAutoDefaultedToTrending = false
        hasFollowedSomeone = false
    }

    private fun retire() {
        if (!hasOpenedSwitcher) hasOpenedSwitcher = true
        wasAutoDefaultedToTrending = false
        _shouldShow.value = false
    }

    private fun currentlyEligible(): Boolean {
        if (remoteConfigService.feedModeTabsEnabled) return false
        return computeShouldShow(
        enabled = remoteConfigService.feedSwitchHintEnabled,
        minSession = remoteConfigService.feedSwitchHintMinSession,
        maxImpressions = remoteConfigService.feedSwitchHintMaxImpressions,
        hasOpened = hasOpenedSwitcher,
        hasDismissed = hasDismissedHint,
        shownThisSession = shownThisSession,
        sessionCount = sessionCount,
        shownCount = shownCount,
        // A non-Following persisted mode normally flags prior exploration —
        // except the post-onboarding auto-default to Trending, which must not
        // suppress the hint on the visit where it's most useful.
        hasExploredOtherFeed = !wasAutoDefaultedToTrending &&
            preferencesDataStore.feedModeSyncSeed()
                .let { it.isNotEmpty() && it != "following" },
            wasAutoDefaultedToTrending = wasAutoDefaultedToTrending,
            hasFollowedSomeone = hasFollowedSomeone,
            feedModeTabsEnabled = remoteConfigService.feedModeTabsEnabled,
        )
    }

    companion object {
        private const val KEY_OPENED = "opened"
        private const val KEY_DISMISSED = "dismissed"
        private const val KEY_SHOWN_COUNT = "shown_count"
        private const val KEY_SESSION_COUNT = "session_count"
        private const val KEY_AUTO_DEFAULTED = "auto_defaulted_to_trending"
        private const val KEY_HAS_FOLLOWED = "has_followed_someone"
        private const val REVEAL_DELAY_MS = 600L

        /** Pure gate — mirrors web `shouldShowHint`. Extracted for testing. */
        fun computeShouldShow(
            enabled: Boolean,
            minSession: Int,
            maxImpressions: Int,
            hasOpened: Boolean,
            hasDismissed: Boolean,
            shownThisSession: Boolean,
            sessionCount: Int,
            shownCount: Int,
            hasExploredOtherFeed: Boolean = false,
            wasAutoDefaultedToTrending: Boolean = false,
            hasFollowedSomeone: Boolean = true,
            feedModeTabsEnabled: Boolean = false,
        ): Boolean {
            if (feedModeTabsEnabled) return false
            if (!enabled) return false
            if (hasOpened || hasDismissed) return false
            // Already on / last picked a non-Following feed → they discovered the
            // switcher and explored other feeds (even before this hint shipped).
            // Callers pass false when the mode was set by post-onboarding default.
            if (hasExploredOtherFeed) return false
            // Auto-landed on Trending with nobody followed: wait until they
            // follow someone, then teach them how to switch feeds.
            if (wasAutoDefaultedToTrending && !hasFollowedSomeone) return false
            if (shownThisSession) return false
            if (sessionCount < minSession) return false
            if (shownCount >= maxImpressions) return false
            return true
        }
    }
}
