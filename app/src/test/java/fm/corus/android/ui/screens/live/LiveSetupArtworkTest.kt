package fm.corus.android.ui.screens.live

import fm.corus.android.data.model.CymbalTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSetupArtworkTest {
    @Test fun queueCardUsesTheFirstSongsFullArtwork() {
        val song = CymbalTrack(
            id = "track",
            name = "Song",
            artistName = "Artist",
            albumName = "Album",
            albumArtURL = "https://i.scdn.co/image/ab67616d00004851abc",
            albumArtLargeURL = "https://i.scdn.co/image/ab67616d0000b273abc",
        )
        val setup = LiveSetup(id = "draft", queue = listOf(LiveTrack(song)))

        assertEquals(song.albumArtLargeURL, setup.firstSongArtwork)
    }

    @Test fun queueCardUpgradesAThumbnailWhenTheFullArtworkIsMissing() {
        val track = LiveTrack(
            CymbalTrack(
                id = "track",
                name = "Song",
                artistName = "Artist",
                albumName = "Album",
                albumArtURL = "https://i.scdn.co/image/ab67616d00004851abc",
            ),
        )
        val setup = LiveSetup(id = "draft", queue = listOf(track))

        assertEquals("https://i.scdn.co/image/ab67616d0000b273abc", setup.firstSongArtwork)
    }
}
