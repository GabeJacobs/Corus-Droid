package fm.corus.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.core.content.FileProvider
import fm.corus.android.R
import fm.corus.android.data.model.ShareRecipient
import fm.corus.android.data.model.FlairStyle
import fm.corus.android.data.model.VinylStyle
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow

internal data class InstagramV2Subject(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val songLink: String,
    val outboundLink: String,
    val username: String? = null,
    val caption: String? = null,
    val avatarUrl: String? = null,
    val isFilm: Boolean = false,
    val isVerified: Boolean = false,
    val isClubMember: Boolean = false,
    val isBot: Boolean = false,
    val flairStyle: FlairStyle = FlairStyle.NONE,
    val isFirstPoster: Boolean = false,
    val isNewRelease: Boolean = false,
)

/** Pure palette math is shared by preview and export and tested with known artwork populations. */
internal object InstagramV2Palette {
    fun color(pixels: IntArray): Int {
        val bins = mutableMapOf<Int, DoubleArray>()
        for (pixel in pixels) {
            if ((pixel ushr 24) < 200) continue
            val r = ((pixel shr 16) and 255) / 255.0
            val g = ((pixel shr 8) and 255) / 255.0
            val b = (pixel and 255) / 255.0
            val key = (r * 7).toInt() * 64 + (g * 7).toInt() * 8 + (b * 7).toInt()
            val bin = bins.getOrPut(key) { DoubleArray(4) }
            bin[0]++; bin[1] += r; bin[2] += g; bin[3] += b
        }
        var best = 0.0
        var winner = 0xff444444.toInt()
        bins.toSortedMap().values.forEach { bin ->
            val r = bin[1] / bin[0]; val g = bin[2] / bin[0]; val b = bin[3] / bin[0]
            val max = maxOf(r, g, b); val min = minOf(r, g, b)
            val saturation = if (max == 0.0) 0.0 else (max - min) / max
            val score = bin[0] * (0.08 + saturation * saturation * 3) * (if (max > .12 && max < .96) 1.0 else .2)
            if (score > best) {
                best = score
                winner = (255 shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
            }
        }
        return winner
    }
    fun darken(color: Int): Int = (255 shl 24) or ((((color shr 16) and 255) * .55).toInt() shl 16) or
        ((((color shr 8) and 255) * .55).toInt() shl 8) or ((color and 255) * .55).toInt()
    fun luminance(color: Int): Double {
        fun linear(channel: Int): Double { val x = channel / 255.0; return if (x <= .04045) x / 12.92 else ((x + .055) / 1.055).pow(2.4) }
        return .2126 * linear((color shr 16) and 255) + .7152 * linear((color shr 8) and 255) + .0722 * linear(color and 255)
    }
    fun ink(top: Int, bottom: Int): Int {
        val low = minOf(luminance(top), luminance(bottom)); val high = maxOf(luminance(top), luminance(bottom))
        return if (1.05 / (high + .05) >= (low + .05) / .05) -1 else 0xff000000.toInt()
    }
}

private fun instagramV2TagWidth(context: Context, label: String): Float {
    val labelPaint = nunitoPaint(context, 27f, 800, Color.White.toArgb())
    return 18f + 10f + labelPaint.measureText(label) + 30f
}

/** Draws the same capsule colors, border, icon/label spacing, and vertical
 * alignment as the feed's FirstPosterBadge and NewReleaseBadge. */
private fun drawInstagramV2Tag(canvas: Canvas, context: Context, x: Float, centerY: Float, label: String, color: Int): Float {
    val width = instagramV2TagWidth(context, label)
    val rect = RectF(x, centerY - 23f, x + width, centerY + 23f)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 36 }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; alpha = 102; style = Paint.Style.STROKE; strokeWidth = 2.4f
    }
    canvas.drawRoundRect(rect, 23f, 23f, fill)
    canvas.drawRoundRect(rect, 23f, 23f, stroke)
    val iconX = x + 24f
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    if (label == "1ST") {
        canvas.drawRoundRect(RectF(iconX - 8f, centerY - 10f, iconX + 8f, centerY + 2f), 5f, 5f, iconPaint)
        canvas.drawRect(iconX - 2.5f, centerY + 1f, iconX + 2.5f, centerY + 9f, iconPaint)
        canvas.drawRect(iconX - 8f, centerY + 8f, iconX + 8f, centerY + 12f, iconPaint)
        iconPaint.style = Paint.Style.STROKE; iconPaint.strokeWidth = 3f
        canvas.drawArc(RectF(iconX - 14f, centerY - 8f, iconX - 2f, centerY + 5f), 85f, 190f, false, iconPaint)
        canvas.drawArc(RectF(iconX + 2f, centerY - 8f, iconX + 14f, centerY + 5f), -95f, 190f, false, iconPaint)
    } else {
        val flame = Path().apply {
            moveTo(iconX, centerY + 12f)
            cubicTo(iconX - 14f, centerY + 3f, iconX - 8f, centerY - 8f, iconX - 1f, centerY - 17f)
            cubicTo(iconX + 1f, centerY - 8f, iconX + 11f, centerY - 7f, iconX + 10f, centerY + 2f)
            cubicTo(iconX + 9f, centerY + 9f, iconX + 4f, centerY + 12f, iconX, centerY + 12f)
            close()
        }
        canvas.drawPath(flame, iconPaint)
    }
    val labelPaint = nunitoPaint(context, 27f, 800, color)
    canvas.drawText(label, x + 42f, centerY - (labelPaint.ascent() + labelPaint.descent()) / 2f, labelPaint)
    return width
}

