package fm.corus.android.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.ui.theme.CorusColors

// ─────────────────────────────────────────────────────────────────────────────
// Keyframe engine — a faithful port of the web's corus-venn-* CSS keyframes
// (app/globals.css). One master clock loops 0→1 over 7.5s; every element
// evaluates piecewise tracks against it, easing each segment with CSS
// ease-in-out (cubic-bezier 0.42,0,0.58,1), exactly like
// `animation: … ease-in-out infinite`.
// ─────────────────────────────────────────────────────────────────────────────

private val CssEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * A piecewise keyframe track: `stops` are (fraction, value) pairs sorted by
 * fraction. Values between stops interpolate with CSS ease-in-out; outside the
 * range the boundary value holds (CSS fill-mode both). Pure — unit-tested in
 * VennKeyframeTrackTest.
 */
internal class KeyframeTrack(private vararg val stops: Pair<Float, Float>) {
    fun at(fraction: Float): Float {
        if (stops.isEmpty()) return 0f
        // Strict < so a fraction landing exactly on a duplicated stop position
        // resolves through the segment scan (later keyframe wins, like CSS).
        if (fraction < stops.first().first) return stops.first().second
        if (fraction >= stops.last().first) return stops.last().second
        for (i in 1 until stops.size) {
            val (endAt, endValue) = stops[i]
            if (fraction <= endAt) {
                val (startAt, startValue) = stops[i - 1]
                val span = endAt - startAt
                if (span <= 0f) return endValue
                val t = CssEaseInOut.transform((fraction - startAt) / span)
                return startValue + (endValue - startValue) * t
            }
        }
        return stops.last().second
    }
}

/**
 * CSS animation-delay on an infinite loop: every iteration is offset by the
 * delay, so the per-element stagger holds on each pass. Wraps the master
 * fraction backwards by [delayFraction]. Pure — unit-tested.
 *
 * [wrap] = false is the FIRST cycle: CSS holds a delayed element at its
 * from-state until the delay elapses (fill backwards), so we clamp to 0
 * instead of wrapping — wrapping on cycle one renders the element's
 * END-of-loop state on the opening frames (the match avatars appeared
 * pre-settled in the lens before the circles ever collided).
 */
internal fun staggeredFraction(master: Float, delayFraction: Float, wrap: Boolean = true): Float {
    if (!wrap) return (master - delayFraction).coerceAtLeast(0f)
    val f = (master - delayFraction) % 1f
    return if (f < 0f) f + 1f else f
}

// ─────────────────────────────────────────────────────────────────────────────
// Geometry + choreography constants (straight from the web implementation)
// ─────────────────────────────────────────────────────────────────────────────

private const val LOOP_MS = 7500

/** Container 356x192; circles 170 at top 11 (left-0 / right-0). Slide = ±50 →
 *  lens center (178, 96). */
private const val CONTAINER_W = 356f
private const val CONTAINER_H = 192f
private const val CIRCLE = 170f
private const val CIRCLE_TOP = 11f
private const val SLIDE = 50f

/** Group-local resting spots for the "you" covers + travel vector to the lens
 *  center (computed against the group's POST-slide position) + resting tilt. */
private data class ArtSpot(
    val left: Float, val top: Float, val w: Float, val h: Float,
    val cx: Float, val cy: Float, val fan: Float,
)

private val ART_SPOTS = listOf(
    ArtSpot(left = 18f, top = 44f, w = 52f, h = 52f, cx = 84f, cy = 15f, fan = -8f),
    ArtSpot(left = 58f, top = 70f, w = 52f, h = 52f, cx = 44f, cy = -11f, fan = 5f),
    ArtSpot(left = 102f, top = 46f, w = 44f, h = 60f, cx = 4f, cy = 9f, fan = -4f),
)

private data class SwirlSpot(val left: Float, val top: Float, val cx: Float, val cy: Float)

