package fm.corus.android.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewPromptManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfigService: RemoteConfigService,
) {
    private var sessionLikeCount = 0
    private val prefs = context.getSharedPreferences("review_prompt", Context.MODE_PRIVATE)

    private val _shouldShowPrompt = MutableStateFlow(false)
    val shouldShowPrompt: StateFlow<Boolean> = _shouldShowPrompt.asStateFlow()

    fun recordLike() {
        if (!remoteConfigService.reviewPromptEnabled) return
        sessionLikeCount++
        if (sessionLikeCount >= 3 && canShowPrompt()) {
            _shouldShowPrompt.value = true
        }
    }

    fun promptShown() {
        _shouldShowPrompt.value = false
        prefs.edit().putLong("last_prompt_date", System.currentTimeMillis()).apply()
        val dismissCount = prefs.getInt("dismiss_count", 0)
        prefs.edit().putInt("dismiss_count", dismissCount + 1).apply()
    }

    fun resetSession() {
        sessionLikeCount = 0
    }

    private fun canShowPrompt(): Boolean {
        val lastPrompt = prefs.getLong("last_prompt_date", 0)
        val daysSince = (System.currentTimeMillis() - lastPrompt) / (1000 * 60 * 60 * 24)
        return daysSince >= 60
    }
}
