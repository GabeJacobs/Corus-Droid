package fm.corus.android.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * iOS [ReviewPromptManager] parity: after 8 likes in a process session, show a
 * custom "Leave a review" sheet. Tapping the button opens the Play Store listing
 * (the Play equivalent of iOS's App Store write-review URL). Backdrop / Not now
 * dismisses and starts the 60-day cooldown.
 *
 * Existing users who were never asked have no [KEY_LAST_PROMPT_DATE], so they
 * are eligible the same way as new users.
 */
@Singleton
class ReviewPromptManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfigService: RemoteConfigService,
) {
    private var sessionLikeCount = 0
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _shouldShowPrompt = MutableStateFlow(false)
    val shouldShowPrompt: StateFlow<Boolean> = _shouldShowPrompt.asStateFlow()

    fun recordLike() {
        if (!remoteConfigService.reviewPromptEnabled) return
        if (hasRecentlyPrompted()) return
        sessionLikeCount += 1
        if (sessionLikeCount >= LIKES_NEEDED && !_shouldShowPrompt.value) {
            _shouldShowPrompt.value = true
        }
    }

    /** User tapped "Leave a review" — hide the sheet and open the Play listing. */
    fun requestReview() {
        _shouldShowPrompt.value = false
        markPrompted()
        openPlayStoreListing()
    }

    /** User tapped "Not now" or the dimmed backdrop. */
    fun dismiss() {
        _shouldShowPrompt.value = false
        markPrompted()
        val count = prefs.getInt(KEY_DISMISS_COUNT, 0)
        prefs.edit().putInt(KEY_DISMISS_COUNT, count + 1).apply()
    }

    fun resetSession() {
        sessionLikeCount = 0
    }

    /**
     * Never-asked devices (no stored date, including every existing user who
     * never saw this prompt) are eligible. Matches iOS `hasRecentlyPrompted`.
     */
    private fun hasRecentlyPrompted(): Boolean {
        val lastDate = prefs.getLong(KEY_LAST_PROMPT_DATE, 0L)
        if (lastDate <= 0L) return false
        val daysSince = (System.currentTimeMillis() - lastDate) / MS_PER_DAY
        return daysSince < DAYS_BETWEEN_PROMPTS
    }

    private fun markPrompted() {
        prefs.edit().putLong(KEY_LAST_PROMPT_DATE, System.currentTimeMillis()).apply()
        sessionLikeCount = 0
    }

    private fun openPlayStoreListing() {
        val packageName = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(market)
        } catch (_: Exception) {
            val web = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            runCatching { context.startActivity(web) }
        }
    }

    companion object {
        private const val PREFS_NAME = "review_prompt"
        internal const val KEY_LAST_PROMPT_DATE = "last_prompt_date"
        private const val KEY_DISMISS_COUNT = "dismiss_count"
        internal const val LIKES_NEEDED = 8
        private const val DAYS_BETWEEN_PROMPTS = 60
        private const val MS_PER_DAY = 1000L * 60 * 60 * 24
    }
}
