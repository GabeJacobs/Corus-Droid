package fm.corus.android.domain

import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import org.junit.Assert.*
import org.junit.Test

class AudiomackRoutingTest {
    @Test fun `full playback requires both the selected provider and the rollout flag`() {
        assertTrue(SongPlayRouting.wantsAudiomackFullSong(TrackSource.SPOTIFY, MusicService.AUDIOMACK, true))
        assertFalse(SongPlayRouting.wantsAudiomackFullSong(TrackSource.SPOTIFY, MusicService.AUDIOMACK, false))
        assertFalse(SongPlayRouting.wantsAudiomackFullSong(TrackSource.AUDIOMACK, MusicService.SPOTIFY, true))
    }
    @Test fun `SoundCloud and Bandcamp retain their own playback engines`() {
        for (source in listOf(TrackSource.SOUNDCLOUD, TrackSource.BANDCAMP)) {
            assertFalse(SongPlayRouting.wantsAudiomackFullSong(source, MusicService.AUDIOMACK, true))
        }
    }
}
