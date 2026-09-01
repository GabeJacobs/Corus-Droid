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

    /** Follow / Message / Edit / playlist pills. Matches iOS `instagramActionHeight`. */
    val profileActionHeight = 34.dp

    /** Extra gap above action pills when bio/website are empty. Matches iOS. */
    val profileEmptyInfoActionsExtra = 4.dp

    /**
     * Feed + profile compose `+` leading inset. Same token on both screens
     * so the glyph does not jump when switching tabs. Matches iOS `lg` (16pt).
     */
    val composePlusLeading = 16.dp

    /** Feed + profile compose `+` hit box. */
    val composePlusSide = 32.dp

    /** Compose `+` glyph. A notch under 32 so it sits with the cog, not over iOS. */
    val composePlusIcon = 28.dp

    /** Profile style vinyl. A notch under iOS 37pt so it sits with the smaller chrome. */
    val profileStyleIcon = 34.dp

    /** Settings cog. Paired with [composePlusIcon] / the style disc. */
    val profileSettingsIcon = 23.dp

    /**
     * Shared Feed + profile title-row height (and top inset) so the compose
     * `+` does not jump vertically when switching tabs.
     */
    val headerTitleRowTop = sm
    val headerTitleRowHeight = 34.dp

    /** Feed filter (un-narrowed). Active filter uses +3 on top of this. */
    val feedFilterIcon = 23.dp

    /** Feed playlist. */
    val feedPlaylistIcon = 22.dp

    /** Icon-only playlist pill next to Edit / Share (34dp tall). */
    val profileActionPlaylistIcon = 23.dp

    /** Top/bottom pad on a post header. Matches iOS (14pt). */
    val postHeaderVertical = 14.dp

    val albumArtThumbnail = 56.dp
    val albumArtSearch = 48.dp

    val touchTarget = 44.dp

    /** Collapsed mini-player artwork. 36dp (was 40) so a 56dp bar still has air. */
    val miniPlayerArtwork = 36.dp

    /** Vertical inset of the collapsed mini-player. 6dp + 44dp controls = 56dp,
     *  ~93% of the previous 60dp bar (8+8 pad). Matches iOS. */
    val miniPlayerVerticalPadding = 6.dp

    /** Floor for collapsed mini-player layout reserve / park height.
     *  Measured height wins when larger; this only prevents a 0-height first frame. */
    val miniPlayerMinHeight = 50.dp

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
