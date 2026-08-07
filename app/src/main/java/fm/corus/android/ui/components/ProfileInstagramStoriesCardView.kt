package fm.corus.android.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import fm.corus.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.floor
import kotlin.math.min

/**
 * Vertical (1080×1920) profile card purpose-built for Instagram Stories.
 * Mirrors iOS `ProfileInstagramStoriesCardView.swift`.
 */
internal data class ProfileStoriesGridLayout(
    val columns: Int,
    val rows: Int,
    val displayCount: Int,
)

internal fun profileStoriesGridLayout(count: Int): ProfileStoriesGridLayout = when (count) {
    0 -> ProfileStoriesGridLayout(0, 0, 0)
    1 -> ProfileStoriesGridLayout(1, 1, 1)
    2 -> ProfileStoriesGridLayout(2, 1, 2)
    3 -> ProfileStoriesGridLayout(3, 1, 3)
    4 -> ProfileStoriesGridLayout(2, 2, 4)
    5, 6 -> ProfileStoriesGridLayout(2, 3, count)
    7, 8 -> ProfileStoriesGridLayout(2, 4, count)
    else -> ProfileStoriesGridLayout(3, 3, min(count, 9))
}

private data class ProfileStoriesPalette(
    val ink: Int,
    val muted: Int,
    val accent: Int,
    val surface: Int,
    val backdrop: Int,
) {
    companion object {
        fun forTheme(theme: ShareCardTheme) = when (theme) {
            ShareCardTheme.LIGHT -> ProfileStoriesPalette(
                ink = android.graphics.Color.parseColor("#1A1A2E"),
                muted = android.graphics.Color.parseColor("#727276"),
                accent = android.graphics.Color.parseColor("#6495ED"),
                surface = android.graphics.Color.parseColor("#F8F8FA"),
                backdrop = android.graphics.Color.WHITE,
            )
            ShareCardTheme.DARK -> ProfileStoriesPalette(
                ink = android.graphics.Color.parseColor("#F5F5F7"),
                muted = android.graphics.Color.parseColor("#9A9AA0"),
                accent = android.graphics.Color.parseColor("#6495ED"),
                surface = android.graphics.Color.parseColor("#1C1C1E"),
                backdrop = android.graphics.Color.BLACK,
            )
        }
    }
}

