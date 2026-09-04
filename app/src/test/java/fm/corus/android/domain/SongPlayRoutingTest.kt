package fm.corus.android.domain

import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.model.CymbalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [SongPlayRouting] — mirrors iOS SongPlayRoutingTests.
 */
class SongPlayRoutingTest {
    @Test
    fun appleOnlyFlagSurvivesPlaybackQueueConversion() {
        val queued = CymbalTrack(
            id = "am:212853519",
            name = "Apple-only song",
            artistName = "Artist",
            albumName = "Album",
            source = TrackSource.APPLEMUSIC,
            previewUrl = "https://example.com/preview.m4a",
            notOnSpotify = true,
        ).toQueuedTrack()

        assertTrue(queued.notOnSpotify)
        assertEquals("https://example.com/preview.m4a", queued.previewUrl)
    }

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
        assertTrue(
            "Apple-sourced catalog rows still enter Connect so ISRC lookup can resolve them",
            SongPlayRouting.wantsSpotifyAuthExperiment(
                TrackSource.APPLEMUSIC, MusicService.SPOTIFY, playFullSongs = true,
                trackId = "am:212853519",
                spotifyURI = "",
            ),
        )
        assertFalse(
            "A backend-confirmed Apple-only track must use its preview instead of entering Connect",
            SongPlayRouting.wantsSpotifyAuthExperiment(
                TrackSource.APPLEMUSIC, MusicService.SPOTIFY, playFullSongs = true,
                trackId = "am:212853519",
                spotifyURI = "",
                knownNotOnSpotify = true,
            ),
        )
        assertFalse(
            SongPlayRouting.wantsSpotifyAuthExperiment(
                TrackSource.APPLEMUSIC, MusicService.SPOTIFY, playFullSongs = false,
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

    @Test
    fun unresolvedAppleSourceKeepsSpotifyForSpotifyViewer() {
        assertEquals(
            MusicService.SPOTIFY,
            SongPlayRouting.displayedLinkOutService(
                TrackSource.APPLEMUSIC,
                MusicService.SPOTIFY,
            ),
        )
    }

    @Test
    fun confirmedNotOnSpotifyShowsAppleMusicToSpotifyViewer() {
        assertEquals(
            MusicService.APPLE_MUSIC,
            SongPlayRouting.displayedLinkOutService(
                TrackSource.APPLEMUSIC,
                MusicService.SPOTIFY,
                knownNotOnSpotify = true,
            ),
        )
    }

    @Test
    fun appleOnlyTrackKeepsNonSpotifyViewer() {
        assertEquals(
            MusicService.APPLE_MUSIC,
            SongPlayRouting.displayedLinkOutService(
                TrackSource.APPLEMUSIC,
                MusicService.APPLE_MUSIC,
            ),
        )
        assertEquals(
            MusicService.TIDAL,
            SongPlayRouting.displayedLinkOutService(
                TrackSource.APPLEMUSIC,
                MusicService.TIDAL,
            ),
        )
        assertEquals(
            MusicService.DEEZER,
            SongPlayRouting.displayedLinkOutService(
                TrackSource.APPLEMUSIC,
                MusicService.DEEZER,
            ),
        )
        assertEquals(
            MusicService.YOUTUBE_MUSIC,
            SongPlayRouting.displayedLinkOutService(
                TrackSource.APPLEMUSIC,
                MusicService.YOUTUBE_MUSIC,
            ),
        )
    }

    @Test
    fun spotifySourceKeepsViewerService() {
        assertEquals(
            MusicService.SPOTIFY,
            SongPlayRouting.displayedLinkOutService(
                TrackSource.SPOTIFY,
                MusicService.SPOTIFY,
            ),
        )
        assertEquals(
            MusicService.APPLE_MUSIC,
            SongPlayRouting.displayedLinkOutService(
                TrackSource.SPOTIFY,
                MusicService.APPLE_MUSIC,
            ),
        )
    }
}