private fun drawInstagramV2Flair(
    canvas: Canvas,
    context: Context,
    style: FlairStyle,
    centerX: Float,
    centerY: Float,
) {
    if (style == FlairStyle.NONE) return
    val accent = CorusColors.Accent.toArgb()
    if (style == FlairStyle.CORUS_LOGO) {
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo_no_background) ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(logo, null, RectF(centerX - 21f, centerY - 21f, centerX + 21f, centerY + 21f), paint)
        logo.recycle()
        return
    }
    val glyph = when (style) {
        FlairStyle.CHECKMARK -> "✓"
        FlairStyle.VINYL -> "◉"
        FlairStyle.HEART -> "♥"
        FlairStyle.SPARKLE -> "✦"
        FlairStyle.MOON -> "◒"
        FlairStyle.HEADPHONES -> "Ω"
        FlairStyle.BOLT -> "ϟ"
        FlairStyle.MUSIC_NOTE -> "♫"
        FlairStyle.PIANO -> "▥"
        FlairStyle.WAVEFORM -> "≋"
        else -> ""
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        textSize = 38f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    if (style == FlairStyle.CHECKMARK) {
        canvas.drawCircle(centerX, centerY, 18f, paint)
        paint.color = Color.White.toArgb(); paint.textSize = 25f
    }
    canvas.drawText(glyph, centerX, centerY - (paint.ascent() + paint.descent()) / 2f, paint)
}