suspend fun generateProfileStoriesCardBitmap(
    context: Context,
    profile: ShareProfileSubject,
    theme: ShareCardTheme,
): Bitmap = withContext(Dispatchers.IO) {
    val canvasWidth = 1080
    val canvasHeight = 1920
    val hPad = 72f
    val headerTop = 200f
    val footerBottom = 220f
    val spacer = 36f
    val brandMarkYOffset = 5f

    val palette = ProfileStoriesPalette.forTheme(theme)
    val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(palette.backdrop)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    val avatarBitmap = profile.avatarUrl?.takeIf { it.isNotBlank() }?.let { downloadShareBitmap(it) }
    val artworkUrls = profile.artworkUrls
    val artworkBitmaps = artworkUrls.take(9).mapNotNull { downloadShareBitmap(it) }

    val handlePaint = shareNunitoPaint(context, 40f, 800, palette.accent)
    val namePaint = shareNunitoPaint(context, 64f, 800, palette.ink)
    val bioPaint = shareNunitoPaint(context, 34f, 500, palette.muted)
    val wordmarkPaint = shareNunitoPaint(context, 64f, 900, palette.ink)

    // --- Header ---
    var cursorY = headerTop
    val avatarSize = 108f
    if (avatarBitmap != null) {
        val circ = shareCircularBitmap(avatarBitmap, avatarSize.toInt())
        canvas.drawBitmap(circ, hPad, cursorY, paint)
        circ.recycle()
        avatarBitmap.recycle()
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surface }
        canvas.drawCircle(hPad + avatarSize / 2f, cursorY + avatarSize / 2f, avatarSize / 2f, placeholderPaint)
    }

    val handleBaseline = cursorY + avatarSize / 2f - (handlePaint.descent() + handlePaint.ascent()) / 2f
    canvas.drawText("@${profile.username}", hPad + avatarSize + 22f, handleBaseline, handlePaint)

    cursorY += avatarSize + 22f
    val displayName = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username
    val nameLines = ellipsizeShareLines(displayName, namePaint, canvasWidth - hPad * 2, 2)
    nameLines.forEach { line ->
        canvas.drawText(line, hPad, cursorY - namePaint.ascent(), namePaint)
        cursorY += namePaint.descent() - namePaint.ascent()
    }

    profile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
        cursorY += 16f
        val bioLines = ellipsizeShareLines(bio, bioPaint, canvasWidth - hPad * 2, 3)
        bioLines.forEach { line ->
            canvas.drawText(line, hPad, cursorY - bioPaint.ascent(), bioPaint)
            cursorY += (bioPaint.descent() - bioPaint.ascent()) + 4f
        }
    }

    // --- Grid ---
    val gridTop = cursorY + spacer
    val gridWidth = canvasWidth - hPad * 2f
    val maxGridHeight = 980f
    val layout = profileStoriesGridLayout(artworkBitmaps.size)

    val gridHeight = if (layout.displayCount == 0) {
        drawEmptyArtworkGrid(
            canvas = canvas,
            context = context,
            profile = profile,
            palette = palette,
            paint = paint,
            centerX = canvasWidth / 2f,
            top = gridTop + 80f,
        )
        420f
    } else {
        val tile = floor(min(gridWidth / layout.columns, maxGridHeight / layout.rows))
        for (index in 0 until layout.displayCount) {
            val row = index / layout.columns
            val col = index % layout.columns
            val x = hPad + col * tile
            val y = gridTop + row * tile
            drawAspectFillTile(canvas, artworkBitmaps[index], x, y, tile, paint)
            artworkBitmaps[index].recycle()
        }
        tile * layout.rows
    }

    // --- Footer (logo + wordmark) ---
    val footerBottomY = canvasHeight - footerBottom
    val logoSize = 56f
    val wordmark = "corus"
    val wordmarkWidth = wordmarkPaint.measureText(wordmark)
    val rowWidth = logoSize + 18f + wordmarkWidth
    val rowLeft = (canvasWidth - rowWidth) / 2f

    drawTintedLogo(
        canvas = canvas,
        context = context,
        left = rowLeft,
        top = footerBottomY - logoSize + brandMarkYOffset,
        size = logoSize,
        color = palette.ink,
        paint = paint,
    )

    val wordmarkBaseline = footerBottomY - (wordmarkPaint.descent() + wordmarkPaint.ascent()) / 2f - wordmarkPaint.descent()
    canvas.drawText(wordmark, rowLeft + logoSize + 18f, wordmarkBaseline, wordmarkPaint)

    bitmap
}

private fun drawEmptyArtworkGrid(
    canvas: Canvas,
    context: Context,
    profile: ShareProfileSubject,
    palette: ProfileStoriesPalette,
    paint: Paint,
    centerX: Float,
    top: Float,
) {
    val avatarSize = 260f
    val avatarBitmap = profile.avatarUrl?.takeIf { it.isNotBlank() }?.let { downloadShareBitmap(it) }
    val left = centerX - avatarSize / 2f
    if (avatarBitmap != null) {
        val circ = shareCircularBitmap(avatarBitmap, avatarSize.toInt())
        canvas.drawBitmap(circ, left, top, paint)
        circ.recycle()
        avatarBitmap.recycle()
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surface }
        canvas.drawCircle(centerX, top + avatarSize / 2f, avatarSize / 2f, placeholderPaint)
        drawTintedLogo(
            canvas = canvas,
            context = context,
            left = centerX - avatarSize * 0.2f,
            top = top + avatarSize * 0.3f,
            size = avatarSize * 0.4f,
            color = palette.ink,
            paint = paint,
            alpha = 90,
        )
    }

    val handlePaint = shareNunitoPaint(context, 44f, 800, palette.accent)
    val handle = "@${profile.username}"
    val handleX = centerX - handlePaint.measureText(handle) / 2f
    val handleY = top + avatarSize + 36f - handlePaint.ascent()
    canvas.drawText(handle, handleX, handleY, handlePaint)
}

private fun drawAspectFillTile(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, size: Float, paint: Paint) {
    val side = min(bitmap.width, bitmap.height)
    val src = Rect((bitmap.width - side) / 2, (bitmap.height - side) / 2, (bitmap.width + side) / 2, (bitmap.height + side) / 2)
    val dst = RectF(x, y, x + size, y + size)
    canvas.drawBitmap(bitmap, src, dst, paint)
}

