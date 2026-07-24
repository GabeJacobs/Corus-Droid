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
    val featuredFilmBackgroundTop: Color,
    val featuredFilmBackgroundBottom: Color,
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
    featuredFilmBackgroundTop = Color(0xFFF3F3F3),
    featuredFilmBackgroundBottom = Color(0xFFBFBFBF),
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
    featuredFilmBackgroundTop = Color(0xFF333336),
    featuredFilmBackgroundBottom = Color(0xFF1F1F22),
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
    val DeezerPurple = Color(0xFFA238FF)
    val YouTubeMusicRed = Color(0xFFFF0000)
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

    val FeaturedFilmBackgroundTop: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.featuredFilmBackgroundTop

    val FeaturedFilmBackgroundBottom: Color
        @Composable @ReadOnlyComposable
        get() = LocalCorusPalette.current.featuredFilmBackgroundBottom

    /**
     * TIDAL's brand is monochrome — black on light, white on dark. Mirrors iOS
     * `cymbalTidal`, so the "Open in TIDAL" capsule matches across platforms.
     */
    val Tidal: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalCorusPalette.current.text == Color(0xFFF5F5F7)) Color.White else Color.Black

    /** Legible foreground on a filled [Tidal] capsule; inverts with the fill. */
    val TidalText: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalCorusPalette.current.text == Color(0xFFF5F5F7)) Color.Black else Color.White

    /** + button in the comment composer. White in dark, accent in light. */
    val CommentAttachmentPlus: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalCorusPalette.current.text == Color(0xFFF5F5F7)) Color.White else Accent

    /** Selected pill inside a segmented control sitting on [CardBackground]. */
    val SegmentedSelected: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalCorusPalette.current.text == Color(0xFFF5F5F7)) Color(0xFF3A3A3C) else Color.White
}