private val SWIRL_SPOTS = listOf(
    SwirlSpot(left = 52f, top = 28f, cx = -32f, cy = 35f),
    SwirlSpot(left = 98f, top = 64f, cx = -78f, cy = -1f),
    SwirlSpot(left = 58f, top = 100f, cx = -38f, cy = -37f),
)

/** Container-space resting spots INSIDE the lens for the match reveal. */
private data class MatchSpot(val left: Float, val top: Float, val size: Float)

private val MATCH_SPOTS = listOf(
    MatchSpot(left = 150f, top = 54f, size = 44f),
    MatchSpot(left = 164f, top = 78f, size = 44f),
    MatchSpot(left = 150f, top = 98f, size = 36f),
)

/** Curated intro art — iconic covers that actually live on Corus, not the
 *  trending pool. Spares swap in on load error if a CDN image ever rots.
 *  Slots: two album covers + one film poster (music AND film). Same URLs as
 *  web VENN_INTRO_ART. */
private val VENN_INTRO_ART: List<List<String>> = listOf(
    listOf(
        // Frank Ocean — Blonde
        "https://i.scdn.co/image/ab67616d0000b273c5649add07ed3720be9d5526",
        // David Bowie
        "https://i.scdn.co/image/ab67616d0000b273481e219b2d0a0218681ad612",
        // Kendrick Lamar
        "https://i.scdn.co/image/ab67616d0000b2739b035b031d9f0a6a75ae464e",
    ),
    listOf(
        // David Bowie — Aladdin Sane (lightning-bolt face, front and center)
        "https://i.scdn.co/image/ab67616d0000b2735db6dbaca8678527e643a866",
        // MF DOOM
        "https://i.scdn.co/image/ab67616d0000b273810322d575e5a2ea235671f9",
        // Radiohead
        "https://i.scdn.co/image/ab67616d0000b27345643f5cf119cbc9d2811c22",
    ),
    listOf(
        // Parasite (poster slot)
        "https://image.tmdb.org/t/p/w342/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg",
        // Pulp Fiction
        "https://image.tmdb.org/t/p/w342/vQWk5YBFWF4bZaofAbv0tShwBvQ.jpg",
        // In the Mood for Love
        "https://image.tmdb.org/t/p/w342/iYypPT4bhqXfq1b6EnmxvRt6b2Y.jpg",
    ),
)

/** Every intro-art URL (primaries + error spares), exposed so the onboarding
 *  flow can warm Coil's cache a screen EARLY — grey placeholder circles
 *  resolving mid-choreography ruin the collision animation's reveal. */
internal val vennIntroArtUrls: List<String> = VENN_INTRO_ART.flatten()

// Keyframe tracks (fractions of the 7.5s loop — see globals.css comments).

private val LOBE_X = KeyframeTrack(0f to 0f, 0.16f to 0f, 0.32f to 1f, 1f to 1f)
private val LOBE_ALPHA = KeyframeTrack(0f to 1f, 0.92f to 1f, 1f to 0f)

private val COVER_ALPHA = KeyframeTrack(0f to 0f, 0.09f to 1f, 0.36f to 1f, 0.46f to 0f, 1f to 0f)
// Resting choreography only (0–36%); the 36→46% dissolve toward the lens is a
// per-element travel vector, applied by lensTravel below.
private val COVER_TX = KeyframeTrack(0f to -26f, 0.09f to 0f, 0.34f to 1f, 0.36f to 0f)
private val COVER_TY = KeyframeTrack(0f to 10f, 0.09f to 0f, 0.34f to -2f, 0.36f to 0f)
private val COVER_SCALE = KeyframeTrack(0f to 0.55f, 0.09f to 1f, 0.36f to 1f, 0.46f to 0.3f, 1f to 0.3f)

private val SWIRL_ALPHA = KeyframeTrack(0f to 0f, 0.08f to 1f, 0.36f to 1f, 0.46f to 0f, 1f to 0f)
private val SWIRL_SCALE = KeyframeTrack(0f to 0.5f, 0.08f to 1f, 0.36f to 1f, 0.46f to 0.3f, 1f to 0.3f)

