package fm.corus.android.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.LocalCorusDarkTheme

// ── Shared immersive header pieces ─────────────────────────────────────────────
// The reusable half of the immersive destination headers (artist, song, …): the
// frosted collapsing top bar, the status-bar-blend plumbing, and the collapse
// math. Each screen supplies its own hero (full-bleed photo, blurred cover, …)
// and drives these with a scroll-derived [0,1] collapse progress.

/** Prototype sub-toggle: also blend the status bar (hero + frosted bar draw up
 *  under it). Flip to false to keep the immersive header but stop at the strip. */
internal const val ImmersiveExtendUnderStatusBar = true

/** Height of the floating collapsing bar (excludes any status-bar inset). */
internal val ImmersiveBarHeight = 56.dp

/**
 * Collapse fraction, driven off a list scroll: 0 while the hero fills the top
 * (bar transparent over it), 1 once the hero has scrolled up by
 * [collapseDistancePx] or off-screen entirely (bar frosted, title shown).
 * Guards the degenerate zero-distance case so the division never yields NaN.
 */
internal fun immersiveCollapseProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    collapseDistancePx: Float,
): Float = when {
    firstVisibleItemIndex > 0 -> 1f
    collapseDistancePx <= 0f -> 1f
    else -> (firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
}

/**
 * True status-bar height in px, read straight from the window. Destination pages
 * render inside the tab scaffold, which consumes the Compose status-bar inset, so
 * a normal `WindowInsets.statusBars` read returns 0 there. Read fresh (not
 * remembered) so it self-corrects if the first read lands before insets dispatch.
 */
@Composable
internal fun currentStatusBarTopPx(): Int {
    val view = LocalView.current
    return ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
}

/**
 * Grows a screen up into the (already-consumed) status-bar strip by [extraTopPx]
 * and shifts it up the same amount, so it spans from the window top to its normal
 * bottom with no gap — while still reporting its original size to the parent, so
 * nothing else in the tab layout moves. Lets an immersive header blend the status
 * bar without changing the shared tab container. No-op when [extraTopPx] <= 0.
 */
internal fun Modifier.extendIntoStatusBar(extraTopPx: Int): Modifier =
    if (extraTopPx <= 0) {
        this
    } else {
        layout { measurable, constraints ->
            val grownMax = if (constraints.hasBoundedHeight) {
                constraints.maxHeight + extraTopPx
            } else {
                constraints.maxHeight
            }
            val placeable = measurable.measure(constraints.copy(maxHeight = grownMax))
            val reportedHeight = (placeable.height - extraTopPx).coerceAtLeast(0)
            layout(placeable.width, reportedHeight) {
                placeable.place(0, -extraTopPx)
            }
        }
    }

/**
 * Toggles the system status-bar icons for an immersive header that blends the
 * status bar: white over the dark hero at the top, theme default once the frosted
 * bar has taken over ([collapseProgress] >= 0.5). Restores the theme default when
 * the screen leaves the composition.
 */
@Composable
internal fun ImmersiveStatusBarIcons(collapseProgress: Float) {
    val view = LocalView.current
    val darkTheme = LocalCorusDarkTheme.current
    val appearanceLightStatusBars = if (collapseProgress < 0.5f) false else !darkTheme
    LaunchedEffect(appearanceLightStatusBars, view) {
        (view.context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, view)
                .isAppearanceLightStatusBars = appearanceLightStatusBars
        }
    }
    DisposableEffect(view, darkTheme) {
        onDispose {
            (view.context as? Activity)?.window?.let { window ->
                WindowInsetsControllerCompat(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
}

/**
 * Blurred-cover hero backdrop for the centered-artwork pages (song, album,
 * director): a full-bleed BLURRED copy of the cover/photo ([artUrl]) — a shimmer
 * while it loads, for skeleton parity — that scrolls up with the header via
 * [listState], under a scrim that darkens the very top for status-icon legibility
 * and fades to the app background by ~75% so the title/meta below sit on a clean
 * surface and the hero blends into the list. `blur` is a no-op below Android 12,
 * where it falls back to a sharp cover crop.
 */
@Composable
internal fun ImmersiveCoverBackdrop(
    artUrl: String?,
    height: Dp,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val heightPx = with(LocalDensity.current) { height.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                val scrolled = if (listState.firstVisibleItemIndex > 0) {
                    heightPx
                } else {
                    listState.firstVisibleItemScrollOffset.toFloat()
                }
                translationY = -minOf(scrolled, heightPx)
            },
    ) {
        if (artUrl != null) {
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(48.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shimmer()
                    .background(CorusColors.Skeleton),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        0.4f to Color.Transparent,
                        0.75f to CorusColors.Background,
                        1f to CorusColors.Background,
                    ),
                ),
        )
    }
}

/**
 * Frosted floating bar that collapses from transparent-over-hero to a solid title
 * bar as [progress] runs 0 → 1. Real backdrop blur (Haze) of the content beneath,
 * ramped in with the collapse; a thin solid sliver at the very top keeps it
 * seamless with the status strip, spreading into soft frost below. The trailing
 * [actions] slot receives the animated icon tint so its icons match the back arrow
 * (white over the hero → theme text once collapsed). When [topInset] > 0 the bar
 * also covers the status strip and the controls sit below it.
 */
@Composable
internal fun ImmersiveCollapsingBar(
    hazeState: HazeState,
    progress: Float,
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    actions: @Composable RowScope.(tint: Color) -> Unit = {},
) {
    val iconTint = lerp(Color.White, CorusColors.Text, progress)
    val frost = CorusColors.Background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ImmersiveBarHeight + topInset)
            // Real backdrop blur of the hero/list underneath; `alpha` ramps the whole
            // effect in with the collapse, so at rest the bar is fully transparent.
            .hazeEffect(state = hazeState) {
                alpha = progress
                blurRadius = 30.dp
                backgroundColor = frost
                tints = listOf(HazeTint(frost.copy(alpha = 0.5f)))
            }
            // Legibility scrim for white icons over a bright hero; fades out as the
            // frost fills in so it never darkens the collapsed bar.
            .then(
                if (progress < 1f) {
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.28f * (1f - progress)),
                                Color.Transparent,
                            ),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            // Solid only across the top ~10% (seamless with the status strip), then
            // spreads into the frosted blur the rest of the way down.
            .background(
                Brush.verticalGradient(
                    0f to frost.copy(alpha = progress),
                    0.1f to frost.copy(alpha = progress),
                    1f to Color.Transparent,
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                // Push the controls below the status strip when blending it.
                .padding(top = topInset)
                .padding(horizontal = CorusSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CorusHeaderIconButton(
                onClick = onBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.feed_cd_back),
                tint = iconTint,
            )
            // Title fades in as the bar collapses (invisible over the hero at rest).
            Text(
                text = title ?: "",
                style = CorusFont.screenTitle,
                color = CorusColors.Text.copy(alpha = progress),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = CorusSpacing.xs),
            )
            actions(iconTint)
        }
        // Hairline under the collapsed bar, dividing it from the list. Fades in.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(CorusColors.Divider.copy(alpha = progress)),
        )
    }
}
