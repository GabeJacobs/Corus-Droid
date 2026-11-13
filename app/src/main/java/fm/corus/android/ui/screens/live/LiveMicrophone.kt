package fm.corus.android.ui.screens.live

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.components.rememberVoiceNoteRecorderState
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10

private val MicrophoneMeterHeight = 16.dp
private const val GoodMicLevel = .07f
private const val MicMeterSegmentCount = 24
private const val LoudMicMeterLevel = (MicMeterSegmentCount - 2f) / MicMeterSegmentCount
private const val MicMeterFloorDecibels = -30f

@Composable
internal fun LiveMicrophone(
    welcome: Boolean,
    initial: LiveRecording?,
    close: () -> Unit,
    done: (LiveRecording?) -> Unit,
) {
    val context = LocalContext.current
    val recorder = rememberVoiceNoteRecorderState(maxDuration = if (welcome) 30f else 3f)
    var sampling by remember { mutableStateOf(welcome) }
    var monitor by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var recordingLevel by remember(recorder.isRecording) { mutableFloatStateOf(0f) }
    var detectedSound by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var pendingRecord by remember { mutableStateOf(false) }
    var recordRequest by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun begin(record: Boolean) {
        if (record) {
            monitor = false
            sampling = true
            recordRequest++
        } else monitor = true
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) begin(pendingRecord)
        pendingRecord = false
    }

    fun request(record: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            begin(record)
        } else {
            pendingRecord = record
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(welcome) {
        if (welcome && initial != null) recorder.loadExisting(context, initial.bytes)
        if (!welcome) request(false)
    }

    LaunchedEffect(recordRequest) {
        if (recordRequest == 0) return@LaunchedEffect
        delay(150)
        recorder.deleteRecording()
        recorder.startRecording(context)
        if (!recorder.isRecording) failed = true
    }

    LaunchedEffect(monitor) {
        if (!monitor) return@LaunchedEffect
        try {
            monitorMicrophone { sample ->
                level = sample
                detectedSound = detectedSound || sample > GoodMicLevel
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            monitor = false
            failed = true
        }
    }

    LaunchedEffect(recorder.isRecording, recorder.isPreviewing) {
        while (recorder.isRecording || recorder.isPreviewing) {
            delay(100)
            if (recorder.isRecording) {
                val peak = recorder.readRecordingPeakAmplitude()
                recordingLevel = peak / 32768f
            }
            recorder.updateTimerTick()
        }
    }

    LaunchedEffect(recorder.hasRecording) {
        if (!welcome && recorder.hasRecording) recorder.togglePreview(context)
    }

    DisposableEffect(recorder, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                monitor = false
                if (recorder.isRecording) recorder.stopRecording()
                recorder.stopPreview()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            recorder.cancelRecording()
            recorder.stopPreview()
        }
    }

    fun finish() {
        if (welcome) {
            val clip = recorder.audioData?.let { LiveRecording(it, recorder.recordingDuration.toDouble().coerceIn(.1, 30.0)) }
            if (clip != null || initial != null) done(clip) else close()
        } else if (detectedSound || recorder.hasRecording) done(null) else close()
    }

    LiveSheet(
        if (welcome) stringResource(CorusStrings.corus_live_welcome_message) else stringResource(CorusStrings.corus_live_test_microphone),
        {
            if (recorder.isRecording) {
                recorder.cancelRecording()
                close()
            } else {
                finish()
            }
        },
    ) {
        LiveText(
            when {
                welcome -> "Record a short greeting for listeners. Up to 30 seconds."
                sampling && recorder.hasRecording -> "Listen back to check how you sound."
                sampling -> "Speak for a few seconds to hear a sample."
                else -> "Speak normally and watch the microphone level."
            },
            CorusFont.body, CorusColors.Secondary, Modifier.align(Alignment.CenterHorizontally),
        )
        Column(
            Modifier.fillMaxWidth().background(CorusColors.SheetControlBackground, RoundedCornerShape(12.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!sampling) {
                LiveText(
                    when {
                        !monitor -> "Microphone paused"
                        microphoneMeterLevel(level) > LoudMicMeterLevel -> "Too loud · Move back"
                        level > GoodMicLevel -> "Good level"
                        detectedSound -> "Too quiet · Move closer"
                        else -> "Speak to test"
                    },
                    CorusFont.username,
                )
                LiveMicrophoneMeter(if (monitor) level else 0f)
            } else {
                Row(
                    Modifier.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (recorder.hasRecording) {
                        LiveIconButton(if (recorder.isPreviewing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (recorder.isPreviewing) "Pause playback" else "Play sample", { recorder.togglePreview(context) },
                            tint = CorusColors.Accent)
                    } else Icon(Icons.Default.Mic, null, tint = CorusColors.Accent, modifier = Modifier.size(24.dp))
                    val duration = recorder.recordingDuration.toInt().coerceAtLeast(0)
                    val position = recorder.previewCurrentTime.toInt().coerceAtLeast(0)
                    LiveText(
                        when {
                            recorder.hasRecording -> "0:${position.toString().padStart(2, '0')} / 0:${duration.toString().padStart(2, '0')}"
                            recorder.isRecording -> if (welcome) "Recording…" else "Speak now"
                            else -> "Ready to record"
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    )
                    if (recorder.hasRecording) {
                        LiveIconButton(Icons.Default.Replay, "Record again", { request(true) })
                        if (welcome) LiveIconButton(Icons.Default.DeleteOutline, "Remove welcome message", { recorder.deleteRecording() })
                    } else if (recorder.isRecording) {
                        LiveText(
                            "0:${duration.toString().padStart(2, '0')}",
                            style = CorusFont.caption.copy(fontFeatureSettings = "tnum"),
                            color = CorusColors.Secondary,
                            lines = 1,
                        )
                    }
                }
                if (recorder.isRecording || (!welcome && !recorder.hasRecording)) {
                    LiveMicrophoneMeter(recordingLevel)
                } else if (recorder.hasRecording) {
                    Box(
                        Modifier.fillMaxWidth().height(MicrophoneMeterHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        LinearProgressIndicator(
                            progress = {
                                (recorder.previewCurrentTime / recorder.recordingDuration.coerceAtLeast(.001f))
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = CorusColors.Accent,
                            drawStopIndicator = {},
                        )
                    }
                }
            }
        }
        when {
            recorder.isRecording -> LivePrimary("Stop recording", onClick = recorder::stopRecording)
            recorder.hasRecording -> LivePrimary(stringResource(CorusStrings.common_done), onClick = ::finish)
            welcome -> LivePrimary("Record welcome message") { request(true) }
            monitor -> LivePrimary("Record a sample") { request(true) }
            else -> LivePrimary(stringResource(CorusStrings.corus_live_test_microphone)) { request(false) }
        }
    }
    if (denied || failed) {
        val clear = { denied = false; failed = false }
        AlertDialog(
            onDismissRequest = clear,
            title = { LiveText(if (denied) "Microphone access is off" else "Couldn’t complete the microphone test. Please try again.") },
            confirmButton = { TextButton(onClick = clear) { LiveText(stringResource(CorusStrings.common_ok)) } },
        )
    }
}

@Composable
private fun LiveMicrophoneMeter(amplitude: Float) {
    val level = microphoneMeterLevel(amplitude)
    val activeColor = when {
        level > LoudMicMeterLevel -> LiveRed
        amplitude > GoodMicLevel -> CorusColors.Verified
        else -> CorusColors.Accent
    }
    Row(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "Microphone level"
            progressBarRangeInfo = ProgressBarRangeInfo(level, 0f..1f)
        },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(MicMeterSegmentCount) { segment ->
            val color = if (segment.toFloat() / MicMeterSegmentCount < level) {
                activeColor
            } else {
                CorusColors.Secondary.copy(alpha = .15f)
            }
            Box(Modifier.weight(1f).height(MicrophoneMeterHeight).background(color, CircleShape))
        }
    }
}

private fun microphoneMeterLevel(amplitude: Float): Float {
    val decibels = 20f * log10(amplitude.coerceAtLeast(.001f))
    return ((decibels - MicMeterFloorDecibels) / -MicMeterFloorDecibels).coerceIn(0f, 1f)
}

private suspend fun monitorMicrophone(onLevel: (Float) -> Unit) = withContext(Dispatchers.IO) {
    val rate = 16_000
    val channel = AudioFormat.CHANNEL_IN_MONO
    val encoding = AudioFormat.ENCODING_PCM_16BIT
    val bufferSize = AudioRecord.getMinBufferSize(rate, channel, encoding).coerceAtLeast(2_048)
    val record = AudioRecord.Builder()
        .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(channel).setEncoding(encoding).build())
        .setBufferSizeInBytes(bufferSize)
        .build()
    val samples = ShortArray(bufferSize / 2)
    try {
        record.startRecording()
        while (isActive) {
            val count = record.read(samples, 0, samples.size)
            check(count > 0) { "Couldn’t read microphone input." }
            var peak = 0
            for (index in 0 until count) peak = maxOf(peak, abs(samples[index].toInt()))
            withContext(Dispatchers.Main) { onLevel((peak / 32768f).coerceIn(0f, 1f)) }
        }
    } finally {
        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        } finally {
            record.release()
        }
    }
}
