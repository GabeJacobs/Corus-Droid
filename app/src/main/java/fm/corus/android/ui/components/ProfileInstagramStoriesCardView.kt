package fm.corus.android.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.DrawableCompat
import fm.corus.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.floor
import kotlin.math.max
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

enum class ProfileStoryGridSize(val artworkLimit: Int) {
    STANDARD(9),
    LARGE(16),
    EXTRA_LARGE(25),
    FULL(28),
}

enum class ProfileStoryBackground(
    val label: String,
    val color: Int,
    val usesDarkInk: Boolean,
) {
    INVITATION_BLUE("Blue", 0xffeaf1ff.toInt(), true),
    CORUS_BLUE("Blue", 0xff6495ed.toInt(), true),
    LIGHT("Light", 0xffffffff.toInt(), true),
    DARK("Dark", 0xff000000.toInt(), false),
    PURPLE("Purple", 0xff7657d5.toInt(), false),
    ROSE("Rose", 0xffc9577d.toInt(), false),
    ORANGE("Orange", 0xffe78345.toInt(), true),
    GREEN("Green", 0xff4a9c78.toInt(), true),
}

internal fun profileStoriesGridLayout(
    count: Int,
    size: ProfileStoryGridSize = ProfileStoryGridSize.STANDARD,
): ProfileStoriesGridLayout = if (size == ProfileStoryGridSize.LARGE && count > 0) {
    val displayCount = min(count, size.artworkLimit)
    ProfileStoriesGridLayout(4, (displayCount + 3) / 4, displayCount)
} else when (count) {
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

        fun forSelection(theme: ShareCardTheme, background: ProfileStoryBackground?): ProfileStoriesPalette {
            if (background == null) return forTheme(theme)
            if (background == ProfileStoryBackground.LIGHT) return forTheme(ShareCardTheme.LIGHT)
            if (background == ProfileStoryBackground.DARK) return forTheme(ShareCardTheme.DARK)
            val ink = if (background.usesDarkInk) 0xff15151a.toInt() else 0xffffffff.toInt()
            val muted = (0xb8 shl 24) or (ink and 0x00ffffff)
            val surface = (0x24 shl 24) or (ink and 0x00ffffff)
            return ProfileStoriesPalette(
                ink = ink,
                muted = muted,
                accent = ink,
                surface = surface,
                backdrop = background.color,
            )
        }
    }
}

