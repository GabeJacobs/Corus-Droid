package fm.corus.android.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable

/** Push history forward and pop it back, preserving both pages during the transition. */
@Composable
internal fun ReferralHistoryNavigation(
    showHistory: Boolean,
    content: @Composable (Boolean) -> Unit,
) {
    AnimatedContent(
        targetState = showHistory,
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            slideInHorizontally(tween(250)) { it * direction } togetherWith
                slideOutHorizontally(tween(250)) { -it * direction }
        },
        label = "referral_history_navigation",
    ) { showingHistory -> content(showingHistory) }
}