private fun drawTintedLogo(
    canvas: Canvas,
    context: Context,
    left: Float,
    top: Float,
    size: Float,
    color: Int,
    paint: Paint,
    alpha: Int = 255,
) {
    val logo = decodeShareDrawable(context, R.drawable.logo_no_background) ?: return
    val scaled = Bitmap.createScaledBitmap(logo, size.toInt(), size.toInt(), true)
    if (scaled != logo) logo.recycle()
    val tintedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        this.alpha = alpha
        colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
    canvas.drawBitmap(scaled, left, top, tintedPaint)
    scaled.recycle()
}

private fun decodeShareDrawable(context: Context, resId: Int): Bitmap? = try {
    BitmapFactory.decodeResource(context.resources, resId)
} catch (_: Exception) {
    null
}

internal fun shareNunitoPaint(
    context: Context,
    size: Float,
    weight: Int,
    color: Int,
    align: Paint.Align = Paint.Align.LEFT,
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    textAlign = align
    this.color = color
    try {
        typeface = context.resources.getFont(R.font.nunito)
        fontVariationSettings = "'wght' $weight"
    } catch (_: Throwable) {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isFakeBoldText = weight >= 700
    }
}

internal fun downloadShareBitmap(url: String): Bitmap? = try {
    URL(url).openStream().use { BitmapFactory.decodeStream(it) }
} catch (_: Exception) {
    null
}

internal fun shareCircularBitmap(src: Bitmap, size: Int): Bitmap {
    val side = min(src.width, src.height)
    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(square, size, size, true)
    if (square != scaled && square != src) square.recycle()
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    p.xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    c.drawBitmap(scaled, 0f, 0f, p)
    if (scaled != out && scaled != src) scaled.recycle()
    return out
}

internal fun ellipsizeShareLines(
    text: String,
    paint: Paint,
    maxWidth: Float,
    maxLines: Int,
): List<String> {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val lines = ArrayList<String>()
    var current = ""
    var i = 0
    while (i < words.size) {
        val candidate = if (current.isEmpty()) words[i] else "$current ${words[i]}"
        if (current.isEmpty() || paint.measureText(candidate) <= maxWidth) {
            current = candidate
            i++
        } else {
            lines.add(current)
            current = ""
            if (lines.size == maxLines) break
        }
    }
    if (lines.size < maxLines && current.isNotEmpty()) {
        lines.add(current)
    }
    if (i < words.size && lines.isNotEmpty()) {
        lines[lines.size - 1] = truncateShareEllipsis(lines.last(), paint, maxWidth)
    }
    return lines
}

private fun truncateShareEllipsis(line: String, paint: Paint, maxWidth: Float): String {
    val ellipsis = "…"
    if (paint.measureText(line + ellipsis) <= maxWidth) return line + ellipsis
    var s = line
    while (s.isNotEmpty() && paint.measureText(s + ellipsis) > maxWidth) {
        s = s.dropLast(1)
    }
    return s.trimEnd() + ellipsis
}

private const val IG_PROFILE_SHARE_TAG = "InstagramProfileShare"

/**
 * Share a profile to Instagram Stories using the background image sticker API.
 * Mirrors iOS `ProfileInstagramStoriesCardView.render` + pasteboard hand-off.
 */
suspend fun shareProfileToInstagramStories(
    context: Context,
    profile: ShareProfileSubject,
    theme: ShareCardTheme,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val bitmap = generateProfileStoriesCardBitmap(context, profile, theme)
        val file = File(context.cacheDir, "instagram_profile_share_${profile.id}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        runCatching {
            context.grantUriPermission(
                "com.instagram.android",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Log.w(IG_PROFILE_SHARE_TAG, "grantUriPermission failed (continuing anyway)", it) }

        val contentUrl = "https://corus.fm/u/${profile.username}"
        val storyIntent = buildAddToStoryIntent(uri, contentUrl, context.packageName)

        withContext(Dispatchers.Main) {
            try {
                context.startActivity(storyIntent)
                true
            } catch (e: Exception) {
                Log.w(IG_PROFILE_SHARE_TAG, "ADD_TO_STORY launch threw; falling back to share sheet", e)
                shareImageViaChooser(context, uri)
            }
        }
    } catch (e: Exception) {
        Log.w(IG_PROFILE_SHARE_TAG, "Failed to build profile Instagram share card", e)
        false
    }
}
