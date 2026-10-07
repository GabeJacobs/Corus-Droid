package fm.corus.android.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect
import fm.corus.android.service.AnalyticsService

/**
 * Log an actual screen appearance, including foreground returns. Inactive tab
 * graphs stay composed, so their screens must also supply their visibility.
 * The destination lifecycle prevents counting a retained screen under a detail
 * page; stable effect keys prevent ordinary recompositions from adding views.
 */
@Composable
internal fun ScreenViewAnalytics(
    screenName: String,
    analyticsService: AnalyticsService,
    isVisible: Boolean = true,
) {
    LifecycleResumeEffect(screenName, isVisible, analyticsService) {
        if (isVisible) analyticsService.logScreenView(screenName)
        onPauseOrDispose { }
    }
}