private val BREW_ALPHA = KeyframeTrack(0f to 0.08f, 0.34f to 0.08f, 0.44f to 0.95f, 0.56f to 0.5f, 0.88f to 0.35f, 1f to 0f)
private val BREW_SCALE = KeyframeTrack(0f to 0.8f, 0.34f to 0.8f, 0.44f to 1.4f, 0.56f to 1.05f, 0.88f to 1f, 1f to 0.75f)

private val MATCH_ALPHA = KeyframeTrack(0f to 0f, 0.48f to 0f, 0.56f to 1f, 0.9f to 1f, 1f to 0f)
private val MATCH_TY = KeyframeTrack(0f to 6f, 0.48f to 6f, 0.56f to 0f, 0.8f to -3f, 0.9f to 0f, 1f to 0f)
private val MATCH_SCALE = KeyframeTrack(0f to 0f, 0.48f to 0f, 0.56f to 1.15f, 0.61f to 1f, 0.9f to 1f, 1f to 0.85f)

/** Reads the system animator scale: 0 = the user disabled animations
 *  (reduced motion). Mirrors the web's prefers-reduced-motion pin. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * The taste-match brand moment: the venn "collision" loop shared by the
 * onboarding intro and the "finding your matches" interstitial. Two separate
 * worlds meet and the intersection is born on screen: covers pop into the
 * "you" circle while community avatars swirl in the other, the circles slide
 * together to form the venn, both sides dissolve into the newborn lens's glow,
 * and DIFFERENT avatars — your people, not the faces that swirled — settle
 * inside the oval. 7.5s loop, five beats; a faithful port of web's
 * VennIntroAnimation + the corus-venn-* keyframes.
 *
 * @param art Custom cover art (the interstitial passes the user's actual quiz
 *   picks); empty = the curated intro covers. With fewer than 3 the last one
 *   fills the remaining slots.
 * @param avatars Up to 6 community avatar URLs: the first 3 swirl, the next 3
 *   settle in the overlap (deliberately NOT the same faces).
 * @param shimmerPlaceholders Shimmer avatar placeholders while real ones load
 *   (the interstitial's "still searching" read).
 */