internal fun renderInstagramV2(context: Context, subject: InstagramV2Subject, art: Bitmap, accent: Int,
                               layout: String, background: String, avatar: Bitmap? = null,
                               includesBackground: Boolean = true): Bitmap {
    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val useVinyl = !subject.isFilm && layout == "vinyl"
    val useFrame = !useVinyl
    var top = accent
    if (background == "gradient") while (InstagramV2Palette.luminance(top) > .18) top = InstagramV2Palette.darken(top)
    val bottom = if (background == "gradient") InstagramV2Palette.darken(top) else top
    val adaptiveInk = InstagramV2Palette.ink(top, bottom)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (includesBackground) {
        drawInstagramV2Background(canvas, art, top, bottom, background, protectsText = !useFrame, adaptiveInk = adaptiveInk, paint = paint)
    }
    val ink = if (useFrame || background == "frosted") -1 else adaptiveInk
    val textWidth = if (useFrame) 820 else 920
    val textX = ((1080 - textWidth) / 2).toFloat()
    fun textLayout(value: String, size: Float, weight: Int, lines: Int, width: Int = textWidth): StaticLayout {
        val textPaint = TextPaint(nunitoPaint(context, size, weight, ink))
        return StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
    }
    val showFlair = !subject.isBot && (subject.isVerified || subject.isClubMember) && subject.flairStyle != FlairStyle.NONE
    val authorAvailableWidth = textWidth - if (avatar == null) 0 else 102
    val usernameText = subject.username?.let { "@$it" }
    val usernameNaturalWidth = usernameText?.let { nunitoPaint(context, 40f, 800, ink).measureText(it) } ?: 0f
    val flairWidth = if (showFlair) 68f else 0f
    var tagSpace = authorAvailableWidth - usernameNaturalWidth - flairWidth - 12f
    val firstTagWidth = instagramV2TagWidth(context, "1ST") + 14f
    val newReleaseTagWidth = instagramV2TagWidth(context, "NEW RELEASE") + 14f
    val showFirstTag = subject.isFirstPoster && tagSpace >= firstTagWidth
    if (showFirstTag) tagSpace -= firstTagWidth
    val showNewReleaseTag = subject.isNewRelease && tagSpace >= newReleaseTagWidth
    val usernameWidth = minOf(
        authorAvailableWidth - flairWidth.toInt(),
        kotlin.math.ceil(usernameNaturalWidth.toDouble()).toInt() + 4,
    ).coerceAtLeast(1)
    val usernameBlock = usernameText?.let { textLayout(it, 40f, 800, 1, usernameWidth) }
    val titleBlock = textLayout(subject.title, 48f, 800, 2)
    val artistBlock = textLayout(subject.artist, 36f, 500, 2)
    val captionBlock = subject.caption?.takeIf { it.isNotBlank() }?.let { textLayout(it, 32f, 500, 5) }
    val visualWidth = when {
        subject.isFilm -> 560f
        useVinyl -> 1240f
        else -> 780f
    }
    val visualHeight = when {
        subject.isFilm -> visualWidth * 1.5f
        useVinyl -> visualWidth * VinylStyle.BLACK.canvasRatio
        else -> visualWidth
    }
    val vinylBottomTrim = if (useVinyl) visualWidth * (49f / 585f) else 0f
    val visualLayoutHeight = visualHeight - vinylBottomTrim
    val authorHeight = if (usernameBlock == null) 0 else if (useVinyl) 94 else 116
    val visualToTitleSpacing = if (useVinyl) 16 else 40
    val contentHeight = authorHeight + visualLayoutHeight.toInt() + visualToTitleSpacing +
        titleBlock.height + 14 + artistBlock.height + 14 + (captionBlock?.let { 8 + it.height + 14 } ?: 0)
    val frameBounds = instagramV2FrameBounds(contentHeight)
    var y = if (useFrame) frameBounds.top + 60f else ((1920f - contentHeight) / 2f).coerceAtLeast(180f)
    if (useFrame && includesBackground) {
        paint.color = 0xd1121212.toInt()
        paint.alpha = 255
        canvas.drawRoundRect(RectF(frameBounds), 36f, 36f, paint)
    }
    fun drawText(block: StaticLayout, x: Float = textX) {
        canvas.save(); canvas.translate(x, y); block.draw(canvas); canvas.restore()
        y += block.height + 14
    }
    usernameBlock?.let {
        val usernameTop = y
        val usernameX: Float
        if (avatar != null) {
            canvas.save()
            val path = android.graphics.Path().apply { addCircle(textX + 40f, usernameTop + 40f, 40f, android.graphics.Path.Direction.CW) }
            canvas.clipPath(path)
            canvas.drawBitmap(avatar, null, RectF(textX, usernameTop, textX + 80f, usernameTop + 80f), paint)
            canvas.restore()
            usernameX = textX + 102f
            drawText(it, usernameX)
        } else {
            usernameX = textX
            drawText(it, usernameX)
        }
        var accessoryX = usernameX + it.getLineWidth(0) + if (showFlair) 22f else 5f
        val accessoryCenterY = usernameTop + 40f
        if (showFlair) {
            drawInstagramV2Flair(canvas, context, subject.flairStyle, accessoryX + 18f, accessoryCenterY)
            accessoryX += 46f
        }
        if (showFirstTag) {
            accessoryX += drawInstagramV2Tag(canvas, context, accessoryX, accessoryCenterY, "1ST", 0xffffc207.toInt()) + 14f
        }
        if (showNewReleaseTag) {
            drawInstagramV2Tag(canvas, context, accessoryX, accessoryCenterY, "NEW RELEASE", 0xff9e59f2.toInt())
        }
        y = usernameTop + authorHeight
    }
    if (useVinyl) {
        drawVinylComposite(canvas, context, VinylStyle.BLACK, art, visualWidth, (1080f - visualWidth) / 2f, y + visualHeight / 2f, paint)
        y += visualLayoutHeight
    } else {
        val scale = minOf(visualWidth / art.width, visualWidth / art.height)
        val w = art.width * scale; val h = art.height * scale
        canvas.drawBitmap(art, null, RectF((1080 - w) / 2, y + (visualHeight - h) / 2, (1080 + w) / 2, y + (visualHeight + h) / 2), paint)
        y += visualHeight
    }
    y += visualToTitleSpacing
    drawText(titleBlock)
    drawText(artistBlock)
    captionBlock?.let { y += 8; drawText(it) }
    canvas.drawText("corus", if (useFrame) 970f else 1000f, if (useFrame) frameBounds.bottom - 45f else 1665f, nunitoPaint(context, 42f, 900, ink, Paint.Align.RIGHT))
    return bitmap
}

