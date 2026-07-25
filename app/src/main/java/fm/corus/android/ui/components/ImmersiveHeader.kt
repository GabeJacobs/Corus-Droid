package fm.corus.android.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
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
import dev.chrisbanes.haze.hazeSource
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

/**
 * Whether the tab hosting the current composition is the one on screen. Every tab
 * NavHost stays composed at once — `MainTabScreen` keeps the inactive ones
 * off-screen (offset), not disposed — so an immersive page on a *background* tab is
 * still in composition and must stop driving the shared, window-wide status-bar
 * appearance. `MainTabScreen`'s `TabContent` provides this per tab; it defaults to
 * true so an immersive screen shown outside the tab host (previews, tests) behaves
 * normally.
 */
val LocalContainingTabSelected = compositionLocalOf { true }

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
    // Prefer the Activity window's decorView: it's the true window root and always
    // carries the real insets. `LocalView.current` can be a nested ComposeView whose
    // `getRootWindowInsets` reports 0 (seen on the profile feed), which silently
    // disabled the status-bar blend. The view's context is usually a ContextWrapper
    // (ContextThemeWrapper), not the Activity directly, so unwrap it to find the
    // Activity before reaching decorView; fall back to the local view.
    var ctx: Context? = view.context
    while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
    val root = (ctx as? Activity)?.window?.decorView ?: view
    val insets = ViewCompat.getRootWindowInsets(root) ?: ViewCompat.getRootWindowInsets(view)
    return insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
}

/**
 * Per-screen plumbing for the no-hero immersive frosted bar (profile feed,
 * other-profile, post detail, …): the Haze state, the status-bar extend, and the
 * content top-padding, derived once so each screen doesn't repeat them. Pair with
 * [ImmersiveFrostedBar] for the bar itself, and remember to (1) set the Scaffold's
 * `contentWindowInsets = WindowInsets(0,0,0,0)` and drop its `topBar` while
 * immersive, and (2) add the route to `MainTabScreen`'s status-strip-cover
 * allow-list so the solid cover doesn't paint over the frosted strip.
 */
@Composable
internal fun rememberImmersiveHeaderState(immersive: Boolean): ImmersiveHeaderState {
    val hazeState = remember { HazeState() }
    val statusBarTopPx = currentStatusBarTopPx()
    val extend = immersive && ImmersiveExtendUnderStatusBar && statusBarTopPx > 0
    val statusBarPadding = if (extend) {
        with(LocalDensity.current) { statusBarTopPx.toDp() }
    } else {
        0.dp
    }
    return ImmersiveHeaderState(immersive, hazeState, statusBarTopPx, extend, statusBarPadding)
}

