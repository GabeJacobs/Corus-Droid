package fm.corus.android.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.VinylStyle
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Instagram Stories share card layout.
 * Dimensions: 1080x1920 (9:16 aspect ratio).
 * This composable is only used for rendering to bitmap — not displayed directly.
 */
@Composable
fun InstagramShareCardContent(
    post: CymbalPost,
) {
    Box(
        modifier = Modifier
            .width(1080.dp)
            .height(1920.dp)
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(154.dp)) // ~8% top padding

            // User info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 80.dp),
            ) {
                AsyncImage(
                    model = post.user.avatarURL,
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = "@${post.user.username}",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CorusColors.Text,
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // Album art or movie poster (centered)
            val imageUrl = post.displayImageLargeURL ?: post.displayImageURL
            if (imageUrl != null) {
                if (post.isMovie) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(400.dp)
                            .height(600.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(500.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Title + subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 80.dp),
            ) {
                Text(
                    text = post.displayTitle,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CorusColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (post.displaySubtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = post.displaySubtitle,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Medium,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                if (!post.caption.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = post.caption!!,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Normal,
                        color = CorusColors.Secondary.copy(alpha = 0.8f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // Corus branding at bottom
            Text(
                text = "corus",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = CorusColors.Text,
            )

            Spacer(modifier = Modifier.height(154.dp)) // ~8% bottom padding
        }
    }
}

/**
 * Generates the 1080x1920 Instagram Stories share card as a bitmap.
 *
 * For tracks this mirrors the on-screen [FeaturedCymbalView] and the iOS share card:
 * a layered vinyl composite (shadow + tinted vinyl disc + circular center label +
 * big album art) centered in the card, with @username up top and the title,
 * subtitle and caption (left) plus the "corus" wordmark (right) along the bottom.
 * Movies fall back to a centered rounded poster.
 */
suspend fun generateShareCardBitmap(
    context: Context,
    post: CymbalPost,
): Bitmap = withContext(Dispatchers.IO) {
    val width = 1080
    val height = 1920
    val marginX = 80f
    val ink = android.graphics.Color.parseColor("#1A1A2E")

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    // Download album art / poster + avatar up front.
    val artBitmap = (post.displayImageLargeURL ?: post.displayImageURL)?.let { downloadBitmap(it) }
    val avatarBitmap = post.user.avatarURL?.takeIf { it.isNotBlank() }?.let { downloadBitmap(it) }

    // --- Top: avatar + @username ---
    val topPad = height * 0.08f
    val avatarSize = 120f
    val usernamePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 46f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.LEFT
    }
    var usernameX = marginX
    if (avatarBitmap != null) {
        val circ = circularBitmap(avatarBitmap, avatarSize.toInt())
        canvas.drawBitmap(circ, marginX, topPad, paint)
        circ.recycle()
        usernameX = marginX + avatarSize + 28f
    }
    // Vertically center the username against the avatar.
    val usernameBaseline = topPad + avatarSize / 2f - (usernamePaint.descent() + usernamePaint.ascent()) / 2f
    canvas.drawText("@${post.user.username}", usernameX, usernameBaseline, usernamePaint)

    // --- Bottom-right: "corus" wordmark ---
    val bottomPad = height * 0.08f
    val brandPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 48f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    canvas.drawText("corus", width - marginX, height - bottomPad, brandPaint)

    // --- Bottom-left: title / subtitle / caption, bottom-aligned with the wordmark ---
    val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 48f
        isFakeBoldText = true
    }
    val subtitlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = (0x80 shl 24) or (ink and 0x00FFFFFF) // ~50% ink
        textSize = 38f
    }
    val captionPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = (0x66 shl 24) or (ink and 0x00FFFFFF) // ~40% ink
        textSize = 34f
    }
    val maxTextWidth = width - marginX * 2
    val captionLines = post.caption?.takeIf { it.isNotBlank() }
        ?.let { ellipsizeLines(it, captionPaint, maxTextWidth, 4) } ?: emptyList()
    val titleLines = ellipsizeLines(post.displayTitle, titlePaint, maxTextWidth, 2)
    val subtitleLine = post.displaySubtitle
        .takeIf { it.isNotBlank() }
        ?.let { ellipsizeLines(it, subtitlePaint, maxTextWidth, 1).firstOrNull() }

    var baseline = height - bottomPad
    captionLines.asReversed().forEach { line ->
        canvas.drawText(line, marginX, baseline, captionPaint)
        baseline -= (captionPaint.descent() - captionPaint.ascent()) + 6f
    }
    if (subtitleLine != null) {
        canvas.drawText(subtitleLine, marginX, baseline, subtitlePaint)
        baseline -= (subtitlePaint.descent() - subtitlePaint.ascent()) + 8f
    }
    titleLines.asReversed().forEach { line ->
        canvas.drawText(line, marginX, baseline, titlePaint)
        baseline -= (titlePaint.descent() - titlePaint.ascent()) + 8f
    }

    // --- Middle: media composite, centered between the avatar row and the text block ---
    val regionTop = topPad + avatarSize + 60f
    val regionBottom = height - bottomPad - 360f
    val regionCenterY = (regionTop + regionBottom) / 2f

    if (artBitmap != null) {
        if (post.isMovie) {
            val posterW = 460f
            val posterH = posterW * 3f / 2f
            val scaled = Bitmap.createScaledBitmap(artBitmap, posterW.toInt(), posterH.toInt(), true)
            val rounded = roundedBitmap(scaled, 24f)
            if (scaled != rounded) scaled.recycle()
            canvas.drawBitmap(rounded, (width - posterW) / 2f, regionCenterY - posterH / 2f, paint)
            rounded.recycle()
        } else {
            drawVinylComposite(
                canvas = canvas,
                context = context,
                style = post.user.vinylStyle,
                art = artBitmap,
                compWidth = width.toFloat(),
                compLeft = 0f,
                compCenterY = regionCenterY,
                paint = paint,
            )
        }
        artBitmap.recycle()
    }
    avatarBitmap?.recycle()

    bitmap
}

/** Layers shadow + tinted vinyl + circular center label + big album art, matching FeaturedCymbalView. */
private fun drawVinylComposite(
    canvas: android.graphics.Canvas,
    context: Context,
    style: VinylStyle,
    art: Bitmap,
    compWidth: Float,
    compLeft: Float,
    compCenterY: Float,
    paint: android.graphics.Paint,
) {
    val compHeight = compWidth * style.canvasRatio
    val compTop = compCenterY - compHeight / 2f
    val dst = android.graphics.RectF(compLeft, compTop, compLeft + compWidth, compTop + compHeight)

    decodeDrawable(context, R.drawable.featured_shadow)?.let {
        canvas.drawBitmap(it, null, dst, paint)
        it.recycle()
    }
    decodeDrawable(context, vinylDrawableRes(style))?.let {
        canvas.drawBitmap(it, null, dst, paint)
        it.recycle()
    }

    // Circular center label (album art masked to a circle), positioned per vinyl style.
    val labelW = compWidth * style.labelWFrac
    val labelH = compHeight * style.labelHFrac
    val labelX = compLeft + compWidth * style.labelXFrac
    val labelY = compTop + compHeight * style.labelYFrac
    val labelSize = minOf(labelW, labelH).toInt().coerceAtLeast(1)
    val label = circularBitmap(art, labelSize)
    canvas.drawBitmap(label, labelX + (labelW - labelSize) / 2f, labelY + (labelH - labelSize) / 2f, paint)
    label.recycle()

    // Big album art (rounded square) on top, with a soft drop shadow.
    val artSize = compWidth * 270f / 585f
    val artX = compLeft + compWidth * 106f / 585f
    val artY = compTop + compHeight * 64f / 448f
    val side = minOf(art.width, art.height)
    val sq = Bitmap.createBitmap(art, (art.width - side) / 2, (art.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(sq, artSize.toInt(), artSize.toInt(), true)
    if (sq != scaled) sq.recycle()
    val rounded = roundedBitmap(scaled, 8f)
    if (scaled != rounded) scaled.recycle()
    val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        setShadowLayer(28f, 0f, 14f, 0x55000000)
    }
    canvas.drawRoundRect(android.graphics.RectF(artX, artY, artX + artSize, artY + artSize), 8f, 8f, shadowPaint)
    canvas.drawBitmap(rounded, artX, artY, paint)
    rounded.recycle()
}

private fun vinylDrawableRes(style: VinylStyle): Int = when (style) {
    VinylStyle.BLACK -> R.drawable.vinyl_black
    VinylStyle.CLEAR -> R.drawable.vinyl_clear
    VinylStyle.RED_MATTE -> R.drawable.vinyl_red_matte
    VinylStyle.PURPLE -> R.drawable.vinyl_purple
    VinylStyle.WHITE -> R.drawable.vinyl_white
    VinylStyle.GOLD -> R.drawable.vinyl_gold
    VinylStyle.RED -> R.drawable.vinyl_red
    VinylStyle.BLUE -> R.drawable.vinyl_blue
    VinylStyle.GREEN -> R.drawable.vinyl_green
}

private fun decodeDrawable(context: Context, resId: Int): Bitmap? = try {
    BitmapFactory.decodeResource(context.resources, resId)
} catch (_: Exception) {
    null
}

private fun downloadBitmap(url: String): Bitmap? = try {
    URL(url).openStream().use { BitmapFactory.decodeStream(it) }
} catch (_: Exception) {
    null
}

/** Center-crop [src] to a square and mask it to a circle of [size] px. */
private fun circularBitmap(src: Bitmap, size: Int): Bitmap {
    val side = minOf(src.width, src.height)
    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(square, size, size, true)
    if (square != scaled) square.recycle()
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    c.drawBitmap(scaled, 0f, 0f, p)
    if (scaled != out) scaled.recycle()
    return out
}

/** Round the corners of [src] by [radius] px. */
private fun roundedBitmap(src: Bitmap, radius: Float): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    c.drawRoundRect(android.graphics.RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, p)
    p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    c.drawBitmap(src, 0f, 0f, p)
    return out
}

/** Greedy word-wrap of [text] to fit [maxWidth], capped at [maxLines] with a trailing ellipsis. */
private fun ellipsizeLines(
    text: String,
    paint: android.graphics.Paint,
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
        current = ""
    }
    if ((i < words.size || current.isNotEmpty()) && lines.isNotEmpty()) {
        lines[lines.size - 1] = truncateWithEllipsis(lines.last(), paint, maxWidth)
    }
    return lines
}

private fun truncateWithEllipsis(line: String, paint: android.graphics.Paint, maxWidth: Float): String {
    val ellipsis = "…"
    if (paint.measureText(line + ellipsis) <= maxWidth) return line + ellipsis
    var s = line
    while (s.isNotEmpty() && paint.measureText(s + ellipsis) > maxWidth) {
        s = s.dropLast(1)
    }
    return s.trimEnd() + ellipsis
}

/**
 * Share a post to Instagram Stories using the background image sticker API.
 */
suspend fun shareToInstagramStories(
    context: Context,
    post: CymbalPost,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val bitmap = generateShareCardBitmap(context, post)

        // Save bitmap to a temporary file
        val file = File(context.cacheDir, "instagram_share_${post.id}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )

        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("source_application", "fm.corus.android")
            putExtra("content_url", "https://corus.fm/post/${post.id}")
        }

        withContext(Dispatchers.Main) {
            try {
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                // Instagram not installed, fall back to regular share
                false
            }
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * Check if Instagram Stories sharing is available on this device.
 */
fun isInstagramAvailable(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.instagram.android", 0)
        true
    } catch (_: Exception) {
        false
    }
}