private fun drawInstagramV2Background(
    canvas: Canvas, art: Bitmap, top: Int, bottom: Int, background: String,
    protectsText: Boolean, adaptiveInk: Int, paint: Paint,
) {
    paint.alpha = 255
    paint.shader = LinearGradient(0f, 0f, 0f, 1920f, top, bottom, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 1080f, 1920f, paint)
    paint.shader = null
    if (background == "frosted") {
        val blurred = bakeInstagramV2FrostedBackground(art)
        canvas.drawBitmap(blurred, null, RectF(0f, 0f, 1080f, 1920f), paint)
        blurred.recycle()
        paint.color = 0x57000000
        canvas.drawRect(0f, 0f, 1080f, 1920f, paint)
    }
    paint.alpha = 255
    paint.shader = null
}

/**
 * Produces the same smooth, artwork-led wash as the player backdrop and iOS
 * Story renderer. The old implementation enlarged a 20 x 36 thumbnail
 * directly to Story size, leaving obvious square color blocks. A three-pass
 * box blur at quarter resolution approximates iOS's large Gaussian blur while
 * keeping preview re-renders inexpensive.
 */
internal fun bakeInstagramV2FrostedBackground(
    art: Bitmap,
    width: Int = 270,
    height: Int = 480,
): Bitmap {
    val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val targetRatio = width.toFloat() / height
    val sourceRatio = art.width.toFloat() / art.height.coerceAtLeast(1)
    val source = if (sourceRatio > targetRatio) {
        val cropWidth = (art.height * targetRatio).toInt().coerceAtLeast(1)
        val left = (art.width - cropWidth) / 2
        Rect(left, 0, left + cropWidth, art.height)
    } else {
        val cropHeight = (art.width / targetRatio).toInt().coerceAtLeast(1)
        val top = (art.height - cropHeight) / 2
        Rect(0, top, art.width, top + cropHeight)
    }
    Canvas(target).drawBitmap(
        art,
        source,
        Rect(0, 0, width, height),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )

    var pixels = IntArray(width * height).also { target.getPixels(it, 0, width, 0, 0, width, height) }
    repeat(3) { pixels = boxBlurInstagramV2(pixels, width, height, radius = 12) }
    target.setPixels(pixels, 0, width, 0, 0, width, height)
    return target
}

private fun boxBlurInstagramV2(source: IntArray, width: Int, height: Int, radius: Int): IntArray {
    if (radius <= 0) return source.copyOf()
    val horizontal = IntArray(source.size)
    val output = IntArray(source.size)
    val divisor = radius * 2 + 1

    for (y in 0 until height) {
        val row = y * width
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val color = source[row + offset.coerceIn(0, width - 1)]
            alpha += color ushr 24
            red += color shr 16 and 255
            green += color shr 8 and 255
            blue += color and 255
        }
        for (x in 0 until width) {
            horizontal[row + x] = (alpha / divisor shl 24) or (red / divisor shl 16) or
                (green / divisor shl 8) or (blue / divisor)
            val outgoing = source[row + (x - radius).coerceIn(0, width - 1)]
            val incoming = source[row + (x + radius + 1).coerceIn(0, width - 1)]
            alpha += (incoming ushr 24) - (outgoing ushr 24)
            red += (incoming shr 16 and 255) - (outgoing shr 16 and 255)
            green += (incoming shr 8 and 255) - (outgoing shr 8 and 255)
            blue += (incoming and 255) - (outgoing and 255)
        }
    }

    for (x in 0 until width) {
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val color = horizontal[offset.coerceIn(0, height - 1) * width + x]
            alpha += color ushr 24
            red += color shr 16 and 255
            green += color shr 8 and 255
            blue += color and 255
        }
        for (y in 0 until height) {
            output[y * width + x] = (alpha / divisor shl 24) or (red / divisor shl 16) or
                (green / divisor shl 8) or (blue / divisor)
            val outgoing = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
            val incoming = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            alpha += (incoming ushr 24) - (outgoing ushr 24)
            red += (incoming shr 16 and 255) - (outgoing shr 16 and 255)
            green += (incoming shr 8 and 255) - (outgoing shr 8 and 255)
            blue += (incoming and 255) - (outgoing and 255)
        }
    }
    return output
}

internal fun instagramV2FrameBounds(contentHeight: Int): Rect {
    val height = (60 + contentHeight + 115).coerceIn(1120, 1500)
    val top = (1920 - height) / 2
    return Rect(60, top, 1020, top + height)
}