internal class ImmersiveHeaderState(
    val immersive: Boolean,
    val hazeState: HazeState,
    private val statusBarTopPx: Int,
    val extendUnderStatusBar: Boolean,
    /** Height of the status strip the bar covers (0 when not blending it). */
    val statusBarPadding: Dp,
) {
    /** Grows the screen's Scaffold up under the status strip (no-op when off). */
    val scaffoldModifier: Modifier
        get() = if (extendUnderStatusBar) Modifier.extendIntoStatusBar(statusBarTopPx) else Modifier

    /** Top contentPadding so a scrollable's first item clears the frosted bar. */
    val contentTopPadding: Dp
        get() = statusBarPadding + ImmersiveBarHeight

    /** Marks a scrollable as the frost's blur source (no-op when not immersive). */
    fun hazeSourceModifier(): Modifier =
        if (immersive) Modifier.hazeSource(hazeState) else Modifier
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

/** The frosted bottom bar's height, published by MainTabScreen so scrollable screens
 *  can clear it (add to `contentPadding` bottom) while their content still scrolls
 *  under it. 0.dp when the frosted bottom bar is off. */
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

/** HazeState the shared bottom bar (in MainTabScreen) blurs. Screens publish their
 *  scrollable into it via [contentHazeSource] so the bar frosts whatever scrolls
 *  under it — WITHOUT a wrapping source around each screen (which would corrupt the
 *  screens' own top-strip haze). null when the frosted bottom bar is off. */
val LocalContentHaze = compositionLocalOf<HazeState?> { null }

/**
 * Marks a scrollable as the source the shared frosted bottom bar blurs. Apply
 * ALONGSIDE the screen's own [ImmersiveHeaderState.hazeSourceModifier] on the SAME
 * scrollable node (siblings of the top strip, so no source ever wraps an effect).
 * No-op when [LocalContentHaze] is null (bottom bar off).
 */
@Composable
fun Modifier.contentHazeSource(): Modifier {
    val state = LocalContentHaze.current ?: return this
    return this.hazeSource(state)
}

/**
 * Bottom-side mirror of [extendIntoStatusBar]: grows a node DOWN by [extraBottomPx]
 * (so its scrollable content draws under the frosted bottom bar) while still
 * reporting its original size, so nothing else in the layout moves. No-op when
 * [extraBottomPx] <= 0.
 */
internal fun Modifier.extendIntoBottomBar(extraBottomPx: Int): Modifier =
    if (extraBottomPx <= 0) {
        this
    } else {
        layout { measurable, constraints ->
            val grownMax = if (constraints.hasBoundedHeight) {
                constraints.maxHeight + extraBottomPx
            } else {
                constraints.maxHeight
            }
            val placeable = measurable.measure(constraints.copy(maxHeight = grownMax))
            val reportedHeight = (placeable.height - extraBottomPx).coerceAtLeast(0)
            layout(placeable.width, reportedHeight) {
                placeable.place(0, 0)
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
    // The status bar is one window-wide resource, but every tab NavHost stays
    // composed at once (inactive tabs are parked off-screen, not disposed). So this
    // override must yield the bar the instant its own tab is deselected — otherwise
    // the white-over-hero icons stay forced onto the light Feed/Profile the user
    // switches to (white-on-white, invisible). While inactive we make NO write at
    // all; MainTabScreen re-asserts the theme default on every tab switch, which
    // both restores a non-immersive arriving tab and keeps two immersive tabs from
    // racing over the bar. Re-keying on `active` re-applies our override the moment
    // this tab is selected again (the LaunchedEffect below won't fire on its own —
    // its appearance key is unchanged across the switch).
    val active = LocalContainingTabSelected.current
    // false → white icons over the dark hero; !darkTheme → theme default once the
    // frosted bar has taken over (collapseProgress >= 0.5).
    val appearanceLightStatusBars = if (collapseProgress < 0.5f) false else !darkTheme
    LaunchedEffect(active, appearanceLightStatusBars, view) {
        if (!active) return@LaunchedEffect
        (view.context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView)
                .isAppearanceLightStatusBars = appearanceLightStatusBars
        }
    }
    // Restore the theme default when the immersive screen leaves composition by
    // navigating BACK WITHIN its own tab: the popped page IS disposed while its tab
    // stays selected (so the tab-switch reset above doesn't apply), and the restored
    // feed/profile entry re-enters without re-asserting — CorusSystemBars only writes
    // from a first-composition SideEffect, which a restored back-stack entry skips.
    // Capture the controller EAGERLY (while the view is still attached), bound to the
    // window's decorView, which stays attached through the pop transition. A
    // controller built lazily inside onDispose — as this screen's ComposeView is
    // detaching — silently dropped its write, leaving the bar stuck on white.
    DisposableEffect(view, darkTheme) {
        val controller = (view.context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView)
        }
        onDispose {
            controller?.isAppearanceLightStatusBars = !darkTheme
        }
    }
}

/**
 * Smoothstep-eased stops for one leg of a vertical scrim: [color] ramping from
 * [fromAlpha] to [toAlpha] across positions [start]→[end], sampled as [samples] + 1
 * stops along smoothstep (S(t) = 3t² − 2t³) so the alpha curve has zero slope at
 * both ends. A plain two-stop linear ramp kinks into a visible "line" where it lifts
 * off and where it tops out — most of all over dark imagery on displays that don't
 * dither the gradient (Compose does not) — and easing removes both knees. The RGB of
 * [color] is held constant across the ramp (only alpha moves), so the fade never
 * crosses through a muddy grey the way a `Transparent`→opaque-color pair does.
 */
internal fun easedScrimStops(
    color: Color,
    start: Float,
    end: Float,
    fromAlpha: Float,
    toAlpha: Float,
    samples: Int = 8,
): List<Pair<Float, Color>> = (0..samples).map { i ->
    val t = i / samples.toFloat()
    val eased = t * t * (3f - 2f * t)
    val pos = start + (end - start) * t
    pos to color.copy(alpha = fromAlpha + (toAlpha - fromAlpha) * eased)
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
                    // Two eased legs: a soft top darkening for white status-icon
                    // legibility, then a WIDE smoothstep fade into the app background so
                    // the hero melts into the list with no visible line — even under a
                    // dark subject. (Was a narrow 0.4→0.75 linear ramp, which kinked.)
                    Brush.verticalGradient(
                        *(
                            easedScrimStops(Color.Black, 0f, 0.28f, 0.22f, 0f, samples = 4) +
                                easedScrimStops(CorusColors.Background, 0.30f, 0.92f, 0f, 1f, samples = 10) +
                                listOf(1f to CorusColors.Background)
                            ).toTypedArray(),
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
        // No bottom hairline: the frost + blur already separate the bar from the
        // list, and a 0.5dp divider read as a stray near-white line on light pages.
    }
}

/**
 * Always-on frosted top bar for screens with NO hero (e.g. the profile feed): a
 * fixed frosted-glass nav bar that blurs the list scrolling beneath it (Haze),
 * with the title always shown. Unlike [ImmersiveCollapsingBar] the frost is
 * UNIFORM across the whole bar — the status strip ([topInset]) included — so the
 * status bar reads as one continuous piece of the nav bar rather than a separate
 * opaque band. There's no hero to reveal over, so no transparent rest state and
 * no collapse: the same frosted glass, top to bottom, at every scroll position.
 */
@Composable
internal fun ImmersiveFrostedBar(
    hazeState: HazeState,
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val frost = CorusColors.Background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ImmersiveBarHeight + topInset)
            // One uniform frost over the FULL height — status strip and nav row
            // alike — so the status bar is the same frosted glass as the bar below
            // it, not a separate solid band. `blur` is a no-op below Android 12,
            // where Haze falls back to a translucent scrim of the same tint.
            .hazeEffect(state = hazeState) {
                blurRadius = 30.dp
                backgroundColor = frost
                // `alpha` is THE frostiness knob: higher = whiter/more opaque, lower
                // = more see-through.
                tints = listOf(HazeTint(frost.copy(alpha = 0.8f)))
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                // Push the controls below the status strip that the bar covers.
                .padding(top = topInset)
                .padding(horizontal = CorusSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CorusHeaderIconButton(
                onClick = onBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.feed_cd_back),
                tint = CorusColors.Text,
            )
            Text(
                text = title ?: "",
                style = CorusFont.screenTitle,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = CorusSpacing.xs),
            )
            actions()
        }
    }
}

