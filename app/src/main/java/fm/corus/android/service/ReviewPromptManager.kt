package fm.corus.android.service

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ReviewPromptManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfig: RemoteConfigService,
    private val analytics: AnalyticsService,
) {
    private val prefs = context.getSharedPreferences("review_prompt", Context.MODE_PRIVATE)
    private val _readyUserId = MutableStateFlow<String?>(null)
    val readyUserId = _readyUserId.asStateFlow()
    private var deferredUntilNextVisit = false

    fun beginVisit(userId: String, now: Long = System.currentTimeMillis()) {
        deferredUntilNextVisit = false
        recordActiveDay(userId, now)
        refresh(userId, now)
    }

    fun recordPublishedPost(
        userId: String,
        deferUntilNextVisit: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        recordActiveDay(userId, now)
        if (!remoteConfig.reviewPromptEnabled || prefs.getInt("days.$userId", 0) < 3 ||
            !cooldownElapsed(now)
        ) return

        prefs.edit().putBoolean("pending.$userId", true).apply()
        if (deferUntilNextVisit) deferredUntilNextVisit = true
        refresh(userId, now)
    }

    fun deferForThisVisit() {
        deferredUntilNextVisit = true
        _readyUserId.value = null
    }

    suspend fun requestIfReady(activity: Activity, userId: String, canPresent: () -> Boolean) {
        if (_readyUserId.value != userId || !remoteConfig.reviewPromptEnabled) return
        val manager = ReviewManagerFactory.create(context)
        val info = suspendCancellableCoroutine<ReviewInfo?> { continuation ->
            manager.requestReviewFlow().addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
        if (_readyUserId.value != userId) return
        if (info == null || !canPresent() || !activity.hasWindowFocus()) {
            deferForThisVisit()
            return
        }
        if (!consumeRequest(userId)) return

        manager.launchReviewFlow(activity, info)
        analytics.logEvent("review_prompt_requested", mapOf("trigger" to "post_published"))
    }

    internal fun consumeRequest(userId: String, now: Long = System.currentTimeMillis()): Boolean {
        if (_readyUserId.value != userId || !cooldownElapsed(now) || !remoteConfig.reviewPromptEnabled) {
            return false
        }
        prefs.edit().putLong(KEY_LAST_REQUEST, now).remove("pending.$userId").apply()
        _readyUserId.value = null
        return true
    }

    private fun refresh(userId: String, now: Long) {
        _readyUserId.value = userId.takeIf {
            !deferredUntilNextVisit && cooldownElapsed(now) && prefs.getBoolean("pending.$userId", false)
        }
    }

    private fun recordActiveDay(userId: String, now: Long) {
        val today = now / DAY_MS
        if (today <= prefs.getLong("last_day.$userId", 0)) return
        val count = prefs.getInt("days.$userId", 0)
        prefs.edit().putLong("last_day.$userId", today)
            .putInt("days.$userId", (count + 1).coerceAtMost(3)).apply()
    }

    private fun cooldownElapsed(now: Long): Boolean =
        now - prefs.getLong(KEY_LAST_REQUEST, 0) >= 30 * DAY_MS

    private companion object {
        const val KEY_LAST_REQUEST = "last_prompt_date"
        const val DAY_MS = 86_400_000L
    }
}
