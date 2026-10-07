package fm.corus.android.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.content.FileProvider
import fm.corus.android.localization.CorusStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

internal fun profileV2ShareLink(profile: ShareProfileSubject, destination: String? = null, v2: Boolean = true,
    background: ProfileStoryBackground? = null): String =
    Uri.parse("https://corus.fm").buildUpon().appendPath("u").appendPath(profile.username).apply {
        if (background == ProfileStoryBackground.LIGHT) appendQueryParameter("theme", "light")
        if (v2) appendQueryParameter("profile_sharing_v2", "true")
        destination?.let { appendQueryParameter("ref", it) }
    }.build().toString()

internal data class ProfileSharePayload(val background: Uri, val sticker: Uri? = null,
    val fallback: Uri = background, val mime: String, val format: String)

private fun profileShareUri(context: Context, file: File) = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

private fun saveProfileShareBitmap(context: Context, bitmap: Bitmap, jpeg: Boolean = false): Uri {
    val file = File.createTempFile("corus_profile_share_", if (jpeg) ".jpg" else ".png", context.cacheDir)
    try {
        file.outputStream().use { check(bitmap.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, if (jpeg) 92 else 100, it)) }
        return profileShareUri(context, file)
    } catch (error: Throwable) { file.delete(); throw error }
    finally { bitmap.recycle() }
}

internal suspend fun exportProfileShare(context: Context, profile: ShareProfileSubject, art: PreparedProfileShareArt,
    layout: ProfileStoryGridSize, background: ProfileStoryBackground, weather: ProfileStoryWeather, x: Boolean): ProfileSharePayload =
    withContext(Dispatchers.Default) {
        if (x) return@withContext ProfileSharePayload(saveProfileShareBitmap(context, renderProfileShareX(context, profile, art, layout, background), true),
            mime = "image/jpeg", format = "x_image")
        if (weather != ProfileStoryWeather.NONE) {
            val card = renderProfileShareStory(context, profile, art, layout, background)
            val file = File.createTempFile("corus_profile_share_", ".mp4", context.cacheDir)
            try {
                encodeProfileStoryVideo(card, weather, file)
                ProfileSharePayload(profileShareUri(context, file), mime = "video/mp4", format = "story_video")
            } catch (error: Throwable) { file.delete(); throw error }
            finally { card.recycle() }
        } else if (layout.artworkLimit == 28 && profile.featuredMoviePosterUrl == null) {
            ProfileSharePayload(saveProfileShareBitmap(context, renderProfileShareStory(context, profile, art, layout, background)),
                mime = "image/png", format = "story_image")
        } else {
            val bg = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply { eraseColor(background.color) }
            val bgUri = saveProfileShareBitmap(context, bg)
            val sticker = saveProfileShareBitmap(context, renderProfileShareStory(context, profile, art, layout, background,
                transparent = true, compactForeground = true))
            val flat = saveProfileShareBitmap(context, renderProfileShareStory(context, profile, art, layout, background))
            ProfileSharePayload(bgUri, sticker, flat, "image/png", "story_layers")
        }
    }

internal fun profileInstagramIntent(payload: ProfileSharePayload, link: String, source: String): Intent =
    buildAddToStoryIntent(payload.background, link, source, payload.sticker).apply {
        setPackage("com.instagram.android")
        setDataAndType(payload.background, payload.mime)
        clipData = ClipData.newRawUri("background", payload.background).apply {
            payload.sticker?.let { addItem(ClipData.Item(it)) }
        }
    }

internal fun profileXSendIntent(caption: String, link: String, image: Uri? = null): Intent = Intent(Intent.ACTION_SEND).apply {
    type = if (image == null) "text/plain" else "image/jpeg"
    setPackage("com.twitter.android")
    putExtra(Intent.EXTRA_TEXT, "$caption\n$link")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    image?.let {
        putExtra(Intent.EXTRA_STREAM, it)
        clipData = ClipData.newRawUri("profile", it)
    }
}

