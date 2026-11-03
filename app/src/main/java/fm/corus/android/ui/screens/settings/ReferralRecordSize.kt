package fm.corus.android.ui.screens.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun referralRecordSize(space: Dp, count: Int, reward: Boolean): Dp {
    if (!reward) return space
    val base = minOf(116.dp, space)
    return base + (space - base) * (count.coerceIn(0, 5) / 5f)
}
