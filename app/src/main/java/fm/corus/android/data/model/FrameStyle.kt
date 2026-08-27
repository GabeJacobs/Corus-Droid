package fm.corus.android.data.model

import androidx.compose.ui.graphics.Color

enum class FrameStyle(val value: String) {
    BLACK("black"),
    THEATER("theater"),
    WHITE("white"),
    RED("red"),
    BLUE("blue"),
    GREEN("green");
    // TODO: more style_pack_1 frame colors — add new entries here, return
    // true from `requiresStylePack1` for them, and add cases to every `when`
    // below. `from()` already falls back to BLACK on unknown values.

    /// True for frame colors gated behind the `style_pack_1_enabled`
    /// Remote Config flag.
    val requiresStylePack1: Boolean
        get() = when (this) {
            THEATER -> true
            BLACK, WHITE, RED, BLUE, GREEN -> false
        }

    val displayName: String
        get() = when (this) {
            BLACK -> "Black"
            WHITE -> "White"
            RED -> "Red"
            BLUE -> "Blue"
            GREEN -> "Green"
            THEATER -> "Marquee"
        }

    val previewColor: Color
        get() = when (this) {
            BLACK -> Color(0xFF333333)
            RED -> Color(0xFFCC0000).copy(alpha = 0.8f)
            WHITE -> Color(0xFFEBEBEB)
            BLUE -> Color(0xFF1D458D)
            GREEN -> Color(0xFF1F6F1D)
            THEATER -> Color(0xFF141414)
        }

    /// Glass overlay is authored for the original frames. Marquee's bulbs
    /// already carry their own glow.
    val usesGlassOverlay: Boolean get() = this != THEATER

    // Poster slot on the shared 585×482 canvas. Marquee is composited to the
    // same canvas height as the other frames; its opening is a bit different.
    val posterXFrac: Float
        get() = when (this) {
            THEATER -> 206f / 585f
            else -> 207.28f / 585f
        }

    val posterYFrac: Float
        get() = when (this) {
            THEATER -> 100f / 482f
            else -> 84.85f / 482f
        }

    val posterWFrac: Float
        get() = when (this) {
            THEATER -> 173f / 585f
            else -> 184.98f / 585f
        }

    val posterHFrac: Float
        get() = when (this) {
            THEATER -> 253f / 482f
            else -> 269.33f / 482f
        }

    /// Marquee uses a single sharp bulb on black in the picker instead of a flat color.
    val usesTextureSwatch: Boolean get() = this == THEATER

    /// Soft standing shadow under the frame silhouette. Stock frames already
    /// carry a baked glow; Marquee sits flat without one.
    val usesStandingShadow: Boolean get() = this == THEATER

    /// Extra canvas points below the PNG so the overlaid title row matches
    /// the clearance under the stock frames. Marquee's bulbs sit on the
    /// canvas edge; the others leave empty space there.
    val extraTitleClearance: Float
        get() = when (this) {
            THEATER -> 32f
            else -> 0f
        }

    /// Featured-section aspect, including any title clearance below the PNG.
    val featuredSectionAspect: Float
        get() = CANVAS_WIDTH / (CANVAS_HEIGHT + extraTitleClearance)

    companion object {
        const val CANVAS_WIDTH = 585f
        const val CANVAS_HEIGHT = 482f

        fun from(value: String?): FrameStyle =
            entries.firstOrNull { it.value == value } ?: BLACK
    }
}