suspend fun generateProfileStoriesCardBitmap(
    context: Context,
    profile: ShareProfileSubject,
    theme: ShareCardTheme,
    showBio: Boolean = true,
    gridSize: ProfileStoryGridSize = ProfileStoryGridSize.STANDARD,
    background: ProfileStoryBackground? = null,
    profileSharingV2: Boolean = false,
): Bitmap = withContext(Dispatchers.IO) {
    val canvasWidth = 1080
    val canvasHeight = 1920
    val hPad = 72f
    val headerTop = 200f
    val footerBottom = 220f
    val spacer = 36f
    val brandMarkYOffset = 5f

    val palette = ProfileStoriesPalette.forSelection(theme, background)
    val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(palette.backdrop)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    val avatarBitmap = profile.avatarUrl?.takeIf { it.isNotBlank() }?.let { downloadShareBitmap(it) }
    if (profileSharingV2 && profile.isInvitation) {
        drawProfileInvitation(canvas, context, profile, palette, avatarBitmap, paint, showBio)
        avatarBitmap?.recycle()
        return@withContext bitmap
    }
    val artworkUrls = profile.artworkUrls
    val artworkBitmaps = artworkUrls.take(gridSize.artworkLimit).mapNotNull { downloadShareBitmap(it) }

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

    profile.bio?.takeIf { showBio && it.isNotBlank() }?.let { bio ->
        cursorY += 16f
        val bioLines = ellipsizeShareLines(bio, bioPaint, canvasWidth - hPad * 2, 3)
        bioLines.forEachIndexed { index, line ->
            canvas.drawText(line, hPad, cursorY - bioPaint.ascent(), bioPaint)
            cursorY += bioPaint.descent() - bioPaint.ascent()
            if (index < bioLines.lastIndex) cursorY += 4f
        }
    }

    // --- Grid (vertically centered between header and footer, matching iOS Spacers) ---
    val headerBottom = cursorY
    val logoSize = 56f
    val wordmarkHeight = wordmarkPaint.descent() - wordmarkPaint.ascent()
    val footerHeight = max(logoSize, wordmarkHeight)
    val footerTop = canvasHeight - footerBottom - footerHeight
    val gridWidth = canvasWidth - hPad * 2f
    val maxGridHeight = 980f
    val layout = profileStoriesGridLayout(artworkBitmaps.size, gridSize)
    val tile = if (layout.displayCount == 0) {
        0f
    } else {
        floor(min(gridWidth / layout.columns, maxGridHeight / layout.rows))
    }
    val gridHeight = if (layout.displayCount == 0) 420f else tile * layout.rows
    val available = footerTop - headerBottom
    val spacerEach = max(spacer, (available - gridHeight) / 2f)
    val gridTop = headerBottom + spacerEach

    if (layout.displayCount == 0) {
        drawEmptyArtworkGrid(
            canvas = canvas,
            context = context,
            profile = profile,
            palette = palette,
            paint = paint,
            centerX = canvasWidth / 2f,
            top = gridTop + 80f,
        )
    } else {
        for (index in 0 until layout.displayCount) {
            val row = index / layout.columns
            val col = index % layout.columns
            val x = hPad + col * tile
            val y = gridTop + row * tile
            drawAspectFillTile(canvas, artworkBitmaps[index], x, y, tile, paint)
            artworkBitmaps[index].recycle()
        }
    }

    // --- Footer (logo + wordmark), pinned above bottom inset like iOS ---
    val footerBottomY = canvasHeight - footerBottom
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
    )

    // Center wordmark on the logo's un-offset mid-line (iOS HStack + logo offset).
    val wordmarkCenterY = footerBottomY - logoSize / 2f
    val wordmarkBaseline = wordmarkCenterY - (wordmarkPaint.ascent() + wordmarkPaint.descent()) / 2f
    canvas.drawText(wordmark, rowLeft + logoSize + 18f, wordmarkBaseline, wordmarkPaint)

    bitmap
}

