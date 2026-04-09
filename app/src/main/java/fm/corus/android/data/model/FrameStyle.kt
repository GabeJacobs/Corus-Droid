package fm.corus.android.data.model

import androidx.compose.ui.graphics.Color

enum class FrameStyle(val value: String) {
    BLACK("black"),
    WHITE("white"),
    RED("red"),
    BLUE("blue"),
    GREEN("green");

    val displayName: String
        get() = when (this) {
            BLACK -> "Black"
            WHITE -> "White"
            RED -> "Red"
            BLUE -> "Blue"
            GREEN -> "Green"
        }

    val previewColor: Color
        get() = when (this) {
            BLACK -> Color(0xFF333333)
            RED -> Color(0xFFCC0000).copy(alpha = 0.8f)
            WHITE -> Color(0xFFEBEBEB)
            BLUE -> Color(0xFF1D458D)
            GREEN -> Color(0xFF1F6F1D)
        }

    companion object {
        fun from(value: String?): FrameStyle =
            entries.firstOrNull { it.value == value } ?: BLACK
    }
}
