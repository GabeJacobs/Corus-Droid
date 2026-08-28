package fm.corus.android.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Horizontal rails consume leftover X so a parent [androidx.compose.foundation.pager.HorizontalPager]
 * cannot page while the finger is on the rail. Matches iOS
 * `defersToInnerHorizontalScroll` on Search.
 */
fun Modifier.deferToInnerHorizontalScroll(): Modifier =
    nestedScroll(DeferToInnerHorizontalScroll)

private object DeferToInnerHorizontalScroll : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (available.x != 0f) Offset(available.x, 0f) else Offset.Zero
}
