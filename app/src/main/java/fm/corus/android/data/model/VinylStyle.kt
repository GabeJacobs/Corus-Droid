package fm.corus.android.data.model

import androidx.compose.ui.graphics.Color

enum class VinylStyle(val value: String) {
    BLACK("black"),
    CLEAR("clear"),
    RED_MATTE("redMatte"),
    PURPLE("purple"),
    WHITE("white"),
    GOLD("gold"),
    RED("red"),
    BLUE("blue"),
    GREEN("green");
    // TODO: style_pack_1 vinyl colors — add new entries here, return true from
    // `requiresStylePack1` for them, and add cases to every `when` below
    // (displayName, previewColor, label fractions). `from()` already falls back
    // to BLACK on unknown values, so old clients are safe.

    /// True for vinyl colors gated behind the `style_pack_1_enabled`
    /// Remote Config flag.
    val requiresStylePack1: Boolean
        get() = when (this) {
            BLACK, CLEAR, RED_MATTE, PURPLE, WHITE, GOLD, RED, BLUE, GREEN -> false
        }

    val displayName: String
        get() = when (this) {
            BLACK -> "Black"
            BLUE -> "Blue"
            WHITE -> "White"
            RED -> "Red"
            RED_MATTE -> "Red - Matte"
            CLEAR -> "Clear"
            PURPLE -> "Purple"
            GREEN -> "Green"
            GOLD -> "Gold"
        }

    val previewColor: Color
        get() = when (this) {
            BLACK -> Color(0xFF1A1A1A)
            BLUE -> Color(0xFF12368E)
            WHITE -> Color(0xFFE8E8E8)
            RED -> Color(0xFFFB0213)
            RED_MATTE -> Color(0xFFD24235)
            CLEAR -> Color(0xFFD0D0D0)
            PURPLE -> Color(0xFF591968)
            GREEN -> Color(0xFF2D8B4E)
            GOLD -> Color(0xFFC5A835)
        }

    // Vinyl label position fractions (from Figma)
    val labelXFrac: Float
        get() = when (this) {
            BLACK -> 330.77f / 585f
            BLUE -> 320f / 585f
            WHITE -> 325f / 585f
            RED -> 331f / 583f
            RED_MATTE -> 324f / 585f
            CLEAR -> 327f / 585f
            PURPLE -> 326f / 585f
            GREEN -> 322f / 585f
            GOLD -> 327f / 585f
        }

    val labelYFrac: Float
        get() = when (this) {
            BLACK -> 153.98f / 447f
            BLUE -> 151f / 447f
            WHITE -> 158f / 447f
            RED -> 154.5f / 447f
            RED_MATTE -> 150f / 447f
            CLEAR -> 154f / 447f
            PURPLE -> 153f / 447f
            GREEN -> 148f / 447f
            GOLD -> 149f / 447f
        }

    val labelWFrac: Float
        get() = when (this) {
            BLACK -> 82.44f / 585f
            BLUE -> 99f / 585f
            WHITE -> 87f / 585f
            RED -> 89f / 583f
            RED_MATTE -> 96f / 585f
            CLEAR -> 88f / 585f
            PURPLE -> 90f / 585f
            GREEN -> 98f / 585f
            GOLD -> 93f / 585f
        }

    val labelHFrac: Float
        get() = when (this) {
            BLACK -> 84.43f / 447f
            BLUE -> 101f / 447f
            WHITE -> 89f / 447f
            RED -> 91f / 447f
            RED_MATTE -> 98f / 447f
            CLEAR -> 90f / 447f
            PURPLE -> 92f / 447f
            GREEN -> 100f / 447f
            GOLD -> 95f / 447f
        }

    val canvasRatio: Float get() = 447f / 585f

    companion object {
        fun from(value: String?): VinylStyle =
            entries.firstOrNull { it.value == value } ?: BLACK
    }
}