internal fun instagramV2FrameBoundsForSubject(context: Context, subject: InstagramV2Subject): Rect {
    val textWidth = 820
    fun textHeight(value: String, size: Float, weight: Int, lines: Int, width: Int = textWidth): Int {
        val textPaint = TextPaint(nunitoPaint(context, size, weight, -1))
        return StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build().height
    }
    val usernameHeight = if (subject.username == null) 0 else 116
    val visualHeight = if (subject.isFilm) 840 else 780
    val titleHeight = textHeight(subject.title, 48f, 800, 2)
    val artistHeight = textHeight(subject.artist, 36f, 500, 2)
    val captionHeight = subject.caption?.takeIf { it.isNotBlank() }?.let { 8 + textHeight(it, 32f, 500, 5) + 14 } ?: 0
    return instagramV2FrameBounds(usernameHeight + visualHeight + 40 + titleHeight + 14 + artistHeight + 14 + captionHeight)
}

internal fun renderInstagramV2FrameBackground(art: Bitmap, accent: Int, background: String): Bitmap =
    Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).also { bitmap ->
        var top = accent
        if (background == "gradient") while (InstagramV2Palette.luminance(top) > .18) top = InstagramV2Palette.darken(top)
        val bottom = if (background == "gradient") InstagramV2Palette.darken(top) else top
        val ink = InstagramV2Palette.ink(top, bottom)
        drawInstagramV2Background(Canvas(bitmap), art, top, bottom, background, protectsText = false, adaptiveInk = ink, paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

internal fun renderInstagramV2FrameSticker(story: Bitmap, bounds: Rect): Bitmap {
    val sticker = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sticker)
    val path = android.graphics.Path().apply {
        addRoundRect(RectF(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat()), 36f, 36f, android.graphics.Path.Direction.CW)
    }
    canvas.clipPath(path)
    canvas.drawBitmap(story, bounds, Rect(0, 0, bounds.width(), bounds.height()), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return sticker
}

/** Crops a transparent story-sized Vinyl foreground to its visible pixels so
 * Instagram treats the composition as a practical movable/resizable sticker. */
internal fun cropInstagramV2TransparentSticker(source: Bitmap, padding: Int = 24): Bitmap? {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    pixels.forEachIndexed { index, pixel ->
        if (android.graphics.Color.alpha(pixel) > 8) {
            val x = index % width
            val y = index / width
            minX = minOf(minX, x); minY = minOf(minY, y)
            maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
        }
    }
    if (maxX < minX || maxY < minY) return null
    val left = (minX - padding).coerceAtLeast(0)
    val top = (minY - padding).coerceAtLeast(0)
    val right = (maxX + padding).coerceAtMost(width - 1)
    val bottom = (maxY + padding).coerceAtMost(height - 1)
    return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
}

private fun downloadInstagramV2Bitmap(url: String): Bitmap? = try {
    java.net.URL(url).openConnection().apply { connectTimeout = 10_000; readTimeout = 10_000 }
        .getInputStream().use { android.graphics.BitmapFactory.decodeStream(it) }
} catch (_: Exception) { null }

@Composable
internal fun PostToInstagramV2Sheet(
    subject: InstagramV2Subject,
    recentContacts: List<ShareRecipient>, searchResults: List<ShareRecipient>,
    isSearching: Boolean, isLoadingContacts: Boolean, instagramShareEnabled: Boolean,
    onSearchQueryChange: (String) -> Unit, onSendToUser: (String, String) -> Unit,
    onDismiss: () -> Unit, onRepost: (() -> Unit)? = null, onAnalyticsLog: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    val prefs = remember { context.getSharedPreferences("instagram_v2", Context.MODE_PRIVATE) }
    var layout by remember {
        val savedLayout = prefs.getString("layout", "cover") ?: "cover"
        mutableStateOf(if (subject.isFilm) "cover" else savedLayout)
    }
    var background by remember {
        val saved = prefs.getString("background", null)
        mutableStateOf(when (saved) {
            "gradient" -> "gradient"
            "solid", "artwork", "frame" -> "solid"
            else -> "frosted"
        })
    }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ShareRecipient?>(null) }
    var message by remember { mutableStateOf("") }
    var avatar by remember(subject) { mutableStateOf<Bitmap?>(null) }
    var artwork by remember(subject) { mutableStateOf<Bitmap?>(null) }
    var accent by remember(subject) { mutableIntStateOf(0xff444444.toInt()) }
    var image by remember(subject) { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var includeCaption by rememberSaveable(subject.outboundLink) { mutableStateOf(true) }
    var includeTags by rememberSaveable(subject.outboundLink) { mutableStateOf(true) }
    var optionsExpanded by remember { mutableStateOf(false) }
    val renderedSubject = subject.copy(
        caption = subject.caption.takeIf { includeCaption },
        isFirstPoster = subject.isFirstPoster && includeTags,
        isNewRelease = subject.isNewRelease && includeTags,
    )
    val contacts = remember(recentContacts, selected) { selected?.let { s -> listOf(s) + recentContacts.filter { it.id != s.id } } ?: recentContacts }
    LaunchedEffect(subject, retry) {
        loading = true; error = null
        image = null
        // Fetch both author assets together and expose them as one complete
        // preview. Previously artwork rendered first, then the avatar update
        // rebuilt the author row and made its flair appear to arrive late.
        val (art, loadedAvatar) = coroutineScope {
            val artTask = async(Dispatchers.IO) { subject.artworkUrl?.let { downloadInstagramV2Bitmap(it) } }
            val avatarTask = async(Dispatchers.IO) { subject.avatarUrl?.let { downloadInstagramV2Bitmap(it) } }
            artTask.await() to avatarTask.await()
        }
        artwork = art
        avatar = loadedAvatar
        if (art != null) {
            accent = withContext(Dispatchers.Default) {
                val small = Bitmap.createScaledBitmap(art, 48, 48, true)
                val pixels = IntArray(48 * 48); small.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                val result = InstagramV2Palette.color(pixels)
                if (small !== art) small.recycle()
                result
            }
        } else error = context.getString(R.string.instagram_v2_artwork_error)
        loading = false
    }
    LaunchedEffect(artwork, avatar, accent, layout, background, renderedSubject, loading) {
        image = null
        if (loading) return@LaunchedEffect
        prefs.edit().putString("layout", layout).putString("background", background).apply()
        val art = artwork ?: return@LaunchedEffect
        image = withContext(Dispatchers.Default) { renderInstagramV2(context, renderedSubject, art, accent, layout, background, avatar) }
    }
    LaunchedEffect(searching) { if (searching) requester.requestFocus() }
    LaunchedEffect(copied) { if (copied && !sharing) { delay(2500); copied = false } }

    fun copyLink(link: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.share_post_clip_label), link))
        copied = true
    }
    fun shareImage(instagram: Boolean) {
        val rendered = image ?: return
        val art = artwork ?: return
        if (sharing) return
        sharing = true
        scope.launch {
            try {
                val (uri, stickerUri) = withContext(Dispatchers.IO) {
                    val useMovableSticker = instagram
                    val backgroundBitmap = if (useMovableSticker) renderInstagramV2FrameBackground(art, accent, background) else rendered
                    val stickerBitmap = when {
                        !useMovableSticker -> null
                        subject.isFilm || layout == "cover" ->
                            renderInstagramV2FrameSticker(rendered, instagramV2FrameBoundsForSubject(context, renderedSubject))
                        else -> {
                            val foreground = renderInstagramV2(
                                context, renderedSubject, art, accent, "vinyl", background, avatar,
                                includesBackground = false,
                            )
                            cropInstagramV2TransparentSticker(foreground).also {
                                if (it !== foreground) foreground.recycle()
                            }
                        }
                    }
                    val backgroundFile = File.createTempFile("instagram_v2_background_", ".png", context.cacheDir)
                    backgroundFile.outputStream().use { backgroundBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val backgroundUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", backgroundFile)
                    val movableStickerUri = stickerBitmap?.let { sticker ->
                        val stickerFile = File.createTempFile("instagram_v2_sticker_", ".png", context.cacheDir)
                        stickerFile.outputStream().use { sticker.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        sticker.recycle()
                        FileProvider.getUriForFile(context, "${context.packageName}.provider", stickerFile)
                    }
                    if (backgroundBitmap !== rendered) backgroundBitmap.recycle()
                    backgroundUri to movableStickerUri
                }
                if (instagram) {
                    copyLink(subject.songLink)
                    delay(1100)
                    val intent = buildAddToStoryIntent(uri, subject.songLink, context.packageName, stickerUri)
                    runCatching { context.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    stickerUri?.let { movableUri ->
                        runCatching { context.grantUriPermission("com.instagram.android", movableUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    }
                    context.startActivity(intent)
                    onAnalyticsLog?.invoke("instagram_stories")
                } else {
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.instagram_v2_share_image)))
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error = context.getString(R.string.instagram_v2_share_error) }
            finally { sharing = false; copied = false }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val safeTop = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .asPaddingValues()
        .calculateTopPadding() + 12.dp
    val searchHeight = (maxHeight - safeTop).coerceAtLeast(0.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (searching) Modifier.height(searchHeight) else Modifier.fillMaxHeight(.94f))
            .imePadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(32.dp)) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f), RoundedCornerShape(50)),
            )
        }
        if (searching) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it; onSearchQueryChange(it) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .focusRequester(requester),
                    singleLine = true,
                    textStyle = CorusFont.body.copy(color = CorusColors.Text),
                    cursorBrush = SolidColor(CorusColors.Accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Search,
                    ),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = CorusColors.Tertiary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text(stringResource(R.string.share_post_search_placeholder), style = CorusFont.body, color = CorusColors.Tertiary)
                                innerTextField()
                            }
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; onSearchQueryChange("") }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Cancel, contentDescription = stringResource(R.string.share_post_cd_clear), tint = CorusColors.Tertiary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    },
                )
                TextButton(onClick = { searching = false; query = ""; onSearchQueryChange(""); focus.clearFocus() }) {
                    Text(stringResource(R.string.instagram_v2_done), style = CorusFont.bodyMedium, color = CorusColors.Accent)
                }
            }
            selected?.let { Text(it.username, style = CorusFont.captionMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            LazyColumn(Modifier.weight(1f)) {
                val hasQuery = query.isNotBlank()
                if (hasQuery && isSearching) item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    val recipients = if (hasQuery) searchResults else contacts
                    if (!hasQuery && isLoadingContacts && recipients.isEmpty()) {
                        items(4) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(CorusColors.Skeleton))
                                Spacer(Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(width = 110.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).background(CorusColors.Skeleton))
                                    Box(Modifier.size(width = 70.dp, height = 10.dp).clip(RoundedCornerShape(4.dp)).background(CorusColors.Skeleton))
                                }
                            }
                        }
                    } else if (hasQuery && recipients.isEmpty()) {
                        item { Text(stringResource(R.string.share_post_no_results), style = CorusFont.body, color = CorusColors.Secondary, modifier = Modifier.fillMaxWidth().padding(top = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                    items(recipients, key = { it.id }) { recipient ->
                        ShareUserRow(recipient, selected?.id == recipient.id, showRemoveAffordance = true) { selected = if (selected?.id == recipient.id) null else recipient }
                    }
                }
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Let the portrait preview use more of the available space while
                // keeping room for the layout, background, recipient, and action rows.
                val previewWidth = minOf(maxWidth * .68f, maxHeight * (9f / 16f))
                Box(
                    Modifier.width(previewWidth).aspectRatio(9f / 16f),
                    contentAlignment = Alignment.Center,
                ) {
                    image?.let {
                        Image(it.asImageBitmap(), stringResource(R.string.instagram_v2_preview), modifier = Modifier.fillMaxSize())
                    } ?: if (loading || artwork != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.instagram_v2_retry)) }
                    }
                    if (!subject.caption.isNullOrBlank() || subject.isFirstPoster || subject.isNewRelease) {
                        Box(Modifier.align(Alignment.TopEnd).offset(x = 22.dp, y = (-22).dp)) {
                            Box(
                                modifier = Modifier.size(44.dp).clickable { optionsExpanded = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier.size(30.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        stringResource(R.string.instagram_v2_options),
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            DropdownMenu(expanded = optionsExpanded, onDismissRequest = { optionsExpanded = false }) {
                                if (!subject.caption.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.instagram_v2_show_caption)) },
                                        leadingIcon = { Checkbox(checked = includeCaption, onCheckedChange = null) },
                                        onClick = { includeCaption = !includeCaption },
                                    )
                                }
                                if (subject.isFirstPoster || subject.isNewRelease) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.instagram_v2_show_tags)) },
                                        leadingIcon = { Checkbox(checked = includeTags, onCheckedChange = null) },
                                        onClick = { includeTags = !includeTags },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.instagram_v2_preview_label),
                style = CorusFont.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 12.dp),
            )
            if (!subject.isFilm) {
                InstagramV2LayoutPicker(
                    layout = layout,
                    enabled = !sharing,
                    onLayoutChange = { layout = it },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                listOf(
                    "frosted" to R.string.instagram_v2_frosted,
                    "solid" to R.string.instagram_v2_solid,
                    "gradient" to R.string.instagram_v2_gradient,
                ).forEach { (value, label) ->
                    InstagramV2BackgroundOption(
                        value = value,
                        label = stringResource(label),
                        selected = background == value,
                        enabled = !sharing,
                        accent = accent,
                        artwork = artwork,
                        onClick = { background = value },
                    )
                }
            }
            error?.let { Text(it, style = CorusFont.caption, modifier = Modifier.padding(horizontal = 16.dp)) }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.width(80.dp).clickable { searching = true },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(25.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.share_post_search_placeholder), style = CorusFont.caption)
                    }
                }
                if (isLoadingContacts && contacts.isEmpty()) {
                    items(4) {
                        Box(Modifier.width(80.dp), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.size(72.dp).background(CorusColors.Skeleton, CircleShape))
                        }
                    }
                } else {
                    items(contacts, key = { it.id }) { recipient ->
                        Box(Modifier.width(80.dp)) { ShareContactCell(recipient, selected?.id == recipient.id, showRemoveAffordance = true) { selected = if (selected?.id == recipient.id) null else recipient } }
                    }
                }
            }
        }
        if (copied) {
            Column(Modifier.fillMaxWidth().background(Color(0xff202020)).padding(12.dp)) {
                Text(stringResource(R.string.instagram_v2_copied), color = Color.White, style = CorusFont.bodyMedium)
                if (sharing) Text(stringResource(R.string.instagram_v2_paste_hint), color = Color.White, style = CorusFont.caption)
            }
        }
        if (selected != null) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(value = message, onValueChange = { message = it }, modifier = Modifier.weight(1f), placeholder = { Text(stringResource(R.string.share_post_message_placeholder)) })
                TextButton(enabled = !sent, onClick = { selected?.let { sent = true; onAnalyticsLog?.invoke("direct_message"); onSendToUser(it.id, message); onDismiss() } }) { Text(stringResource(R.string.share_post_send)) }
            }
        } else if (!searching) {
            HorizontalDivider()
            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (onRepost != null) item { ShareActionButton(icon = Icons.Default.Repeat, label = stringResource(R.string.share_post_repost), isProminent = true) { onAnalyticsLog?.invoke("repost"); onRepost() } }
                if (instagramShareEnabled && isInstagramAvailable(context)) item {
                    InstagramShareButton(isLoading = sharing, onClick = { if (image != null) shareImage(true) })
                }
                if (isWhatsAppAvailable(context)) item {
                    ShareActionButton(label = stringResource(R.string.share_post_whatsapp), painter = painterResource(R.drawable.whatsapp_logo), backgroundColor = Color(0xff25D366), iconTint = Color.White) {
                        onAnalyticsLog?.invoke("whatsapp")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(subject.outboundLink)}"))) }
                    }
                }
                item { XShareButton {
                    onAnalyticsLog?.invoke("x")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/intent/tweet?text=${Uri.encode(subject.title + " by " + subject.artist + " on @corusapp")}&url=${Uri.encode(subject.outboundLink)}"))) }
                } }
                item { ShareActionButton(icon = Icons.Default.Share, label = stringResource(R.string.share_post_share_link)) {
                    onAnalyticsLog?.invoke("share_link")
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, subject.outboundLink) }, context.getString(R.string.share_post_share_chooser)))
                } }
                item { ShareActionButton(icon = Icons.Default.ContentCopy, label = stringResource(R.string.share_post_copy_link)) { copyLink(subject.outboundLink); onAnalyticsLog?.invoke("copy_link") } }
                if (image != null) item {
                    ShareActionButton(icon = Icons.Default.Image, label = stringResource(R.string.instagram_v2_share_image)) {
                        onAnalyticsLog?.invoke("share_image")
                        shareImage(false)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun InstagramV2LayoutPicker(
    layout: String,
    enabled: Boolean,
    onLayoutChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .width(260.dp)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
    ) {
        listOf(
            "cover" to stringResource(R.string.instagram_v2_cover),
            "vinyl" to stringResource(R.string.instagram_v2_vinyl),
        ).forEach { (value, label) ->
            val selected = layout == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(
                        width = if (selected) 1.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = enabled) { onLayoutChange(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = CorusFont.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun InstagramV2BackgroundOption(
    value: String,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    accent: Int,
    artwork: Bitmap?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 64.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) CorusColors.Accent else Color.Transparent,
                    shape = CircleShape,
                )
                .padding(4.dp)
                .clip(CircleShape),
        ) {
            val color = Color(accent)
            if (value == "frosted" && artwork != null) {
                Image(
                    artwork.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().scale(1.12f).blur(2.5.dp),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .34f)))
            } else {
                val end = if (value == "gradient") Color(InstagramV2Palette.darken(accent)) else color
                Box(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color, end)),
                    ),
                )
            }
        }
        Text(
            text = label,
            style = CorusFont.captionMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
