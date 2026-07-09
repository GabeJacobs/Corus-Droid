package fm.corus.android.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.delay
import java.io.File

/**
 * Voice note recorder state holder.
 * Three states: idle, recording, recorded.
 * Records M4A/AAC, 44.1kHz, mono, 64kbps. Max 30 seconds.
 */
class VoiceNoteRecorderState {
    var isRecording by mutableStateOf(false)
        private set
    var hasRecording by mutableStateOf(false)
        private set
    var recordingDuration by mutableStateOf(0f)
        private set
    var audioData by mutableStateOf<ByteArray?>(null)
        private set
    var isPreviewing by mutableStateOf(false)
        private set
    var previewCurrentTime by mutableStateOf(0f)
        private set

    /**
     * True when [audioData] was loaded from a previously-saved note (a resumed
     * draft) rather than freshly recorded, so the composer reuses the stored
     * download URL at post/save time instead of re-uploading identical audio.
     * A fresh [startRecording] clears it. Mirrors iOS `loadedFromExisting`.
     */
    var loadedFromExisting by mutableStateOf(false)
        private set

    /**
     * True while a resumed draft's saved note is downloading, so the recorder
     * shows a loading placeholder instead of flashing the idle "Record" control.
     * Mirrors iOS `isLoadingExisting`.
     */
    var isLoadingExisting by mutableStateOf(false)
        private set

    private var recorder: MediaRecorder? = null
    private var previewPlayer: android.media.MediaPlayer? = null
    private var file: File? = null

    private val maxDuration = 30f

    fun startRecording(context: android.content.Context) {
        stopPreview()
        // A fresh recording supersedes any existing (resumed-draft) note.
        loadedFromExisting = false
        isLoadingExisting = false

        val outputFile = File(context.cacheDir, "cymbal_voice_note.m4a")
        file = outputFile

        try {
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setAudioEncodingBitRate(64000)
                setMaxDuration((maxDuration * 1000).toInt())
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            recordingDuration = 0f
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) { }
        recorder = null
        isRecording = false

        file?.let {
            if (it.exists()) {
                audioData = it.readBytes()
                hasRecording = audioData != null
            }
        }
    }

    fun deleteRecording() {
        stopPreview()
        audioData = null
        hasRecording = false
        recordingDuration = 0f
        loadedFromExisting = false
        isLoadingExisting = false
        file?.delete()
    }

    /**
     * Enter the "downloading an existing note" state (a resumed voice draft), so
     * the recorder shows a loading placeholder and never flashes the idle Record
     * control before the note finishes downloading. Mirrors iOS
     * `beginLoadingExisting`.
     */
    fun beginLoadingExisting() {
        stopPreview()
        audioData = null
        hasRecording = false
        recordingDuration = 0f
        loadedFromExisting = false
        isLoadingExisting = true
    }

    /**
     * Load an already-recorded note (from a resumed draft) so the recorded-state
     * UI (play / scrub / delete) shows it exactly like a freshly recorded note.
     * The bytes are written to the same cache file the preview player reads, and
     * [loadedFromExisting] is set so the composer reuses the stored download URL
     * at post/save time instead of re-uploading identical audio. Mirrors iOS
     * `loadExisting`. Returns true if the note loaded successfully.
     */
    fun loadExisting(context: android.content.Context, data: ByteArray): Boolean {
        stopPreview()
        val outputFile = File(context.cacheDir, "cymbal_voice_note.m4a")
        return try {
            outputFile.writeBytes(data)
            file = outputFile
            audioData = data
            recordingDuration = measureDuration(outputFile)
            hasRecording = true
            loadedFromExisting = true
            isLoadingExisting = false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isLoadingExisting = false
            false
        }
    }

    /** Abandon a failed/cancelled existing-note download without leaving the UI stuck. */
    fun cancelLoadingExisting() {
        isLoadingExisting = false
    }

    private fun measureDuration(f: File): Float {
        return try {
            val mp = android.media.MediaPlayer()
            mp.setDataSource(f.absolutePath)
            mp.prepare()
            val d = mp.duration.toFloat() / 1000f
            mp.release()
            d
        } catch (_: Exception) {
            0f
        }
    }

    fun togglePreview(context: android.content.Context) {
        if (isPreviewing) {
            stopPreview()
        } else {
            startPreview(context)
        }
    }

    private fun startPreview(context: android.content.Context) {
        val f = file ?: return
        if (!f.exists()) return

        try {
            previewPlayer = android.media.MediaPlayer().apply {
                setDataSource(f.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    stopPreview()
                }
            }
            isPreviewing = true
            previewCurrentTime = 0f
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) { }
        previewPlayer = null
        isPreviewing = false
        previewCurrentTime = 0f
    }

