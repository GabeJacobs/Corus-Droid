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
import fm.corus.android.data.model.CymbalPost
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
 * Generates the share image as a bitmap by downloading images and compositing them.
 * This is a simplified version that creates a share card image without full Compose rendering.
 */
suspend fun generateShareCardBitmap(
    context: Context,
    post: CymbalPost,
): Bitmap = withContext(Dispatchers.IO) {
    val width = 1080
    val height = 1920
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // White background
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    // Download album art/poster
    val imageUrl = post.displayImageLargeURL ?: post.displayImageURL
    var artBitmap: Bitmap? = null
    if (imageUrl != null) {
        try {
            val stream = URL(imageUrl).openStream()
            artBitmap = BitmapFactory.decodeStream(stream)
            stream.close()
        } catch (_: Exception) { }
    }

    // Draw album art centered
    if (artBitmap != null) {
        val artSize = if (post.isMovie) {
            // Movie poster: 400x600 equivalent
            Pair(400, 600)
        } else {
            // Album art: 500x500
            Pair(500, 500)
        }
        val scaledArt = Bitmap.createScaledBitmap(artBitmap, artSize.first, artSize.second, true)
        val artX = (width - artSize.first) / 2f
        val artY = (height - artSize.second) / 2f - 60f
        canvas.drawBitmap(scaledArt, artX, artY, paint)
        scaledArt.recycle()
        artBitmap.recycle()
    }

    // Draw title
    val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1A1A2E")
        textSize = 48f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText(post.displayTitle, width / 2f, height - 400f, titlePaint)

    // Draw subtitle
    if (post.displaySubtitle.isNotBlank()) {
        val subtitlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#8E8E93")
            textSize = 38f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText(post.displaySubtitle, width / 2f, height - 340f, subtitlePaint)
    }

    // Draw "corus" branding
    val brandPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1A1A2E")
        textSize = 48f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("corus", width / 2f, height - 154f, brandPaint)

    // Draw @username at top (with avatar if available)
    val userPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1A1A2E")
        textSize = 42f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // Download avatar
    var avatarBitmap: Bitmap? = null
    val avatarUrl = post.user.avatarURL
    if (!avatarUrl.isNullOrBlank()) {
        try {
            val stream = URL(avatarUrl).openStream()
            avatarBitmap = BitmapFactory.decodeStream(stream)
            stream.close()
        } catch (_: Exception) { }
    }

    // Draw avatar as circle (before username text)
    if (avatarBitmap != null) {
        val avatarSize = 100
        val scaledAvatar = Bitmap.createScaledBitmap(avatarBitmap, avatarSize, avatarSize, true)

        // Create circular bitmap
        val circularAvatar = Bitmap.createBitmap(avatarSize, avatarSize, Bitmap.Config.ARGB_8888)
        val avatarCanvas = android.graphics.Canvas(circularAvatar)
        val avatarPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        avatarCanvas.drawCircle(avatarSize / 2f, avatarSize / 2f, avatarSize / 2f, avatarPaint)
        avatarPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        avatarCanvas.drawBitmap(scaledAvatar, 0f, 0f, avatarPaint)

        // Position avatar to the left of username
        val avatarX = (width / 2f) - 160f  // offset left of center
        val avatarY = 140f
        canvas.drawBitmap(circularAvatar, avatarX, avatarY, paint)

        // Adjust username position to right of avatar
        userPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText("@${post.user.username}", avatarX + avatarSize + 16f, 200f, userPaint)

        scaledAvatar.recycle()
        circularAvatar.recycle()
        avatarBitmap.recycle()
    } else {
        // Fallback: centered username text only
        canvas.drawText("@${post.user.username}", width / 2f, 200f, userPaint)
    }

    bitmap
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
