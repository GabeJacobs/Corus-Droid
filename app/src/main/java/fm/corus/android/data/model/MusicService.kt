package fm.corus.android.data.model

/**
 * Represents the user's preferred music streaming service.
 * Mirrors the iOS MusicService enum.
 */
enum class MusicService(val value: String) {
    SPOTIFY("spotify"),
    APPLE_MUSIC("appleMusic"),
    TIDAL("tidal");

    val displayLabel: String
        get() = when (this) {
            SPOTIFY -> "Spotify"
            APPLE_MUSIC -> "Apple Music"
            TIDAL -> "TIDAL"
        }

    companion object {
        fun fromValue(value: String): MusicService {
            return entries.find { it.value == value } ?: SPOTIFY
        }
    }
}
