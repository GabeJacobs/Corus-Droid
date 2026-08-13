package fm.corus.android.domain

import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [SongPlayRouting] — mirrors iOS SongPlayRoutingTests.
 */
class SongPlayRoutingTest {
    @Test
    fun fullSongForEntitledAppleMusicUser() {
        for (source in listOf(TrackSource.SPOTIFY, TrackSource.APPLEMUSIC)) {
            assertTrue(
                "$source should route to MusicKit",
                SongPlayRouting.wantsFullSong(source, MusicService.APPLE_MUSIC, playFullSongs = true),
            )
        }
    }

    @Test
    fun previewWhenFullSongsDisabled() {
        assertFalse(
            SongPlayRouting.wantsFullSong(TrackSource.SPOTIFY, MusicService.APPLE_MUSIC, playFullSongs = false),
        )
    }

    @Test
    fun previewForOtherServices() {
        for (service in MusicService.entries) {
            if (service == MusicService.APPLE_MUSIC) continue
            assertFalse(
                "$service has no MusicKit entitlement",
                SongPlayRouting.wantsFullSong(TrackSource.SPOTIFY, service, playFullSongs = true),
            )
        }
    }

    @Test
    fun previewForNonCatalogSources() {
        for (source in listOf(
            TrackSource.SOUNDCLOUD,
            TrackSource.AUDIOMACK,
            TrackSource.TIDAL,
            TrackSource.DEEZER,
        )) {
            assertFalse(
                "$source has no MusicKit equivalent",
                SongPlayRouting.wantsFullSong(source, MusicService.APPLE_MUSIC, playFullSongs = true),
            )
        }
    }

    @Test
    fun spotifyAuthExperimentRouting() {
        assertTrue(
            SongPlayRouting.wantsSpotifyAuthExperiment(
                TrackSource.SPOTIFY, MusicService.SPOTIFY, playFullSongs = true,
            ),
        )
        assertFalse(
            SongPlayRouting.wantsSpotifyAuthExperiment(
                TrackSource.SPOTIFY, MusicService.SPOTIFY, playFullSongs = false,
            ),
        )
        assertFalse(
            SongPlayRouting.wantsSpotifyAuthExperiment(
                TrackSource.SPOTIFY, MusicService.APPLE_MUSIC, playFullSongs = true,
            ),
        )
        assertFalse(
            SongPlayRouting.wantsSpotifyAuthExperiment(
                TrackSource.SOUNDCLOUD, MusicService.SPOTIFY, playFullSongs = true,
            ),
        )
        assertFalse(
            "Apple-sourced without a Spotify id must preview, not Connect",
            SongPlayRouting.wantsSpotifyAuthExperiment(
                TrackSource.APPLEMUSIC, MusicService.SPOTIFY, playFullSongs = true,
                trackId = "am:212853519",
                spotifyURI = "",
            ),
        )
        assertFalse(
            SongPlayRouting.hasPlayableSpotifyId("am:212853519", ""),
        )
        assertTrue(
            SongPlayRouting.wantsSpotifyAuthExperiment(
                TrackSource.APPLEMUSIC, MusicService.SPOTIFY, playFullSongs = true,
                trackId = "4yR9WXd0n67vf8KqG2N4Zv",
                spotifyURI = "spotify:track:4yR9WXd0n67vf8KqG2N4Zv",
            ),
        )
    }
}
