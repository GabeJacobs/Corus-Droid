package fm.corus.android.ui.screens.messaging

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.round

object MessageVideoLimits {
    const val MAX_PREPARE_DURATION_MS = 2 * 60 * 1000L
    const val MAX_BYTES = 30L * 1024L * 1024L
    const val COMPRESSED_BITS_PER_SECOND = 1_200_000L
    const val SHORT_CLIP_1080P_MS = 20_000L

    fun estimatedCompressedBytes(durationMs: Long): Long {
        val seconds = durationMs / 1000.0
        return ceil(seconds * COMPRESSED_BITS_PER_SECOND / 8.0).toLong()
    }

    fun fitsAsIs(size: Long): Boolean = size > 0L && size <= MAX_BYTES
}

class MessageVideoException(message: String) : Exception(message) {
    companion object {
        const val TOO_LONG = "Videos can be up to 2 minutes"
        const val TOO_LARGE = "Videos need to be under 30 MB"
        const val FAILED = "Couldn't prepare this video"
    }
}

data class PreparedMessageVideo(
    val file: File,
    val thumbnailJpeg: ByteArray,
    val durationMs: Int,
    val width: Int,
    val height: Int,
)

@OptIn(UnstableApi::class)
object MessageVideoPreparer {
    suspend fun prepare(
        context: Context,
        uri: Uri,
        onPreview: ((ByteArray) -> Unit)? = null,
        onDuration: ((Int) -> Unit)? = null,
        onAccepted: (() -> Unit)? = null,
    ): PreparedMessageVideo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            if (durationMs <= 0L) {
                throw MessageVideoException(MessageVideoException.FAILED)
            }
            onDuration?.invoke(durationMs.toInt())
            if (durationMs > MessageVideoLimits.MAX_PREPARE_DURATION_MS) {
                throw MessageVideoException(MessageVideoException.TOO_LONG)
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

            val sourceBytes = sourceByteCount(context, uri)
            val fitsAsIs = MessageVideoLimits.fitsAsIs(sourceBytes)
            val estimate = MessageVideoLimits.estimatedCompressedBytes(durationMs)
            if (!fitsAsIs && estimate > MessageVideoLimits.MAX_BYTES) {
                throw MessageVideoException(MessageVideoException.TOO_LARGE)
            }
            onAccepted?.invoke()

            val thumb = retriever.getFrameAtTime(100_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            val jpeg = ByteArrayOutputStream().use { out ->
                (thumb ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
                    .compress(Bitmap.CompressFormat.JPEG, 70, out)
                out.toByteArray()
            }
            onPreview?.invoke(jpeg)

            val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull() ?: 0f
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            val wantsCompat = fps >= 48f
                || maxOf(width, height) > 1280
                || mime.contains("hevc", ignoreCase = true)
                || mime.contains("h265", ignoreCase = true)
            val transcode = !fitsAsIs || (wantsCompat && estimate <= MessageVideoLimits.MAX_BYTES)
            val dest = File(context.cacheDir, "dm-video-${System.currentTimeMillis()}.mp4")
            if (transcode) {
                val use1080 = durationMs <= MessageVideoLimits.SHORT_CLIP_1080P_MS
                    && fps < 48f
                    && maxOf(width, height) <= 1920
                exportLikeWhatsApp(context, uri, durationMs, dest, use1080)
            } else {
                copyUri(context, uri, dest)
            }
            if (!dest.exists() || dest.length() <= 0L) {
                throw MessageVideoException(MessageVideoException.FAILED)
            }
            if (dest.length() > MessageVideoLimits.MAX_BYTES) {
                dest.delete()
                throw MessageVideoException(MessageVideoException.TOO_LARGE)
            }
            PreparedMessageVideo(
                file = dest,
                thumbnailJpeg = jpeg,
                durationMs = durationMs.toInt(),
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1),
            )
        } finally {
            retriever.release()
        }
    }

    private suspend fun exportLikeWhatsApp(
        context: Context,
        uri: Uri,
        durationMs: Long,
        dest: File,
        use1080: Boolean,
    ) {
        if (dest.exists()) dest.delete()
        val height = if (use1080) 1080 else 720
        val timeoutMs = (durationMs * 2.5).toLong().coerceIn(60_000L, 180_000L)
        withTimeout(timeoutMs) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                        .setEffects(
                            Effects(
                                emptyList(),
                                listOf(Presentation.createForHeight(height)),
                            )
                        )
                        .build()
                    val transformer = Transformer.Builder(context.applicationContext)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(
                            DefaultEncoderFactory.Builder(context.applicationContext)
                                .setRequestedVideoEncoderSettings(
                                    VideoEncoderSettings.Builder()
                                        .setBitrate(MessageVideoLimits.COMPRESSED_BITS_PER_SECOND.toInt())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                    val listener = object : Transformer.Listener {
                        override fun onCompleted(
                            composition: androidx.media3.transformer.Composition,
                            exportResult: ExportResult,
                        ) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: androidx.media3.transformer.Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            dest.delete()
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    MessageVideoException(MessageVideoException.FAILED)
                                )
                            }
                        }
                    }
                    transformer.addListener(listener)
                    transformer.start(edited, dest.absolutePath)
                    cont.invokeOnCancellation {
                        Handler(Looper.getMainLooper()).post { transformer.cancel() }
                    }
                }
            }
        }
    }

    private fun sourceByteCount(context: Context, uri: Uri): Long {
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }

    private fun copyUri(context: Context, uri: Uri, dest: File) {
        if (dest.exists()) dest.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw MessageVideoException(MessageVideoException.FAILED)
    }
}

fun formatMessageVideoDuration(ms: Int?): String {
    if (ms == null || ms <= 0) return ""
    val total = round(ms / 1000.0).toInt()
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
