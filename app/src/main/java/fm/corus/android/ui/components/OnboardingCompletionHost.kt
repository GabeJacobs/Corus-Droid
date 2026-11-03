package fm.corus.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.theme.CorusColors

/** Keep the notification primer under the same conditional HUD as iOS. */
@Composable
internal fun OnboardingCompletionHost(
    completing: Boolean,
    hasPendingInvitation: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        ChromeLoadingHud(
            visible = completing && hasPendingInvitation,
            message = stringResource(CorusStrings.referral_checking),
            spinnerTint = CorusColors.Accent,
            showDelayMs = 0L,
        )
    }
}
