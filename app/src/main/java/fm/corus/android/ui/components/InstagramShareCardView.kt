package fm.corus.android.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
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
    val height = 1920f
    val marginX = 80f
    val ink = android.graphics.Color.parseColor("#1A1A2E")

    val bitmap = Bitmap.createBitmap(width, height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    // Download album art / poster + avatar up front.
    val artBitmap = (post.displayImageLargeURL ?: post.displayImageURL)?.let { downloadBitmap(it) }
    val avatarBitmap = post.user.avatarURL?.takeIf { it.isNotBlank() }?.let { downloadBitmap(it) }

    // Brand-font paints (Nunito, weights matched to iOS CymbalFont: heavy=800, medium=500, black=900).
    val usernamePaint = nunitoPaint(context, size = 46f, weight = 800, color = ink)
    val titlePaint = nunitoPaint(context, size = 48f, weight = 800, color = ink)
    val subtitlePaint = nunitoPaint(context, size = 38f, weight = 500, color = (0x80 shl 24) or (ink and 0x00FFFFFF))
    val captionPaint = nunitoPaint(context, size = 34f, weight = 500, color = (0x66 shl 24) or (ink and 0x00FFFFFF))
    val brandPaint = nunitoPaint(context, size = 60f, weight = 900, color = ink, align = android.graphics.Paint.Align.RIGHT)
    val wordmark = "corus"

    // Wrap the bottom text up front so we can size the block and center the layout. Reserve
    // room on the right for the bottom-aligned wordmark so the caption never runs under it —
    // mirrors iOS, where the text column and the wordmark sit side by side in an HStack.
    val maxTextWidth = width - marginX * 2 - brandPaint.measureText(wordmark) - 76f
    val titleLines = ellipsizeLines(post.displayTitle, titlePaint, maxTextWidth, 2)
    val subtitleLine = post.displaySubtitle
        .takeIf { it.isNotBlank() }
        ?.let { ellipsizeLines(it, subtitlePaint, maxTextWidth, 1).firstOrNull() }
    val captionLines = post.caption?.takeIf { it.isNotBlank() }
        ?.let { ellipsizeLines(it, captionPaint, maxTextWidth, 4) } ?: emptyList()

    fun lineHeight(p: android.graphics.Paint) = p.descent() - p.ascent()
    val titleLH = lineHeight(titlePaint)
    val subtitleLH = lineHeight(subtitlePaint)
    val captionLH = lineHeight(captionPaint)
    var textBlockHeight = titleLines.size * titleLH
    if (subtitleLine != null) textBlockHeight += 8f + subtitleLH
    if (captionLines.isNotEmpty()) textBlockHeight += 12f + captionLines.size * captionLH

    // iOS lays the card out as a single VStack centered in the 1080x1920 frame:
    // 8% top pad, avatar row, 4% spacer, vinyl slot (0.65 * width), 4% spacer,
    // title/subtitle/caption block, 8% bottom pad. Center the whole block so the
    // spacing matches instead of anchoring to the image edges.
    val topPad = height * 0.08f
    val bottomPad = height * 0.08f
    val gapSpacer = height * 0.04f
    val avatarSize = 120f
    val vinylSlot = width * 0.65f
    val contentH = topPad + avatarSize + gapSpacer + vinylSlot + gapSpacer + textBlockHeight + bottomPad
    val startY = ((height - contentH) / 2f).coerceAtLeast(0f)

    // --- Top: avatar + @username ---
    val avatarTop = startY + topPad
    var usernameX = marginX
    if (avatarBitmap != null) {
        val circ = circularBitmap(avatarBitmap, avatarSize.toInt())
        canvas.drawBitmap(circ, marginX, avatarTop, paint)
        circ.recycle()
        usernameX = marginX + avatarSize + 28f
    }
    val usernameBaseline = avatarTop + avatarSize / 2f - (usernamePaint.descent() + usernamePaint.ascent()) / 2f
    canvas.drawText("@${post.user.username}", usernameX, usernameBaseline, usernamePaint)

    // --- Middle: media composite, centered in its slot (it overflows the slot, like iOS) ---
    val vinylSlotTop = avatarTop + avatarSize + gapSpacer
    val vinylCenterY = vinylSlotTop + vinylSlot / 2f
    if (artBitmap != null) {
        if (post.isMovie) {
            val posterW = 460f
            val posterH = posterW * 3f / 2f
            val scaled = Bitmap.createScaledBitmap(artBitmap, posterW.toInt(), posterH.toInt(), true)
            val rounded = roundedBitmap(scaled, 24f)
            if (scaled != rounded && scaled != artBitmap) scaled.recycle()
            canvas.drawBitmap(rounded, (width - posterW) / 2f, vinylCenterY - posterH / 2f, paint)
            rounded.recycle()
        } else {
            drawVinylComposite(
                canvas = canvas,
                context = context,
                style = post.user.vinylStyle,
                art = artBitmap,
                compWidth = width.toFloat(),
                compLeft = 0f,
                compCenterY = vinylCenterY,
                paint = paint,
            )
        }
        artBitmap.recycle()
    }
    avatarBitmap?.recycle()

    // --- Bottom: title / subtitle / caption (left), "corus" wordmark (right, bottom-aligned) ---
    val textTop = vinylSlotTop + vinylSlot + gapSpacer
    var cursorTop = textTop
    titleLines.forEach { line ->
        canvas.drawText(line, marginX, cursorTop - titlePaint.ascent(), titlePaint)
        cursorTop += titleLH
    }
    if (subtitleLine != null) {
        cursorTop += 8f
        canvas.drawText(subtitleLine, marginX, cursorTop - subtitlePaint.ascent(), subtitlePaint)
        cursorTop += subtitleLH
    }
    if (captionLines.isNotEmpty()) {
        cursorTop += 12f
        captionLines.forEach { line ->
            canvas.drawText(line, marginX, cursorTop - captionPaint.ascent(), captionPaint)
            cursorTop += captionLH
        }
    }
    val textBottom = textTop + textBlockHeight
    canvas.drawText(wordmark, width - marginX, textBottom - brandPaint.descent(), brandPaint)

    bitmap
}

