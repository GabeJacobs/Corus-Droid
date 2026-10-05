package fm.corus.android.ui.screens.feed

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
    prototypeEnabled: Boolean = false,
): List<String> = (if (prototypeEnabled) listOf(
    FeedModeOrder.FOLLOWING, FeedModeOrder.TASTE_MATCHES, FeedModeOrder.TRENDING, FeedModeOrder.FAVORITES,
) else FEED_MODE_TAB_ORDER).filter { mode ->
    when (mode) {
        FeedModeOrder.FOLLOWING -> true
        FeedModeOrder.TRENDING -> trendingEnabled
        FeedModeOrder.TASTE_MATCHES -> tasteMatchesAvailable || prototypeEnabled
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

/** Matches iOS `FeedModeTabBar` VStack spacing + top pad. */
internal val TabLabelToUnderline = 6.dp
internal val TabRowTopPadding = 6.dp
/** Matches iOS `FeedModeTabBar` 14pt Nunito Heavy. */
internal const val TabLabelSizeSp = 14
/** Max extra each side. Actual overshoot is the smaller of this and half the tightest gap. */
internal val TabUnderlineExtra = 24.dp
/** Matches the 2pt iOS accent pill. */
internal val TabUnderlineHeight = 2.dp
/**
 * Inset for the first/last label when 4 tabs are visible (feed with
 * Favorites, or search). iOS uses 14pt; on a wide Android phone that
 * pins Following / Matches to the glass. 36.dp leaves a little more
 * gap between tabs without hugging the edges.
 */
internal val TabRowHorizontalPadding = 36.dp
/**
 * Extra inset when Favorites is hidden (3 tabs). Pulls Following /
 * Matches inward so the leftover gap is smaller. 4-tab rows keep
 * [TabRowHorizontalPadding].
 */
internal val TabRowThreeTabHorizontalPadding = 56.dp

internal fun tabRowHorizontalPadding(tabCount: Int) =
    if (tabCount >= 4) TabRowHorizontalPadding else TabRowThreeTabHorizontalPadding

/**
 * Compact feed-mode tab row under the Corus wordmark. Labels stay full size
 * (no scaling). Taste Matches shortens to "Matches". Leftover width is
 * split between the tabs (iOS `Spacer`). The accent underline is the
 * label width plus a short overshoot, and tracks [pagerOffset].
 */
@Composable
fun FeedModeTabBar(
    modes: List<String>,
    selected: String,
    pagerOffset: Float,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    prototypeEnabled: Boolean = false,
    tuningMode: fm.corus.android.domain.ForYouTuningMode = fm.corus.android.domain.ForYouTuningMode.BALANCED,
    onTune: (() -> Unit)? = null,
) {
    val reduceMotion = if (prototypeEnabled) rememberControlsReduceMotion() else false
    val labels = ArrayList<String>(modes.size)
    for (mode in modes) {
        labels.add(if (prototypeEnabled && mode == FeedModeOrder.TASTE_MATCHES) stringResource(R.string.for_you_title) else feedModeTabLabel(mode))
    }
    ModeTabBar(
        labels = labels,
        selectedIndex = modes.indexOf(selected).coerceAtLeast(0),
        pagerOffset = pagerOffset,
        onSelect = { index ->
            modes.getOrNull(index)?.let { mode ->
                if (prototypeEnabled && mode == FeedModeOrder.TASTE_MATCHES && selected == mode && onTune != null) {
                    onTune()
                } else {
                    onSelect(mode)
                }
            }
        },
        modifier = modifier,
        showDivider = showDivider,
        tabAccessory = { index, activation, isSelected ->
            if (prototypeEnabled && modes[index] == FeedModeOrder.TASTE_MATCHES && onTune != null) {
                val controlsActivation = if (reduceMotion) { if (isSelected) 1f else 0f } else activation
                val description = stringResource(R.string.for_you_tune) + ": " + stringResource(tuningMode.titleResource)
                Box(
                    Modifier.width(28.dp * controlsActivation).height(22.dp)
                        .clipToBounds().alpha(controlsActivation)
                        .then(if (isSelected) Modifier
                            .clickable(role = Role.Button, onClick = onTune)
                            .semantics { contentDescription = description }
                        else Modifier.clearAndSetSemantics {}),
                ) {
                    // Keep the artwork fixed; release its slot with the pager,
                    // clipping from the trailing edge like iOS's nested frames.
                    Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(28.dp).height(22.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Tune, contentDescription = null,
                            tint = CorusColors.Accent, modifier = Modifier.size(14.dp))
                    }
                }
            }
        },
        horizontalPadding = if (prototypeEnabled) 14.dp else null,
    )
}

/** Android's Remove animations setting is the counterpart of iOS Reduce Motion. */
@Composable
private fun rememberControlsReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    var reduceMotion by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduceMotion = read() }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduceMotion
}