private fun drawProfileInvitation(
    canvas: Canvas,
    context: Context,
    profile: ShareProfileSubject,
    palette: ProfileStoriesPalette,
    avatar: Bitmap?,
    paint: Paint,
    showBio: Boolean,
) {
    val centerX = 540f
    val maxWidth = 936f
    val avatarSize = 340f
    val name = profile.displayName?.trim()?.takeIf { it.isNotEmpty() }
    val distinctName = name?.takeIf { !it.removePrefix("@").equals(profile.username, ignoreCase = true) }
    fun fittedPaint(text: String, size: Float, color: Int) = shareNunitoPaint(context, size, 800, color, Paint.Align.CENTER).apply {
        if (measureText(text) > maxWidth) textSize *= (maxWidth / measureText(text)).coerceAtLeast(0.5f)
    }
    val heading = context.getString(R.string.share_profile_invitation)
    val headingPaint = fittedPaint(heading, 76f, palette.ink)
    val handle = "@${profile.username}"
    val handlePaint = fittedPaint(handle, 56f, palette.accent)
    val namePaint = fittedPaint(distinctName.orEmpty(), 48f, palette.ink)
    val bioPaint = shareNunitoPaint(context, 42f, 500, palette.muted, Paint.Align.CENTER)
    val bioLines = profile.bio?.trim()?.takeIf { showBio && it.isNotEmpty() }
        ?.let { ellipsizeShareLines(it, bioPaint, maxWidth, 3) }.orEmpty()
    val brandPaint = shareNunitoPaint(context, 48f, 800, palette.ink)
    fun lineHeight(p: Paint) = p.descent() - p.ascent()
    val height = avatarSize + 40f + lineHeight(headingPaint) +
        (if (distinctName != null) 16f + lineHeight(namePaint) else 0f) +
        16f + lineHeight(handlePaint) + (if (bioLines.isNotEmpty()) 24f + lineHeight(bioPaint) * bioLines.size else 0f) +
        48f + max(44f, lineHeight(brandPaint))
    var y = (1920f - height) / 2f
    if (avatar != null) {
        val circle = shareCircularBitmap(avatar, avatarSize.toInt())
        canvas.drawBitmap(circle, centerX - avatarSize / 2f, y, paint)
        circle.recycle()
    } else {
        canvas.drawCircle(centerX, y + avatarSize / 2f, avatarSize / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surface })
        val initialPaint = shareNunitoPaint(context, avatarSize * 0.42f, 800, palette.accent, Paint.Align.CENTER)
        canvas.drawText((name ?: profile.username).take(1).uppercase(), centerX,
            y + avatarSize / 2f - (initialPaint.ascent() + initialPaint.descent()) / 2f, initialPaint)
    }
    y += avatarSize + 40f
    canvas.drawText(heading, centerX, y - headingPaint.ascent(), headingPaint)
    y += lineHeight(headingPaint)
    if (distinctName != null) {
        y += 16f
        canvas.drawText(distinctName, centerX, y - namePaint.ascent(), namePaint)
        y += lineHeight(namePaint)
    }
    y += 16f
    canvas.drawText(handle, centerX, y - handlePaint.ascent(), handlePaint)
    y += lineHeight(handlePaint)
    if (bioLines.isNotEmpty()) {
        y += 24f
        bioLines.forEach { line -> canvas.drawText(line, centerX, y - bioPaint.ascent(), bioPaint); y += lineHeight(bioPaint) }
    }
    y += 48f
    val brandWidth = 44f + 14f + brandPaint.measureText("corus")
    val brandLeft = centerX - brandWidth / 2f
    val brandHeight = max(44f, lineHeight(brandPaint))
    drawTintedLogo(canvas, context, brandLeft, y + (brandHeight - 44f) / 2f, 44f, palette.ink)
    canvas.drawText("corus", brandLeft + 58f, y + brandHeight / 2f - (brandPaint.ascent() + brandPaint.descent()) / 2f, brandPaint)
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

internal fun drawTintedLogo(
    canvas: Canvas,
    context: Context,
    left: Float,
    top: Float,
    size: Float,
    color: Int,
    alpha: Int = 255,
) {
    // logo_no_background is a vector — draw it directly (BitmapFactory cannot decode vectors).
    val drawable = ContextCompat.getDrawable(context, R.drawable.logo_no_background)?.mutate()
    if (drawable == null) {
        Log.w(IG_PROFILE_SHARE_TAG, "logo_no_background drawable missing")
        return
    }
    val px = size.toInt().coerceAtLeast(1)
    DrawableCompat.setTint(drawable, color)
    drawable.alpha = alpha.coerceIn(0, 255)
    drawable.setBounds(0, 0, px, px)
    canvas.save()
    canvas.translate(left, top)
    drawable.draw(canvas)
    canvas.restore()
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
 *
 * Instagram does not add a tappable link sticker for third-party shares;
 * `content_url` is attribution at best. The share sheet copies the profile
 * URL so the user can paste it onto the story.
 */
suspend fun shareProfileToInstagramStories(
    context: Context,
    profile: ShareProfileSubject,
    theme: ShareCardTheme,
    showBio: Boolean = true,
    gridSize: ProfileStoryGridSize = ProfileStoryGridSize.STANDARD,
    background: ProfileStoryBackground? = null,
    profileSharingV2: Boolean = false,
): Boolean = withContext(Dispatchers.IO) {
    try {
        Log.i(IG_PROFILE_SHARE_TAG, "Building Stories card theme=${theme.analyticsValue} user=${profile.username}")
        val bitmap = generateProfileStoriesCardBitmap(context, profile, theme, showBio, gridSize, background, profileSharingV2)
        // Unique filename per theme + share so Instagram can't reuse a stale cached URI.
        val file = File(
            context.cacheDir,
            "instagram_profile_share_${profile.id}_${theme.analyticsValue}_${System.currentTimeMillis()}.png",
        )
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

        val contentUrl = buildString {
            append("https://corus.fm/u/${profile.username}")
            theme.queryValue?.let { append("?theme=$it") }
        }
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
