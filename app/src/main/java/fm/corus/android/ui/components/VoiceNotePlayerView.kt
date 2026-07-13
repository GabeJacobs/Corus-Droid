package fm.corus.android.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import fm.corus.android.domain.TrailerPlaybackCoordinator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Wraps Android's audio-focus API so the voice-note player auto-pauses when
 * another app (e.g. Spotify) starts playing music.
 */
private class VoiceNoteAudioFocus(
    private val context: Context,
    private val onPause: () -> Unit,
) {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
        ) {
            onPause()
        }
    }
    private var request: AudioFocusRequest? = null

    fun request(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            request = r
            audioManager.requestAudioFocus(r)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let { audioManager.abandonAudioFocusRequest(it) }
            request = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
    }
}

/**
 * Plays a voice note from a URL. Downloads on first play, then caches in memory.
 * Only one voice note plays at a time across the app (singleton player management).
 */
object VoiceNotePlayerManager {
    private var activePlayerUrl: String? = null
    private var activeStopCallback: (() -> Unit)? = null

    /** Pauses the song player. Registered by [NowPlayingManager] so a starting
     *  caption can stop music without a direct dependency, mirroring
     *  [TrailerPlaybackCoordinator.pauseMusic]. */
    var pauseMusic: (() -> Unit)? = null

    fun stopActivePlayer() {
        activeStopCallback?.invoke()
        activeStopCallback = null
        activePlayerUrl = null
    }

    fun registerActivePlayer(url: String, stopCallback: () -> Unit) {
        activePlayerUrl = url
        activeStopCallback = stopCallback
    }

    fun clearIfActive(url: String) {
        if (activePlayerUrl == url) {
            activeStopCallback = null
            activePlayerUrl = null
        }
    }
}

@Composable
fun VoiceNotePlayerView(
    voiceNoteURL: String,
    username: String,
    onUsernameTap: () -> Unit = {},
    onPlaybackStarted: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var currentTime by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val context = LocalContext.current
    val audioFocus = remember {
        VoiceNoteAudioFocus(context.applicationContext) {
            player?.takeIf { it.isPlaying }?.pause()
            isPlaying = false
            VoiceNotePlayerManager.clearIfActive(voiceNoteURL)
        }
    }

    // Timer for progress updates
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(100)
            player?.let {
                if (it.isPlaying) {
                    currentTime = it.currentPosition.toFloat() / 1000f
                }
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            VoiceNotePlayerManager.clearIfActive(voiceNoteURL)
            player?.release()
            audioFocus.abandon()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
    ) {
        // Username
        Text(
            text = username,
            style = CorusFont.username,
            color = CorusColors.Text,
            modifier = Modifier.clickable(onClick = onUsernameTap),
        )

        // Mic icon
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            tint = CorusColors.Accent,
            modifier = Modifier.padding(horizontal = 1.dp).size(14.dp),
        )

        // Play/Pause — blue circle with white icon (matching iOS)
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(CorusColors.Accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (isLoading) return@clickable
                    if (player != null) {
                        if (isPlaying) {
                            player?.pause()
                            isPlaying = false
                            audioFocus.abandon()
                            VoiceNotePlayerManager.clearIfActive(voiceNoteURL)
                        } else {
                            // Resume — stop any other active player first
                            VoiceNotePlayerManager.stopActivePlayer()
                            // Starting a caption also stops the song and any inline
                            // trailer, so the three sources never play over each other.
                            VoiceNotePlayerManager.pauseMusic?.invoke()
                            TrailerPlaybackCoordinator.stopAll()
                            onPlaybackStarted?.invoke()
                            audioFocus.request()
                            player?.start()
                            isPlaying = true
                            VoiceNotePlayerManager.registerActivePlayer(voiceNoteURL) {
                                player?.pause()
                                isPlaying = false
                                audioFocus.abandon()
                            }
                        }
                    } else {
                        // First play — stop any other active player, then download
                        VoiceNotePlayerManager.stopActivePlayer()
                        // Starting a caption also stops the song and any inline
                        // trailer, so the three sources never play over each other.
                        VoiceNotePlayerManager.pauseMusic?.invoke()
                        TrailerPlaybackCoordinator.stopAll()
                        onPlaybackStarted?.invoke()
                        isLoading = true
                        val mp = MediaPlayer()
                        // Store the player immediately so DisposableEffect.onDispose can
                        // always release it, even if this composable leaves the screen
                        // while prepareAsync() is still streaming. Otherwise the
                        // half-prepared MediaPlayer (with an open HTTP connection to
                        // voiceNoteURL) leaks, and its GC finalizer's
                        // MediaHTTPConnection.disconnect() can block >10s and trip the
                        // FinalizerWatchdog (TimeoutException crash). release() is safe to
                        // call mid-prepare; the isLoading guard above blocks taps until ready.
                        player = mp
                        mp.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build(),
                        )
                        mp.setOnPreparedListener {
                            duration = it.duration.toFloat() / 1000f
                            audioFocus.request()
                            it.start()
                            isPlaying = true
                            isLoading = false
                            // player was already set to mp at creation (see above).
                            VoiceNotePlayerManager.registerActivePlayer(voiceNoteURL) {
                                mp.pause()
                                isPlaying = false
                                audioFocus.abandon()
                            }
                        }
                        mp.setOnCompletionListener {
                            isPlaying = false
                            currentTime = 0f
                            it.seekTo(0)
                            audioFocus.abandon()
                            VoiceNotePlayerManager.clearIfActive(voiceNoteURL)
                        }
                        mp.setOnErrorListener { _, _, _ ->
                            isLoading = false
                            isPlaying = false
                            audioFocus.abandon()
                            VoiceNotePlayerManager.clearIfActive(voiceNoteURL)
                            // Release the failed player so it can't leak to the finalizer,
                            // and null it so the next tap starts a fresh one.
                            mp.release()
                            player = null
                            true
                        }
                        try {
                            mp.setDataSource(voiceNoteURL)
                            mp.prepareAsync()
                        } catch (_: Exception) {
                            isLoading = false
                            mp.release()
                            player = null
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        // Progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CorusColors.Secondary.copy(alpha = 0.2f)),
        ) {
            val progress = if (duration > 0f) (currentTime / duration).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(CorusColors.Accent, RoundedCornerShape(2.dp)),
            )
        }

        // Duration
        Text(
            text = formatVoiceNoteTime(if (isPlaying) currentTime else duration),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
        )
    }
}

private fun formatVoiceNoteTime(seconds: Float): String {
    val s = seconds.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
