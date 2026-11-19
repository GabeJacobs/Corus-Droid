package fm.corus.android.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Keyboard-first downward drags on sheet chrome/results; exclude editable content. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.dismissKeyboardOnDownwardDrag(): Modifier {
    val visible by rememberUpdatedState(WindowInsets.isImeVisible)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val dismiss by rememberUpdatedState({ focus.clearFocus(); keyboard?.hide() })
    return keyboardFirstDownwardDrag({ visible }) { dismiss() }
}

internal fun Modifier.keyboardFirstDownwardDrag(
    keyboardVisible: () -> Boolean,
    dismissKeyboard: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startedWithKeyboard = keyboardVisible()
        var deciding = startedWithKeyboard
        var intercepted = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val displacement = change.position - down.position
            if (deciding && (abs(displacement.x) > viewConfiguration.touchSlop || abs(displacement.y) > viewConfiguration.touchSlop)) {
                deciding = false
                if (displacement.y > viewConfiguration.touchSlop && displacement.y > abs(displacement.x)) {
                    intercepted = true
                    dismissKeyboard()
                }
            }
            // Retain ownership through release, even after IME visibility changes.
            // Material's draggable sees consumed movement and cancels this drag.
            if (intercepted) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/** Only sheet chrome owns this gesture; reading/scrolling content never closes it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.closeSheetOnHeaderPull(onClose: () -> Unit): Modifier {
    val visible by rememberUpdatedState(WindowInsets.isImeVisible)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val close by rememberUpdatedState(onClose)
    return pointerInput(Unit) {
        val threshold = 88.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val keyboardFirst = visible
            var dy = 0f
            var dx = 0f
            var pulled = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                dy = change.position.y - down.position.y
                dx = change.position.x - down.position.x
                if (dy > viewConfiguration.touchSlop && dy > abs(dx)) {
                    pulled = true
                    if (keyboardFirst) { focus.clearFocus(); keyboard?.hide() }
                }
                if (pulled) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
            if (!keyboardFirst && dy > threshold && dy > abs(dx) * 1.2f) close()
        }
    }
}
