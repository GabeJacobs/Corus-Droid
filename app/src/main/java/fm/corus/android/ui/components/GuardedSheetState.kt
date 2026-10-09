package fm.corus.android.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull

/** Shared modal sheet state, with two guards against accidental
 *  dismissal (Instagram-style):
 *  1. While the keyboard is up, a swipe-down only closes the keyboard.
 *  2. Releasing after a short drag springs the sheet back up instead of
 *     dismissing it. A deliberate drag past ~a quarter of the screen, a tap on
 *     the dimmed area, or the back gesture still dismiss. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun rememberGuardedSheetState(
    skipPartiallyExpanded: Boolean = true,
    // An explicit action may dismiss immediately after a detent change, before
    // the resting offset settles. Its hide animation should bypass drag guards.
    allowProgrammaticDismiss: () -> Boolean = { false },
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState {
    val callerConfirm by rememberUpdatedState(confirmValueChange)
    val programmaticDismiss by rememberUpdatedState(allowProgrammaticDismiss)
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val dismissDragPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() } * DISMISS_DRAG_FRACTION
    val dismissDrag by rememberUpdatedState(dismissDragPx)
    // Top edge of the sheet when it is at rest (updated whenever it settles).
    val restingTop = remember { mutableFloatStateOf(Float.NaN) }
    val holder = remember { arrayOfNulls<SheetState>(1) }
    val confirm = remember {
        { target: SheetValue ->
            if (!callerConfirm(target)) {
                false
            } else if (target != SheetValue.Hidden || programmaticDismiss()) {
                true
            } else {
                val top = runCatching { holder[0]?.requireOffset() }.getOrNull()
                val dragged = if (top != null && !restingTop.floatValue.isNaN()) top - restingTop.floatValue else 0f
                // dragged ~ 0 means scrim tap / back / a programmatic hide(): always allow.
                // A real drag with the keyboard up only closes the keyboard; a real but
                // short drag springs back instead of dismissing.
                when {
                    dragged <= 4f -> true
                    imeVisible -> { keyboard?.hide(); false }
                    else -> dragged >= dismissDrag
                }
            }
        }
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded, confirmValueChange = confirm)
    holder[0] = state
    LaunchedEffect(state) {
        snapshotFlow { runCatching { state.requireOffset() }.getOrNull() }
            .filterNotNull()
            .debounce(500)
            .collect { if (state.currentValue != SheetValue.Hidden) restingTop.floatValue = it }
    }
    return state
}

private const val DISMISS_DRAG_FRACTION = 0.25f
