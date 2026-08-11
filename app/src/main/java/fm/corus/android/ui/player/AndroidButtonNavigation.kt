package fm.corus.android.ui.player

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** AOSP `config_navBarInteractionMode` — 3-button. */
internal const val NAV_BAR_INTERACTION_MODE_THREE_BUTTON = 0

/** AOSP `config_navBarInteractionMode` — 2-button. */
internal const val NAV_BAR_INTERACTION_MODE_TWO_BUTTON = 1

/** AOSP `config_navBarInteractionMode` — gesture. */
internal const val NAV_BAR_INTERACTION_MODE_GESTURE = 2

/**
 * Inset floor used when the OEM integer isn't available. Gesture strips are
 * typically ≤ ~28dp; 3-button nav is ~48dp.
 */
internal val ButtonNavigationInsetFloor = 40.dp

/**
 * Whether the system is using on-screen nav *buttons* (3-button / 2-button)
 * rather than gesture navigation.
 *
 * Prefer AOSP's hidden `config_navBarInteractionMode` integer; fall back to
 * the navigationBars inset height when the resource is missing.
 */
internal fun isButtonStyleNavigation(
    interactionMode: Int?,
    navInset: Dp,
): Boolean {
    return when (interactionMode) {
        NAV_BAR_INTERACTION_MODE_GESTURE -> false
        NAV_BAR_INTERACTION_MODE_THREE_BUTTON,
        NAV_BAR_INTERACTION_MODE_TWO_BUTTON,
        -> true
        else -> navInset >= ButtonNavigationInsetFloor
    }
}

internal fun readNavBarInteractionMode(context: Context): Int? {
    val id = context.resources.getIdentifier(
        "config_navBarInteractionMode",
        "integer",
        "android",
    )
    if (id == 0) return null
    return runCatching { context.resources.getInteger(id) }.getOrNull()
}

@Composable
internal fun rememberIsButtonStyleNavigation(
    navInset: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
): Boolean {
    val context = LocalContext.current
    return remember(navInset, context) {
        isButtonStyleNavigation(
            interactionMode = readNavBarInteractionMode(context),
            navInset = navInset,
        )
    }
}