@Composable
fun VennCollisionAnimation(
    modifier: Modifier = Modifier,
    art: List<String> = emptyList(),
    // Parallel to [art]: true = 2:3 poster art (films), false = square (album
    // covers). The third curated slot is poster-SHAPED for the curated film,
    // but a user's own picks land in slots by order — a square album cover
    // stuffed into the poster frame renders visibly cropped. Ignored when
    // [art] is empty (curated slots keep their designed shapes).
    artIsPoster: List<Boolean> = emptyList(),
    avatars: List<String> = emptyList(),
    shimmerPlaceholders: Boolean = false,
    // Hold the opening frame this long before the loop starts — the intro
    // passes the step-transition duration so the collision's first beat
    // isn't swallowed mid-transition.
    startDelayMs: Long = 0L,
) {
    val reducedMotion = rememberReducedMotion()
    // Animatable (not rememberInfiniteTransition) so the pre-delay holds the
    // exact opening frame; an infinite transition starts advancing at
    // composition and would clip the first startDelayMs of beat one.
    val masterAnim = remember { Animatable(0f) }
    // False until one full loop has run. Cycle one clamps the per-element
    // stagger (CSS fill-backwards semantics); later cycles wrap so delayed
    // elements finish their tails across the loop boundary.
    var firstCycleDone by remember { mutableStateOf(false) }
    if (!reducedMotion) {
        LaunchedEffect(Unit) {
            if (startDelayMs > 0) delay(startDelayMs)
            masterAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(LOOP_MS, easing = LinearEasing),
            )
            masterAnim.snapTo(0f)
            firstCycleDone = true
            masterAnim.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(LOOP_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        }
    }

    // Per-slot fallback index into the curated art pools (custom art has no spares).
    val artFailures = remember { mutableStateListOf(0, 0, 0) }
    val swirlAvatars = avatars.take(3)
    val matchAvatars = avatars.drop(3).take(3)

    Box(modifier = modifier.size(CONTAINER_W.dp, CONTAINER_H.dp)) {
        val p = if (reducedMotion) null else masterAnim.value

        // ── Left group: the "you" circle + covers ──
        Box(
            modifier = Modifier
                .offset(0.dp, CIRCLE_TOP.dp)
                .size(CIRCLE.dp)
                .graphicsLayer {
                    if (p != null) {
                        translationX = SLIDE.dp.toPx() * LOBE_X.at(p)
                        alpha = LOBE_ALPHA.at(p)
                    }
                },
        ) {
            VennCircleOutline()
            ART_SPOTS.forEachIndexed { i, spot ->
                val pool = if (art.isNotEmpty()) {
                    listOf(art[minOf(i, art.size - 1)])
                } else {
                    VENN_INTRO_ART[i]
                }
                val src = pool[minOf(artFailures[i], pool.size - 1)]
                // Custom picks size by their OWN media shape; curated art
                // keeps each slot's designed frame (slot 3 = the film poster).
                val poster = if (art.isNotEmpty()) artIsPoster.getOrNull(i) == true else spot.h != spot.w
                val slotW = if (poster) 44f else 52f
                val slotH = if (poster) 60f else 52f
                val ep = p?.let { staggeredFraction(it, i * 0.15f * 1000f / LOOP_MS, wrap = firstCycleDone) }
                Box(
                    modifier = Modifier
                        .offset(spot.left.dp, spot.top.dp)
                        .size(slotW.dp, slotH.dp)
                        .graphicsLayer {
                            if (ep != null) {
                                alpha = COVER_ALPHA.at(ep)
                                translationX = coverTravelX(ep, spot.cx).dp.toPx()
                                translationY = coverTravelY(ep, spot.cy).dp.toPx()
                                rotationZ = coverRotation(ep, spot.fan)
                                val s = COVER_SCALE.at(ep)
                                scaleX = s
                                scaleY = s
                            } else {
                                rotationZ = spot.fan
                            }
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .border(2.dp, CorusColors.Background, RoundedCornerShape(8.dp)),
                ) {
                    AsyncImage(
                        model = src,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = {
                            if (artFailures[i] < pool.size - 1) artFailures[i] = artFailures[i] + 1
                        },
                    )
                }
            }
        }

        // ── Right group: the community circle + swirling avatars ──
        Box(
            modifier = Modifier
                .offset((CONTAINER_W - CIRCLE).dp, CIRCLE_TOP.dp)
                .size(CIRCLE.dp)
                .graphicsLayer {
                    if (p != null) {
                        translationX = -SLIDE.dp.toPx() * LOBE_X.at(p)
                        alpha = LOBE_ALPHA.at(p)
                    }
                },
        ) {
            VennCircleOutline()
            SWIRL_SPOTS.forEachIndexed { i, spot ->
                val ep = p?.let { staggeredFraction(it, i * 0.12f * 1000f / LOOP_MS, wrap = firstCycleDone) }
                VennAvatar(
                    url = swirlAvatars.getOrNull(i),
                    size = 44f,
                    shimmer = shimmerPlaceholders,
                    modifier = Modifier
                        .offset(spot.left.dp, spot.top.dp)
                        .graphicsLayer {
                            if (ep != null) {
                                alpha = SWIRL_ALPHA.at(ep)
                                translationX = swirlTravelX(ep, spot.cx).dp.toPx()
                                translationY = swirlTravelY(ep, spot.cy).dp.toPx()
                                val s = SWIRL_SCALE.at(ep)
                                scaleX = s
                                scaleY = s
                            }
                        },
                )
            }
        }

        // ── The newborn lens: glow blooms as both sides dissolve into it ──
        // (Radial-gradient glow instead of web's blur-xl — RenderEffect blur
        // needs API 31+, the gradient reads identically on every device.)
        if (p != null) {
            Box(
                modifier = Modifier
                    .offset((178 - 56).dp, (96 - 56).dp)
                    .size(112.dp)
                    .graphicsLayer {
                        alpha = BREW_ALPHA.at(p)
                        val s = BREW_SCALE.at(p)
                        scaleX = s
                        scaleY = s
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                CorusColors.Accent.copy(alpha = 0.85f),
                                CorusColors.Accent.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .offset(170.dp, 88.dp)
                    .size(16.dp)
                    .graphicsLayer {
                        alpha = BREW_ALPHA.at(p)
                        val s = BREW_SCALE.at(p)
                        scaleX = s
                        scaleY = s
                    }
                    .background(CorusColors.Accent, CircleShape),
            )
        }

        // ── The match: fresh faces settle inside the oval ──
        if (p != null) {
            MATCH_SPOTS.forEachIndexed { i, spot ->
                val ep = staggeredFraction(p, i * 0.18f * 1000f / LOOP_MS, wrap = firstCycleDone)
                VennAvatar(
                    url = matchAvatars.getOrNull(i),
                    size = spot.size,
                    shimmer = shimmerPlaceholders,
                    modifier = Modifier
                        .offset(spot.left.dp, spot.top.dp)
                        .graphicsLayer {
                            alpha = MATCH_ALPHA.at(ep)
                            translationY = MATCH_TY.at(ep).dp.toPx()
                            val s = MATCH_SCALE.at(ep)
                            scaleX = s
                            scaleY = s
                        },
                )
            }
        }
    }
}

// Travel helpers: resting choreography until 36%, then fly along the per-slot
// (cx, cy) vector into the lens (CSS keyframes hit the vector at 46% and hold).

private fun coverTravelX(f: Float, cx: Float): Float =
    if (f < 0.36f) COVER_TX.at(f) else lensTravel(f, cx)

private fun coverTravelY(f: Float, cy: Float): Float =
    if (f < 0.36f) COVER_TY.at(f) else lensTravel(f, cy)

private val SWIRL_TX = KeyframeTrack(0f to 0f, 0.08f to 0f, 0.17f to 6f, 0.25f to 1f, 0.33f to -6f, 0.36f to 0f)
private val SWIRL_TY = KeyframeTrack(0f to 0f, 0.08f to 0f, 0.17f to -5f, 0.25f to -9f, 0.33f to -4f, 0.36f to 0f)

private fun swirlTravelX(f: Float, cx: Float): Float =
    if (f < 0.36f) SWIRL_TX.at(f) else lensTravel(f, cx)

private fun swirlTravelY(f: Float, cy: Float): Float =
    if (f < 0.36f) SWIRL_TY.at(f) else lensTravel(f, cy)

private fun lensTravel(f: Float, target: Float): Float =
    if (f >= 0.46f) target
    else target * CssEaseInOut.transform((f - 0.36f) / 0.10f)

private fun coverRotation(f: Float, fan: Float): Float = when {
    f <= 0f -> -15f
    f < 0.09f -> -15f + (fan + 15f) * CssEaseInOut.transform(f / 0.09f)
    f < 0.36f -> fan
    f < 0.46f -> fan * (1f - CssEaseInOut.transform((f - 0.36f) / 0.10f))
    else -> 0f
}

@Composable
private fun VennCircleOutline() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(3.dp, CorusColors.Accent.copy(alpha = 0.7f), CircleShape)
            .background(CorusColors.Accent.copy(alpha = 0.05f), CircleShape),
    )
}

@Composable
private fun VennAvatar(
    url: String?,
    size: Float,
    shimmer: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .border(2.dp, CorusColors.Background, CircleShape),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (shimmer) Modifier.shimmer() else Modifier)
                    .background(CorusColors.Skeleton, CircleShape),
            )
        }
    }
}
