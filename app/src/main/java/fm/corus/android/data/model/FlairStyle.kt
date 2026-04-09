package fm.corus.android.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.graphics.vector.ImageVector

enum class FlairStyle(val value: String) {
    NONE("none"),
    CHECKMARK("checkmark"),
    CORUS_LOGO("corusLogo"),
    VINYL("vinyl"),
    HEART("heart"),
    SPARKLE("sparkle"),
    MOON("moon"),
    HEADPHONES("headphones"),
    BOLT("bolt"),
    MUSIC_NOTE("musicNote"),
    PIANO("piano"),
    WAVEFORM("waveform");

    val displayName: String
        get() = when (this) {
            NONE -> "None"
            CHECKMARK -> "Checkmark"
            CORUS_LOGO -> "Corus"
            VINYL -> "Vinyl"
            HEART -> "Heart"
            SPARKLE -> "Sparkle"
            MOON -> "Moon"
            HEADPHONES -> "Headphones"
            BOLT -> "Lightning"
            MUSIC_NOTE -> "Music Note"
            PIANO -> "Piano"
            WAVEFORM -> "Waveform"
        }

    /** Material icon for this flair, null for NONE and CORUS_LOGO (uses asset image). */
    val icon: ImageVector?
        get() = when (this) {
            NONE -> null
            CHECKMARK -> Icons.Filled.Verified
            CORUS_LOGO -> null // uses logo_no_background drawable
            VINYL -> Icons.Filled.Album
            HEART -> Icons.Filled.Favorite
            SPARKLE -> Icons.Filled.AutoAwesome
            MOON -> Icons.Filled.DarkMode
            HEADPHONES -> Icons.Filled.Headphones
            BOLT -> Icons.Filled.Bolt
            MUSIC_NOTE -> Icons.Filled.MusicNote
            PIANO -> Icons.Filled.Piano
            WAVEFORM -> Icons.Filled.GraphicEq
        }

    /** Whether this flair uses the custom asset image (logo_no_background). */
    val usesAssetImage: Boolean get() = this == CORUS_LOGO

    /** Icon to show when no flair icon is available (for the "None" option). */
    val placeholderIcon: ImageVector get() = Icons.Filled.Remove

    companion object {
        fun from(value: String?): FlairStyle =
            entries.firstOrNull { it.value == value } ?: CHECKMARK
    }
}