/** A Paint using the Nunito brand font at the given variable-font weight (falls back to system bold). */
private fun nunitoPaint(
    context: Context,
    size: Float,
    weight: Int,
    color: Int,
    align: android.graphics.Paint.Align = android.graphics.Paint.Align.LEFT,
): android.graphics.Paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
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
    // [sq] may BE [art] (immutable, already-square cover); never recycle the caller's art here.
    if (sq != scaled && sq != art) sq.recycle()
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
    // createBitmap/createScaledBitmap can return the source itself (immutable, already-square art),
    // so guard against recycling [src] — it belongs to the caller, which recycles it later.
    if (square != scaled && square != src) square.recycle()
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    c.drawBitmap(scaled, 0f, 0f, p)
    if (scaled != out && scaled != src) scaled.recycle()
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

private const val IG_SHARE_TAG = "InstagramShare"

/**
 * Builds the Instagram "add to story" intent for [uri] (the rendered card). Extracted so the
 * attribution wiring is unit-testable.
 *
 * [sourceApplication] is our own app id — the Android package name. Do NOT "correct" this to
 * the iOS Facebook app id (1364343535452154): that was tried and silently regressed real
 * devices (the share spun and did nothing). The package name is the value verified to work.
 */
internal fun buildAddToStoryIntent(uri: Uri, contentUrl: String, sourceApplication: String): Intent =
    Intent("com.instagram.share.ADD_TO_STORY").apply {
        setDataAndType(uri, "image/png")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra("source_application", sourceApplication)
        putExtra("content_url", contentUrl)
    }

/**
 * Share a post to Instagram Stories using the background image sticker API.
 *
 * Returns true if the launch was handed off (Instagram Stories, or the system share-sheet
 * fallback when the direct launch throws). Note: if Instagram accepts the intent but then
 * silently drops it internally, no exception is thrown and this still returns true — that
 * case is not detectable from here.
 */
suspend fun shareToInstagramStories(
    context: Context,
    post: CymbalPost,
): Boolean = withContext(Dispatchers.IO) {
    try {
        // Log the Instagram version so a logcat tells us whether a specific Instagram build
        // is rejecting our hand-off (the leading theory for "worked, then stopped").
        runCatching {
            val igVersion = context.packageManager.getPackageInfo("com.instagram.android", 0).versionName
            Log.i(IG_SHARE_TAG, "starting Instagram share (Instagram v$igVersion)")
        }

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

        // Best-effort: explicitly grant Instagram read access to the card. Some devices don't
        // honor the implicit intent flag. This must NEVER break the share — if it throws, the
        // implicit FLAG_GRANT_READ_URI_PERMISSION on the intent still applies (the path that
        // historically worked).
        runCatching {
            context.grantUriPermission(
                "com.instagram.android",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Log.w(IG_SHARE_TAG, "grantUriPermission failed (continuing anyway)", it) }

        val storyIntent = buildAddToStoryIntent(uri, "https://corus.fm/post/${post.id}", context.packageName)

        withContext(Dispatchers.Main) {
            try {
                context.startActivity(storyIntent)
                Log.i(IG_SHARE_TAG, "ADD_TO_STORY launched")
                true
            } catch (e: Exception) {
                // Only fires if the launch itself throws (no activity for the intent, etc.).
                // A silent rejection inside Instagram won't throw and can't be caught here.
                Log.w(IG_SHARE_TAG, "ADD_TO_STORY launch threw; falling back to share sheet", e)
                shareImageViaChooser(context, uri)
            }
        }
    } catch (e: Exception) {
        Log.w(IG_SHARE_TAG, "Failed to build Instagram share card", e)
        false
    }
}

/**
 * Last-resort fallback: hand the already-rendered card to the system share sheet as an
 * image so the user can still post it to Instagram (feed/DM) or anywhere else when the
 * direct Stories hand-off isn't available. Returns true if a chooser was launched.
 */
internal fun shareImageViaChooser(context: Context, uri: Uri): Boolean = try {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    true
} catch (e: Exception) {
    Log.w(IG_SHARE_TAG, "Fallback image chooser failed", e)
    false
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
