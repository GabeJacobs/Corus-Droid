package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.domain.FeedModeOrder
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlin.math.roundToInt

/** Fixed tab order — `feed_mode_order` still owns the dropdown, not the tabs. */
val FEED_MODE_TAB_ORDER = listOf(
    FeedModeOrder.FOLLOWING,
    FeedModeOrder.TRENDING,
    FeedModeOrder.TASTE_MATCHES,
    FeedModeOrder.FAVORITES,
)

/**
 * Modes shown in the tab row + pager. Following always appears; Trending /
 * Taste Matches / Favorites follow the same gates as iOS (Favorites also
 * requires at least one starred person).
 */
fun visibleFeedModeTabs(
    trendingEnabled: Boolean,
    tasteMatchesAvailable: Boolean,
    favoritesEnabled: Boolean,
    favoritesCount: Int,
    favoritesUnlocked: Boolean = false,
): List<String> = FEED_MODE_TAB_ORDER.filter { mode ->
    when (mode) {
        FeedModeOrder.FOLLOWING -> true
        FeedModeOrder.TRENDING -> trendingEnabled
        FeedModeOrder.TASTE_MATCHES -> tasteMatchesAvailable
        FeedModeOrder.FAVORITES ->
            fm.corus.android.domain.FavoritesTabGate.showsTab(
                featureEnabled = favoritesEnabled,
                count = favoritesCount,
                unlocked = favoritesUnlocked,
            )
        else -> false
    }
}

@Composable
fun feedModeLabel(mode: String): String = when (mode) {
    FeedModeOrder.FOLLOWING -> stringResource(R.string.feed_mode_following)
    FeedModeOrder.TRENDING -> stringResource(R.string.feed_mode_trending)
    FeedModeOrder.TASTE_MATCHES -> stringResource(R.string.feed_mode_taste_matches)
    FeedModeOrder.FAVORITES -> stringResource(R.string.feed_mode_favorites)
    else -> mode
}

@Composable
fun feedModeTabLabel(mode: String): String = when (mode) {
    FeedModeOrder.TASTE_MATCHES -> stringResource(R.string.feed_mode_taste_matches_tab)
    else -> feedModeLabel(mode)
}

/**
 * Compact feed-mode tab row under the Corus wordmark. Labels stay full size
 * (no scaling). Taste Matches shortens to "Matches". Leftover width is split
 * between the tabs. The accent underline tracks [pagerOffset] (page index +
 * fraction) so it rides with the pager while you swipe.
 */
@Composable
fun FeedModeTabBar(
    modes: List<String>,
    selected: String,
    pagerOffset: Float,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frames = remember { mutableStateMapOf<Int, Rect>() }
    var barBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .onGloballyPositioned { barBounds = it.boundsInWindow() },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                modes.forEachIndexed { index, mode ->
                    if (index > 0) {
                        Spacer(Modifier.widthIn(min = 8.dp).weight(1f))
                    }
                    val activation = FeedChromeCollapseMath.tabActivation(pagerOffset, index)
                    val isSelected = selected == mode
                    Column(
                        modifier = Modifier
                            .wrapContentWidth()
                            .clickable(
                                interactionSource = remember(mode) { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(mode) },
                            )
                            .semantics { this.selected = isSelected }
                            .padding(top = 6.dp, bottom = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = feedModeTabLabel(mode),
                            style = CorusFont.custom(if (activation >= 0.5f) 800 else 500, 14),
                            color = androidx.compose.ui.graphics.lerp(
                                CorusColors.Secondary,
                                CorusColors.Text,
                                activation,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            softWrap = false,
                        )
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    val window = coords.boundsInWindow()
                                    frames[index] = Rect(
                                        left = window.left - barBounds.left,
                                        top = window.top - barBounds.top,
                                        right = window.right - barBounds.left,
                                        bottom = window.bottom - barBounds.top,
                                    )
                                },
                        )
                    }
                }
            }

            val placement = underlinePlacement(modes.size, pagerOffset, frames)
            if (placement != null) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(placement.left.roundToInt(), placement.top.roundToInt()) }
                        .width(with(density) { placement.width.toDp() })
                        .height(2.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(CorusColors.Accent),
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = CorusColors.Divider)
    }
}

private fun underlinePlacement(
    modeCount: Int,
    pagerOffset: Float,
    frames: Map<Int, Rect>,
): Rect? {
    if (modeCount <= 0) return null
    val maxPage = (modeCount - 1).toFloat()
    val page = pagerOffset.coerceIn(0f, maxPage)
    val i0 = page.toInt().coerceIn(0, modeCount - 1)
    val i1 = (i0 + 1).coerceAtMost(modeCount - 1)
    val f0 = frames[i0] ?: return null
    val f1 = frames[i1] ?: return null
    val t = page - i0
    return Rect(
        left = f0.left + (f1.left - f0.left) * t,
        top = f0.top,
        right = f0.left + (f1.left - f0.left) * t + f0.width + (f1.width - f0.width) * t,
        bottom = f0.top + f0.height,
    )
}
