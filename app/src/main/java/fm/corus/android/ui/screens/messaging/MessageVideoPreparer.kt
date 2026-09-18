package fm.corus.android.ui.screens.messaging

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object MessageVideoLimits {
    /** Skip transcode for hour-long camera-roll items. Upload cap is MAX_BYTES. */
    const val MAX_PREPARE_DURATION_MS = 10 * 60 * 1000L
    const val MAX_BYTES = 16L * 1024L * 1024L
    const val TARGET_BYTES = 14L * 1024L * 1024L
}

class MessageVideoException(message: String) : Exception(message)

data class PreparedMessageVideo(
    val file: File,
    val thumbnailJpeg: ByteArray,
    val durationMs: Int,
    val width: Int,
    val height: Int,
)

object MessageVideoPreparer {
    suspend fun prepare(context: Context, uri: Uri): PreparedMessageVideo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            if (durationMs <= 0L) {
                throw MessageVideoException("Couldn't prepare this video")
            }
            if (durationMs > MessageVideoLimits.MAX_PREPARE_DURATION_MS) {
                throw MessageVideoException("This clip is too large — try a shorter one")
            }
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val tmp = width
                width = height
                height = tmp
            }
            val thumb = retriever.getFrameAtTime(100_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            val jpeg = ByteArrayOutputStream().use { out ->
                (thumb ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
                    .compress(Bitmap.CompressFormat.JPEG, 70, out)
                out.toByteArray()
            }

            val dest = File(context.cacheDir, "dm-video-${System.currentTimeMillis()}.mp4")
            val sourceBytes = sourceByteCount(context, uri)
            if (sourceBytes in 1L..MessageVideoLimits.MAX_BYTES) {
                copyUri(context, uri, dest)
            } else {
                compress(context, uri, dest, durationMs)
            }
            if (!dest.exists() || dest.length() <= 0L) {
                throw MessageVideoException("Couldn't prepare this video")
            }
            if (dest.length() > MessageVideoLimits.MAX_BYTES) {
                dest.delete()
                throw MessageVideoException("This clip is too large — try a shorter one")
            }
            PreparedMessageVideo(
                file = dest,
                thumbnailJpeg = jpeg,
                durationMs = durationMs.toInt(),
                width = width,
                height = height,
            )
        } finally {
            retriever.release()
        }
    }

    private suspend fun compress(context: Context, uri: Uri, dest: File, durationMs: Long) {
        val heightLadder = when {
            durationMs <= 90_000L -> listOf(720, 480, 360)
            durationMs <= 180_000L -> listOf(540, 360)
            else -> listOf(360, 240)
        }
        val hdrModes = listOf(
            Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
            Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIA_CODEC,
        )
        var lastError: Exception? = null
        for (height in heightLadder) {
            for (hdrMode in hdrModes) {
                if (dest.exists()) dest.delete()
                try {
                    runTransformer(context, uri, dest, height, hdrMode, durationMs)
                    if (dest.exists() && dest.length() > 0L) return
                } catch (e: Exception) {
                    lastError = e
                    dest.delete()
                }
            }
        }
        throw lastError ?: MessageVideoException("Couldn't prepare this video")
    }

    private fun sourceByteCount(context: Context, uri: Uri): Long {
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }

    private fun copyUri(context: Context, uri: Uri, dest: File) {
        if (dest.exists()) dest.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw MessageVideoException("Couldn't prepare this video")
    }

    private suspend fun runTransformer(
        context: Context,
        uri: Uri,
        dest: File,
        height: Int,
        hdrMode: Int,
        durationMs: Long,
    ) {
        val bitrate = videoBitrate(durationMs, height)
        suspendCancellableCoroutine { cont ->
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(bitrate)
                        .build()
                )
                .build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .build()
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(height))))
                .build()
            val composition = Composition.Builder(EditedMediaItemSequence(listOf(edited)))
                .setHdrMode(hdrMode)
                .build()
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            })
            transformer.start(composition, dest.absolutePath)
            cont.invokeOnCancellation { transformer.cancel() }
        }
    }

    private fun videoBitrate(durationMs: Long, height: Int): Int {
        val seconds = (durationMs / 1000.0).coerceAtLeast(1.0)
        val adaptive = ((MessageVideoLimits.TARGET_BYTES * 8) / seconds).toInt()
        val qualityCap = when {
            height >= 720 -> 1_800_000
            height >= 480 -> 1_000_000
            else -> 600_000
        }
        return adaptive.coerceIn(200_000, qualityCap)
    }
}

fun formatMessageVideoDuration(ms: Int?): String {
    if (ms == null || ms <= 0) return ""
    val total = kotlin.math.round(ms / 1000.0).toInt()
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