    fun updateTimerTick() {
        if (isRecording) {
            recordingDuration = (recordingDuration + 0.1f).coerceAtMost(maxDuration)
            if (recordingDuration >= maxDuration) {
                stopRecording()
            }
        }
        if (isPreviewing) {
            previewCurrentTime = (previewPlayer?.currentPosition?.toFloat()?.div(1000f)) ?: 0f
        }
    }
}

@Composable
fun rememberVoiceNoteRecorderState(): VoiceNoteRecorderState {
    return remember { VoiceNoteRecorderState() }
}

@Composable
fun VoiceNoteRecorderView(
    recorderState: VoiceNoteRecorderState,
    modifier: Modifier = Modifier,
    onRecorded: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    // Fire onRecorded once per completed recording (edge: hasRecording false → true)
    LaunchedEffect(recorderState.hasRecording) {
        if (recorderState.hasRecording) onRecorded?.invoke()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) recorderState.startRecording(context)
    }

    // Timer tick
    LaunchedEffect(recorderState.isRecording, recorderState.isPreviewing) {
        while (recorderState.isRecording || recorderState.isPreviewing) {
            delay(100)
            recorderState.updateTimerTick()
        }
    }

    Box(modifier = modifier) {
        when {
            recorderState.isRecording -> RecordingState(recorderState)
            recorderState.hasRecording -> RecordedState(recorderState, context)
            // A resumed draft's note is still downloading — hold the recorded-row
            // height with a spinner so the idle Record control never flashes.
            recorderState.isLoadingExisting -> LoadingExistingState()
            else -> IdleState(
                onStartRecording = {
                    val permission = Manifest.permission.RECORD_AUDIO
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        recorderState.startRecording(context)
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
            )
        }
    }
}

/**
 * Placeholder while a resumed draft's saved note downloads. Occupies the same
 * row height as [RecordedState] so there's no layout jump when it swaps in.
 */
@Composable
private fun LoadingExistingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = CorusColors.Accent,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun IdleState(onStartRecording: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
            .border(1.5.dp, CorusColors.Accent, RoundedCornerShape(CorusSpacing.cornerRadius))
            .clickable(onClick = onStartRecording)
            .padding(vertical = CorusSpacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = CorusColors.Accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Text(stringResource(R.string.voice_note_record), style = CorusFont.body, color = CorusColors.Accent)
    }
}

@Composable
private fun RecordingState(recorderState: VoiceNoteRecorderState) {
    // Pulsing red dot animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        // Pulsing red dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(alpha)
                .background(Color.Red, CircleShape),
        )

        Text(
            text = formatTime(recorderState.recordingDuration),
            style = CorusFont.body,
            color = CorusColors.Text,
        )

        Text(
            text = stringResource(R.string.voice_note_max_duration_format, formatTime(30f)),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Progress bar
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CorusColors.Secondary.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(recorderState.recordingDuration / 30f)
                    .background(Color.Red, RoundedCornerShape(2.dp)),
            )
        }

        // Stop button
        Icon(
            Icons.Filled.Stop,
            contentDescription = stringResource(R.string.voice_note_cd_stop),
            tint = Color.Red,
            modifier = Modifier
                .size(28.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { recorderState.stopRecording() },
        )
    }
}

@Composable
private fun RecordedState(
    recorderState: VoiceNoteRecorderState,
    context: android.content.Context,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        // Play/Pause
        Icon(
            imageVector = if (recorderState.isPreviewing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (recorderState.isPreviewing) stringResource(R.string.voice_note_cd_pause) else stringResource(R.string.voice_note_cd_play),
            tint = CorusColors.Accent,
            modifier = Modifier
                .size(28.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { recorderState.togglePreview(context) },
        )

        // Progress bar + duration
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CorusColors.Secondary.copy(alpha = 0.2f)),
            ) {
                val progress = if (recorderState.recordingDuration > 0f)
                    recorderState.previewCurrentTime / recorderState.recordingDuration else 0f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(CorusColors.Accent, RoundedCornerShape(2.dp)),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatTime(recorderState.recordingDuration),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
            )
        }

        // Delete
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.voice_note_cd_delete),
            tint = CorusColors.Secondary,
            modifier = Modifier
                .size(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { recorderState.deleteRecording() },
        )
    }
}

private fun formatTime(seconds: Float): String {
    val s = seconds.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
