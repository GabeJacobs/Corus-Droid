package fm.corus.android.ui.screens.live

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal fun newLiveStreamPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(AudioAttributes.DEFAULT, true)
        setHandleAudioBecomingNoisy(true)
    }

internal suspend fun ExoPlayer.prepareLiveStream(url: String, positionMs: Long = 0) {
    setMediaItem(MediaItem.fromUri(url), positionMs)
    prepare()
    val ready = withTimeoutOrNull(12_000L) {
        while (playbackState != Player.STATE_READY) {
            playerError?.let { throw it }
            check(playbackState != Player.STATE_ENDED)
            delay(40)
        }
        true
    }
    check(ready == true) { "This track took too long to load." }
}
