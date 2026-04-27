package fm.corus.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Adaptive (light/dark) color palette. Brand colors live on [CorusColors] directly. */
data class CorusColorPalette(
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val cardBackground: Color,
    val skeleton: Color,
    val divider: Color,
    val featuredBackgroundTop: Color,
    val featuredBackgroundBottom: Color,
)

val LightCorusPalette = CorusColorPalette(
    text = Color(0xFF1A1A2E),
    secondary = Color(0xFF8E8E93),
    tertiary = Color(0xFFC7C7CC),
    background = Color.White,
    cardBackground = Color(0xFFF8F8FA),
    skeleton = Color(0xFFE8E8ED),
    divider = Color(0xFFEEEEF0),
    featuredBackgroundTop = Color(0xFFF3F3F3),
    featuredBackgroundBottom = Color(0xFFBFBFBF),
)

val DarkCorusPalette = CorusColorPalette(
    text = Color(0xFFF5F5F7),
    secondary = Color(0xFF9A9AA0),
    tertiary = Color(0xFF55555A),
    background = Color(0xFF000000),
    cardBackground = Color(0xFF1C1C1E),
    skeleton = Color(0xFF2C2C2E),
    divider = Color(0xFF2C2C2E),
    featuredBackgroundTop = Color(0xFF1F1F22),
    featuredBackgroundBottom = Color(0xFF0C0C0E),
)

val LocalCorusPalette = staticCompositionLocalOf { LightCorusPalette }

object CorusColors {
    // ── Brand colors (mode-independent) ──
    val Accent = Color(0xFF6495ED)
    val Club = Color(0xFF6495ED)
    val Like = Color(0xFFE84393)
    val Verified = Color(0xFF2ED573)
    val SpotifyGreen = Color(0xFF1DB954)
    val AppleMusicPink = Color(0xFFFC3C44)
    val Error = Color(0xFFE74C3C)

    // ── Adaptive colors (resolved from LocalCorusPalette at composition time) ──
    val Text: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.text

    val Secondary: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.secondary

    val Tertiary: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.tertiary

    val Background: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.background

    val CardBackground: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.cardBackground

    val Skeleton: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.skeleton

    val Divider: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.divider

    val FeaturedBackgroundTop: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.featuredBackgroundTop

    val FeaturedBackgroundBottom: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.featuredBackgroundBottom
}