/**
 * A no-controls frosted glass strip covering JUST the status bar, for the tab-root
 * screens (feed, own profile) whose own header ("corus" switcher / settings row) is
 * a scrolling list item rather than a fixed bar. Content scrolls under it and it
 * blurs whatever passes beneath (Haze). Pair with: the screen extending up via
 * [rememberImmersiveHeaderState].scaffoldModifier, [hazeSourceModifier] on the
 * scrollable, and a top clearance of [ImmersiveHeaderState.statusBarPadding] on the
 * first list item so the header sits just below the strip. No-op when [topInset] is 0.
 */
@Composable
internal fun FrostedStatusStrip(
    hazeState: HazeState,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    if (topInset <= 0.dp) return
    val frost = CorusColors.Background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topInset)
            .hazeEffect(state = hazeState) {
                blurRadius = 30.dp
                backgroundColor = frost
                // Match ImmersiveFrostedBar's frostiness.
                tints = listOf(HazeTint(frost.copy(alpha = 0.8f)))
            },
    )
}

/**
 * Frosted overlay wrapping an arbitrary FIXED header ([content]) — for tab roots
 * whose header is NOT a scrolling list item (e.g. Activity). Covers the status strip
 * ([topInset]) + the header and blurs the list scrolling beneath it (Haze). When
 * [immersive] is false it degrades to a plain solid-background header so content still
 * reads under it. Measure this via `Modifier.onGloballyPositioned` to size the list's
 * top clearance (there's no fixed height — the header sizes to its content).
 */
@Composable
internal fun FrostedHeaderOverlay(
    immersive: Boolean,
    hazeState: HazeState,
    topInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val frost = CorusColors.Background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (immersive) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurRadius = 30.dp
                        backgroundColor = frost
                        tints = listOf(HazeTint(frost.copy(alpha = 0.8f)))
                    }
                } else {
                    Modifier.background(frost)
                },
            )
            .padding(top = if (immersive) topInset else 0.dp),
    ) {
        content()
    }
}
