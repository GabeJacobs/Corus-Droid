package fm.corus.android.ui.components

import androidx.compose.ui.graphics.Color

/** Profile share-card colors — keep in lockstep with iOS ProfileSharePalette. */
data class ProfileSharePalette(
    val ink: Color,
    val muted: Color,
    val dim: Color,
    val accent: Color,
    val surface: Color,
    val backdrop: Color,
) {
    companion object {
        val LIGHT = ProfileSharePalette(
            ink = Color(0xFF1A1A2E),
            muted = Color(0xFF727276),
            dim = Color(0xFFC7C7CC),
            accent = Color(0xFF6495ED),
            surface = Color(0xFFF8F8FA),
            backdrop = Color.White,
        )

        val DARK = ProfileSharePalette(
            ink = Color(0xFFF5F5F7),
            muted = Color(0xFF9A9AA0),
            dim = Color(0xFF55555A),
            accent = Color(0xFF6495ED),
            surface = Color(0xFF1C1C1E),
            backdrop = Color.Black,
        )

        fun forTheme(theme: ShareCardTheme): ProfileSharePalette =
            if (theme == ShareCardTheme.LIGHT) LIGHT else DARK
    }
}