/**
 * Shared feed / search tab row: wrap-width labels, leftover space split
 * between tabs. The accent pill matches the current label and interpolates
 * with the pager; overshoot shrinks when tabs sit closer (4-tab search / feed).
 */
@Composable
fun ModeTabBar(
    labels: List<String>,
    selectedIndex: Int,
    pagerOffset: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    tabAccessory: (@Composable (Int, Float, Boolean) -> Unit)? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp? = null,
) {
    val frames = remember(labels) { mutableStateMapOf<Int, Rect>() }
    var barBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    // Hairline + accent share this box's bottom edge, matching iOS
    // `overlay(alignment: .bottom)` so the divider is edge-to-edge and
    // the pill sits on it. Draw the divider first so it cannot cover the
    // bottom of the accent pill.
    Box(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CorusColors.Divider),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding ?: tabRowHorizontalPadding(labels.size))
                .onGloballyPositioned { barBounds = it.boundsInWindow() },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                labels.forEachIndexed { index, label ->
                    if (index > 0) {
                        Spacer(Modifier.widthIn(min = 8.dp).weight(1f))
                    }
                    val activation = FeedChromeCollapseMath.tabActivation(pagerOffset, index)
                    val isSelected = selectedIndex == index
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable(
                                interactionSource = remember(label) { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(index) },
                            )
                            .semantics { this.selected = isSelected }
                            .padding(top = TabRowTopPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(TabLabelToUnderline),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                style = CorusFont.custom(
                                    if (activation >= 0.5f) 800 else 500,
                                    TabLabelSizeSp,
                                ),
                                color = androidx.compose.ui.graphics.lerp(
                                    CorusColors.Secondary,
                                    CorusColors.Text,
                                    activation,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                softWrap = false,
                            )
                            tabAccessory?.invoke(index, activation, isSelected)
                        }
                        Box(
                            modifier = Modifier
                                .height(TabUnderlineHeight)
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

            val maxExtraPx = with(density) { TabUnderlineExtra.toPx() }
            val extraPx = underlineOvershootPx(frames, maxExtraPx)
            val placement = underlinePlacement(
                labels.size,
                pagerOffset,
                frames,
                extraPx,
            )
            if (placement != null) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(placement.left.roundToInt(), placement.top.roundToInt()) }
                        .width(with(density) { placement.width.toDp() })
                        .height(TabUnderlineHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(CorusColors.Accent),
                )
            }
        }
    }
}

/**
 * Overshoot shrinks when tabs sit closer together. 3-tab rows keep the
 * 24.dp extra; 4-tab (and search) rows use half the smallest gap so the
 * pill does not keep the 3-tab length.
 */
internal fun underlineOvershootPx(frames: Map<Int, Rect>, maxExtraPx: Float): Float {
    val ordered = frames.entries.sortedBy { it.key }.map { it.value }
    if (ordered.size < 2) return maxExtraPx
    val minGap = ordered.zipWithNext { a, b -> b.left - a.right }.minOrNull() ?: return maxExtraPx
    return minOf(maxExtraPx, (minGap / 2f).coerceAtLeast(0f))
}

/** Lerp the slot center and width so each label gets its own pill. */
internal fun underlinePlacement(
    modeCount: Int,
    pagerOffset: Float,
    frames: Map<Int, Rect>,
    extraPx: Float = 0f,
): Rect? {
    if (modeCount <= 0) return null
    val maxPage = (modeCount - 1).toFloat()
    val page = pagerOffset.coerceIn(0f, maxPage)
    val i0 = page.toInt().coerceIn(0, modeCount - 1)
    val i1 = (i0 + 1).coerceAtMost(modeCount - 1)
    val f0 = frames[i0] ?: return null
    val f1 = frames[i1] ?: return null
    val t = page - i0
    val center0 = (f0.left + f0.right) / 2f
    val center1 = (f1.left + f1.right) / 2f
    val center = center0 + (center1 - center0) * t
    val width = f0.width + (f1.width - f0.width) * t + extraPx * 2f
    val left = center - width / 2f
    return Rect(
        left = left,
        top = f0.top,
        right = left + width,
        bottom = f0.top + f0.height,
    )
}