/** Accepted means Android accepted the intent, never that the user published. */
internal fun handoffProfileShare(context: Context, payload: ProfileSharePayload, link: String, x: Boolean): Boolean {
    val direct = if (x) profileXSendIntent(context.getString(CorusStrings.profile_share_x_caption), link, payload.background)
        else profileInstagramIntent(payload, link, context.packageName)
    val pkg = if (x) "com.twitter.android" else "com.instagram.android"
    listOfNotNull(payload.background, payload.sticker).forEach { uri ->
        runCatching { context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    return try { context.startActivity(direct); true }
    catch (_: Exception) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = payload.mime
            putExtra(Intent.EXTRA_STREAM, payload.fallback)
            putExtra(Intent.EXTRA_TEXT, if (x) "${context.getString(CorusStrings.profile_share_x_caption)}\n$link" else link)
            clipData = ClipData.newRawUri("profile", payload.fallback)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { context.startActivity(Intent.createChooser(fallback, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
        catch (_: Exception) { false }
    }
}

internal fun shareFlaggedProfileLinkToX(context: Context, profile: ShareProfileSubject): Boolean {
    val caption = context.getString(CorusStrings.profile_share_x_caption)
    val link = profileV2ShareLink(profile, "x", v2 = false)
    return try { context.startActivity(profileXSendIntent(caption, link)); true }
    catch (_: Exception) {
        try {
            val uri = Uri.parse("https://twitter.com/intent/tweet").buildUpon()
                .appendQueryParameter("text", caption).appendQueryParameter("url", link).build()
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: Exception) { false }
    }
}

/** One reusable frame; no array of 600 full-resolution images. Flexible YUV honors
 * each encoder plane's row/pixel stride. Cancellation releases encoder and muxer. */
internal suspend fun encodeProfileStoryVideo(card: Bitmap, weather: ProfileStoryWeather, file: File) {
    require(card.width == 1080 && card.height == 1920 && weather != ProfileStoryWeather.NONE)
    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    var muxer: MediaMuxer? = null
    var codecStarted = false; var muxerStarted = false
    val frame = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    try {
        val capabilities = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
        val fps = if (capabilities?.areSizeAndRateSupported(1080, 1920, 60.0) == true) 60 else 30
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1080, 1920).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start(); codecStarted = true
        muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val writer = muxer
        val info = MediaCodec.BufferInfo()
        var track = -1; var outputEnded = false
        val pixels = IntArray(1080 * 1920)
        val scene = ProfileStoryWeatherScene(weather)
        val canvas = Canvas(frame)
        var nextFrame = 0
        var lastProgress = System.nanoTime()
        while (!outputEnded) {
            currentCoroutineContext().ensureActive()
            if (nextFrame <= fps * 10) {
                val input = codec.dequeueInputBuffer(10_000)
                if (input >= 0) {
                    if (nextFrame == fps * 10) {
                        codec.queueInputBuffer(input, 0, 0, 10_000_000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        canvas.drawBitmap(card, 0f, 0f, null)
                        scene.draw(canvas, 1080f, 1920f)
                        frame.getPixels(pixels, 0, 1080, 0, 0, 1080, 1920)
                        val image = requireNotNull(codec.getInputImage(input)) { "Encoder has no writable YUV image" }
                        writeProfileYuv(pixels, image, 1080, 1920)
                        image.close()
                        codec.queueInputBuffer(input, 0, 1080 * 1920 * 3 / 2, nextFrame * 1_000_000L / fps, 0)
                        scene.advance(1f / fps)
                    }
                    nextFrame++; lastProgress = System.nanoTime()
                }
            }
            var output = codec.dequeueOutputBuffer(info, 10_000)
            while (output != MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    check(!muxerStarted); track = writer.addTrack(codec.outputFormat); writer.start(); muxerStarted = true
                } else if (output >= 0) {
                    val buffer = requireNotNull(codec.getOutputBuffer(output))
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        check(muxerStarted)
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        writer.writeSampleData(track, buffer, info)
                    }
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(output, false)
                    lastProgress = System.nanoTime()
                }
                if (outputEnded) break
                output = codec.dequeueOutputBuffer(info, 0)
            }
            check(System.nanoTime() - lastProgress < 30_000_000_000L) { "Video encoder timed out" }
        }
        check(muxerStarted)
    } finally {
        if (codecStarted) runCatching { codec.stop() }
        codec.release()
        if (muxerStarted) runCatching { muxer?.stop() }
        muxer?.release(); frame.recycle()
    }
}

internal fun writeProfileYuv(pixels: IntArray, image: Image, width: Int, height: Int) {
    val planes = image.planes
    val offsets = planes.map { it.buffer.position() }
    for (y in 0 until height) for (x in 0 until width) {
        val color = pixels[y * width + x]
        val r = color shr 16 and 255; val g = color shr 8 and 255; val b = color and 255
        val yy = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
        planes[0].buffer.put(offsets[0] + y * planes[0].rowStride + x * planes[0].pixelStride, yy.coerceIn(0, 255).toByte())
        if (x % 2 == 0 && y % 2 == 0) {
            val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
            val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
            planes[1].buffer.put(offsets[1] + y / 2 * planes[1].rowStride + x / 2 * planes[1].pixelStride, u.coerceIn(0, 255).toByte())
            planes[2].buffer.put(offsets[2] + y / 2 * planes[2].rowStride + x / 2 * planes[2].pixelStride, v.coerceIn(0, 255).toByte())
        }
    }
}
