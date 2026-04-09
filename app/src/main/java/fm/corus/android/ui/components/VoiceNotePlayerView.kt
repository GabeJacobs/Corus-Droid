package fm.corus.android.ui.components

import android.media.MediaPlayer
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
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Plays a voice note from a URL. Downloads on first play, then caches in memory.
 * Only one voice note plays at a time across the app (singleton player management).
 */
object VoiceNotePlayerManager {
    private var activePlayerUrl: String? = null
    private var activeStopCallback: (() -> Unit)? = null

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
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
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
            modifier = Modifier.size(10.dp),
        )

        // Play/Pause
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (isLoading) return@clickable
                    if (player != null) {
                        if (isPlaying) {
                            player?.pause()
                            isPlaying = false
                            VoiceNotePlayerManager.clearIfActive(voiceNoteURL)
                        } else {
                            // Resume — stop any other active player first
                            VoiceNotePlayerManager.stopActivePlayer()
                            onPlaybackStarted?.invoke()
                            player?.start()
                            isPlaying = true
                            VoiceNotePlayerManager.registerActivePlayer(voiceNoteURL) {
                                player?.pause()
                                isPlaying = false
                            }
                        }
                    } else {
                        // First play — stop any other active player, then download
                        VoiceNotePlayerManager.stopActivePlayer()
                        onPlaybackStarted?.invoke()
                        isLoading = true
                        val mp = MediaPlayer()
                        mp.setOnPreparedListener {
                            duration = it.duration.toFloat() / 1000f
                            it.start()
                            isPlaying = true
                            isLoading = false
                            player = mp
                            VoiceNotePlayerManager.registerActivePlayer(voiceNoteURL) {
                                mp.pause()
                                isPlaying = false
                            }
                        }
                        mp.setOnCompletionListener {
                            isPlaying = false
                            currentTime = 0f
                            it.seekTo(0)
                            VoiceNotePlayerManager.clearIfActive(voiceNoteURL)
                        }
                        mp.setOnErrorListener { _, _, _ ->
                            isLoading = false
                            VoiceNotePlayerManager.clearIfActive(voiceNoteURL)
                            true
                        }
                        try {
                            mp.setDataSource(voiceNoteURL)
                            mp.prepareAsync()
                        } catch (_: Exception) {
                            isLoading = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = CorusColors.Accent,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(24.dp),
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
