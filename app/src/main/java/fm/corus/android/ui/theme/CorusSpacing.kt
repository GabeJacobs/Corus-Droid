package fm.corus.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object CorusSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    val avatarSmall = 28.dp
    val avatarMedium = 36.dp
    val avatarLarge = 72.dp

    val albumArtThumbnail = 56.dp
    val albumArtSearch = 48.dp

    val touchTarget = 44.dp

    val tabBarHeight = 50.dp

    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val iconXl = 28.dp

    val cornerRadius = 6.dp
    val cornerRadiusMedium = 12.dp
    val cornerRadiusLarge = 16.dp
    val pillCornerRadius = 14.dp

    /** iPad-equivalent cap so wide screens don't blow out the layout. */
    val maxContentWidth = 645.dp

    const val bottomSheetMaxHeightFraction = 0.94f

    /**
     * Largest screen width (dp) still treated as "narrow" for the own-profile
     * EDIT / SHARE action row. Matches iOS `narrowProfileActionRowMaxWidth`
     * (375pt): mini/SE-class and small Androids (~360dp) get the smaller
     * button type; Pixel-class 390dp+ keep full size.
     */
    const val narrowProfileActionRowMaxWidthDp = 375

    fun isNarrowProfileActionRow(widthDp: Int): Boolean =
        widthDp <= narrowProfileActionRowMaxWidthDp
}

/**
 * Card width for two-up horizontal rails that show ~half of the third card
 * on the right edge — a strong visual hint that the rail scrolls.
 *
 * Layout: `lg + card + md + card + md + (card / 2)` across the screen, so
 * `2.5 * card = screen - lg - 2*md`. Matches the spec: third card peeks
 * ~half-width regardless of phone size.
 */
@Composable
fun horizontalRailCardWidth(): Dp {
    val screen = minOf(LocalConfiguration.current.screenWidthDp.dp, CorusSpacing.maxContentWidth)
    val computed = (screen - CorusSpacing.lg - CorusSpacing.md * 2) / 2.5f
    return maxOf(140.dp, computed)
}

@Composable
fun bottomSheetMaxHeight(): Dp =
    (LocalConfiguration.current.screenHeightDp * CorusSpacing.bottomSheetMaxHeightFraction).dp
