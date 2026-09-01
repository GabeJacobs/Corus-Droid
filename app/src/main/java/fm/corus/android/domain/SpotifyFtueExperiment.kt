package fm.corus.android.domain

import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.MusicService
import fm.corus.android.service.AnalyticsService

enum class SpotifyFtueVariant(val value: String) {
    OFF("off"),
    A("a"),
    B("b"),
    INELIGIBLE("ineligible");

    companion object {
        fun fromRaw(raw: String?): SpotifyFtueVariant? {
            if (raw.isNullOrBlank()) return null
            return entries.find { it.value == raw }
        }
    }
}

data class SpotifyFtueAssignment(
    val variant: SpotifyFtueVariant,
    val alwaysPlayFullSongs: Boolean,
)

enum class SpotifyFtuePromptKind {
    LINK_SPOTIFY,
    CHOOSE_LISTEN,
}

object SpotifyFtuePromptSurface {
    const val FIRST_PLAY = "first_play"
    const val MINIPLAYER_FULL = "miniplayer_full"
    const val SETTINGS_ALWAYS_FULL = "settings_always_full"
}

/** New-user Spotify playback FTUE. Android does not support in-app Apple full. */
object SpotifyFtueExperiment {
    const val SUPPORTS_APPLE_FULL_PLAYBACK = false

    fun assignment(
        service: MusicService,
        spotifyInstalled: Boolean,
        rcVariant: String,
        supportsAppleFullPlayback: Boolean = SUPPORTS_APPLE_FULL_PLAYBACK,
    ): SpotifyFtueAssignment {
        return when (service) {
            MusicService.APPLE_MUSIC -> SpotifyFtueAssignment(
                variant = SpotifyFtueVariant.INELIGIBLE,
                alwaysPlayFullSongs = supportsAppleFullPlayback,
            )
            MusicService.SPOTIFY -> {
                if (!spotifyInstalled) {
                    SpotifyFtueAssignment(
                        variant = SpotifyFtueVariant.A,
                        alwaysPlayFullSongs = false,
                    )
                } else {
                    when (rcVariant.trim().lowercase()) {
                        "a" -> SpotifyFtueAssignment(
                            variant = SpotifyFtueVariant.A,
                            alwaysPlayFullSongs = false,
                        )
                        "b" -> SpotifyFtueAssignment(
                            variant = SpotifyFtueVariant.B,
                            alwaysPlayFullSongs = true,
                        )
                        else -> SpotifyFtueAssignment(
                            variant = SpotifyFtueVariant.OFF,
                            alwaysPlayFullSongs = true,
                        )
                    }
                }
            }
            MusicService.TIDAL, MusicService.YOUTUBE_MUSIC, MusicService.DEEZER ->
                SpotifyFtueAssignment(
                    variant = SpotifyFtueVariant.INELIGIBLE,
                    alwaysPlayFullSongs = false,
                )
        }
    }

    fun shouldPromptFirstPlay(
        assignedVariant: SpotifyFtueVariant?,
        firstPlayChooserConsumed: Boolean,
    ): Boolean = assignedVariant == SpotifyFtueVariant.B && !firstPlayChooserConsumed

    fun shouldPromptEnableFull(
        assignedVariant: SpotifyFtueVariant?,
        linkPromptConsumed: Boolean,
        firstPlayChooserConsumed: Boolean,
    ): Boolean {
        if (linkPromptConsumed) return false
        return when (assignedVariant) {
            SpotifyFtueVariant.A -> true
            SpotifyFtueVariant.B -> firstPlayChooserConsumed
            else -> false
        }
    }

    /** A (or B after 30s) turned Always Full on in Settings — prompt on next play. */
    fun shouldPromptAlwaysFullPlay(
        assignedVariant: SpotifyFtueVariant?,
        alwaysPlayFullSongs: Boolean,
        linkPromptConsumed: Boolean,
        firstPlayChooserConsumed: Boolean,
    ): Boolean = alwaysPlayFullSongs && shouldPromptEnableFull(
        assignedVariant,
        linkPromptConsumed,
        firstPlayChooserConsumed,
    )

    suspend fun applyOnboardingDefaults(
        uid: String?,
        service: MusicService,
        spotifyInstalled: Boolean,
        rcVariant: String,
        preferences: PreferencesDataStore,
        analytics: AnalyticsService,
        supportsAppleFullPlayback: Boolean = SUPPORTS_APPLE_FULL_PLAYBACK,
    ): SpotifyFtueAssignment? {
        if (uid.isNullOrBlank()) return null
        if (preferences.isSpotifyFtueAssigned(uid)) return null

        val result = assignment(
            service = service,
            spotifyInstalled = spotifyInstalled,
            rcVariant = rcVariant,
            supportsAppleFullPlayback = supportsAppleFullPlayback,
        )
        preferences.persistSpotifyFtueAssignment(
            uid = uid,
            variant = result.variant.value,
            alwaysPlayFullSongs = result.alwaysPlayFullSongs,
        )
        preferences.resetSpotifyFtuePrompts()
        analytics.setSpotifyFtueUserProperties(result.variant.value, service.value)
        analytics.logSpotifyFtueAssigned(result.variant.value, spotifyInstalled)
        return result
    }

    fun restoreUserProperties(
        preferences: PreferencesDataStore,
        musicService: String,
        analytics: AnalyticsService,
    ) {
        val variant = preferences.spotifyFtueVariantSync() ?: return
        analytics.setSpotifyFtueUserProperties(variant, musicService)
    }
}
