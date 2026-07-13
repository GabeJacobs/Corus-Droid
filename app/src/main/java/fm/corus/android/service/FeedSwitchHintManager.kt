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
 * The default feed is Following, so a user who never opens the switcher never
 * finds Trending / Taste Matches; this reveals it once.
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
    }

    private fun retire() {
        if (!hasOpenedSwitcher) hasOpenedSwitcher = true
        _shouldShow.value = false
    }

    private fun currentlyEligible(): Boolean = computeShouldShow(
        enabled = remoteConfigService.feedSwitchHintEnabled,
        minSession = remoteConfigService.feedSwitchHintMinSession,
        maxImpressions = remoteConfigService.feedSwitchHintMaxImpressions,
        hasOpened = hasOpenedSwitcher,
        hasDismissed = hasDismissedHint,
        shownThisSession = shownThisSession,
        sessionCount = sessionCount,
        shownCount = shownCount,
        // A non-Following persisted mode is only ever set by an explicit pick, so
        // it flags users who already explored feed types (even before the hint
        // shipped, whose opened/dismissed flags never recorded). Following/empty
        // are excluded — Following is also written programmatically (the
        // empty-state reset at FeedScreen `setFeedMode("following")`).
        hasExploredOtherFeed = preferencesDataStore.feedModeSyncSeed()
            .let { it.isNotEmpty() && it != "following" },
    )

    companion object {
        private const val KEY_OPENED = "opened"
        private const val KEY_DISMISSED = "dismissed"
        private const val KEY_SHOWN_COUNT = "shown_count"
        private const val KEY_SESSION_COUNT = "session_count"
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
        ): Boolean {
            if (!enabled) return false
            if (hasOpened || hasDismissed) return false
            // Already on / last picked a non-Following feed → they discovered the
            // switcher and explored other feeds (even before this hint shipped).
            if (hasExploredOtherFeed) return false
            if (shownThisSession) return false
            if (sessionCount < minSession) return false
            if (shownCount >= maxImpressions) return false
            return true
        }
    }
}
