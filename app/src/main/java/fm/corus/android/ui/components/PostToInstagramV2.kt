package fm.corus.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fm.corus.android.R
import fm.corus.android.data.model.ShareRecipient
import fm.corus.android.data.model.VinylStyle
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.Dispatchers
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

internal fun renderInstagramV2(context: Context, subject: InstagramV2Subject, art: Bitmap, accent: Int,
                               layout: String, background: String, caption: Boolean, avatar: Bitmap? = null): Bitmap {
    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    var top = when (background) { "light" -> -1; "dark" -> 0xff131313.toInt(); else -> accent }
    if (background == "gradient") while (InstagramV2Palette.luminance(top) > .18) top = InstagramV2Palette.darken(top)
    val bottom = if (background == "gradient") InstagramV2Palette.darken(top) else top
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, 1920f, top, bottom, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 1080f, 1920f, paint)
    paint.shader = null
    val ink = InstagramV2Palette.ink(top, bottom)
    var y = 250f
    fun text(value: String, size: Float, weight: Int, lines: Int) {
        val textPaint = TextPaint(nunitoPaint(context, size, weight, ink))
        val block = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, 920)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
        canvas.save(); canvas.translate(80f, y); block.draw(canvas); canvas.restore()
        y += block.height + 14
    }
    subject.username?.let {
        if (avatar != null) {
            canvas.save()
            val path = android.graphics.Path().apply { addCircle(112f, 282f, 32f, android.graphics.Path.Direction.CW) }
            canvas.clipPath(path)
            canvas.drawBitmap(avatar, null, RectF(80f, 250f, 144f, 314f), paint)
            canvas.restore()
            canvas.save(); canvas.translate(82f, 0f); text("@$it", 32f, 800, 1); canvas.restore()
        } else text("@$it", 32f, 800, 1)
        y = 350f
    }
    if (layout == "vinyl") {
        drawVinylComposite(canvas, context, VinylStyle.BLACK, art, 920f, 80f, y + 360f, paint)
        y += 720
    } else {
        val scale = minOf(720f / art.width, 720f / art.height)
        val w = art.width * scale; val h = art.height * scale
        canvas.drawBitmap(art, null, RectF((1080 - w) / 2, y + (720 - h) / 2, (1080 + w) / 2, y + (720 + h) / 2), paint)
        y += 720
    }
    y += 40
    text(subject.title, 48f, 800, 2)
    text(subject.artist, 36f, 500, 2)
    if (caption && !subject.caption.isNullOrBlank()) { y += 8; text(subject.caption, 32f, 500, 3) }
    canvas.drawText("corus", 1000f, 1665f, nunitoPaint(context, 42f, 900, ink, Paint.Align.RIGHT))
    return bitmap
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
    var layout by remember { mutableStateOf(prefs.getString("layout", "cover") ?: "cover") }
    var background by remember { mutableStateOf(prefs.getString("background", "artwork") ?: "artwork") }
    var caption by remember { mutableStateOf(prefs.getBoolean("caption", true)) }
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
    var options by remember { mutableStateOf(false) }
    val contacts = remember(recentContacts, selected) { selected?.let { s -> listOf(s) + recentContacts.filter { it.id != s.id } } ?: recentContacts }
    LaunchedEffect(subject, retry) {
        loading = true; error = null
        val art = withContext(Dispatchers.IO) { subject.artworkUrl?.let { downloadInstagramV2Bitmap(it) } }
        artwork = art
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
        avatar = withContext(Dispatchers.IO) { subject.avatarUrl?.let { downloadInstagramV2Bitmap(it) } }
    }
    LaunchedEffect(artwork, avatar, accent, layout, background, caption) {
        image = null
        prefs.edit().putString("layout", layout).putString("background", background).putBoolean("caption", caption).apply()
        val art = artwork ?: return@LaunchedEffect
        image = withContext(Dispatchers.Default) { renderInstagramV2(context, subject, art, accent, layout, background, caption, avatar) }
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
        if (sharing) return
        sharing = true
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val file = File.createTempFile("instagram_v2_", ".png", context.cacheDir)
                    file.outputStream().use { rendered.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                }
                if (instagram) {
                    copyLink(subject.songLink)
                    delay(1100)
                    val intent = buildAddToStoryIntent(uri, subject.songLink, context.packageName)
                    runCatching { context.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
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

    Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (searching) subject.title else stringResource(if (onRepost != null) R.string.instagram_v2_share_post else R.string.instagram_v2_share_song),
                modifier = Modifier.weight(1f), style = CorusFont.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.instagram_v2_close)) }
        }
        if (searching) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(value = query, onValueChange = { query = it; onSearchQueryChange(it) }, singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(requester), placeholder = { Text(stringResource(R.string.share_post_search_placeholder)) })
                TextButton(onClick = { searching = false; query = ""; onSearchQueryChange(""); focus.clearFocus() }) { Text(stringResource(R.string.instagram_v2_done)) }
            }
            selected?.let { Text(it.username, style = CorusFont.captionMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            LazyColumn(Modifier.weight(1f)) {
                if (isSearching) item { CircularProgressIndicator(Modifier.padding(20.dp)) }
                else {
                    val recipients = if (query.isBlank()) contacts else searchResults
                    if (recipients.isEmpty()) item { Text(stringResource(R.string.share_post_no_results), modifier = Modifier.padding(20.dp)) }
                    items(recipients, key = { it.id }) { recipient ->
                        ShareUserRow(recipient, selected?.id == recipient.id) { selected = if (selected?.id == recipient.id) null else recipient }
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                image?.let { Image(it.asImageBitmap(), stringResource(R.string.instagram_v2_preview), modifier = Modifier.fillMaxHeight().aspectRatio(9f / 16f)) }
                    ?: if (loading || artwork != null) CircularProgressIndicator() else TextButton(onClick = { retry++ }) { Text(stringResource(R.string.instagram_v2_retry)) }
            }
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = layout == "cover", onClick = { if (!sharing) layout = "cover" }, label = { Text(stringResource(R.string.instagram_v2_cover)) })
                FilterChip(selected = layout == "vinyl", onClick = { if (!sharing) layout = "vinyl" }, label = { Text(stringResource(R.string.instagram_v2_vinyl)) })
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("artwork" to R.string.instagram_v2_artwork, "gradient" to R.string.instagram_v2_gradient, "light" to R.string.instagram_v2_light, "dark" to R.string.instagram_v2_dark)) { (value, label) ->
                    FilterChip(selected = background == value, onClick = { if (!sharing) background = value }, label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            val color = when (value) { "light" -> Color.White; "dark" -> Color(0xff131313); else -> Color(accent) }
                            val end = if (value == "gradient") Color(InstagramV2Palette.darken(accent)) else color
                            Box(Modifier.size(18.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color, end)), CircleShape))
                            Text(stringResource(label))
                        }
                    })
                }
                item {
                    Box {
                        IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreHoriz, stringResource(R.string.instagram_v2_options)) }
                        DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                            if (!subject.caption.isNullOrBlank()) DropdownMenuItem(text = { Text(stringResource(if (caption) R.string.instagram_v2_hide_caption else R.string.instagram_v2_show_caption)) }, onClick = { caption = !caption; options = false }, enabled = !sharing)
                            DropdownMenuItem(text = { Text(stringResource(R.string.instagram_v2_share_image)) }, onClick = { options = false; shareImage(false) }, enabled = image != null && !sharing)
                        }
                    }
                }
            }
            error?.let { Text(it, style = CorusFont.caption, modifier = Modifier.padding(horizontal = 16.dp)) }
            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                item { ShareActionButton(icon = Icons.Default.Search, label = stringResource(R.string.share_post_search_placeholder)) { searching = true } }
                if (isLoadingContacts) item { CircularProgressIndicator(Modifier.size(48.dp)) }
                items(contacts, key = { it.id }) { recipient ->
                    Box(Modifier.width(80.dp)) { ShareContactCell(recipient, selected?.id == recipient.id) { selected = if (selected?.id == recipient.id) null else recipient } }
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
            }
        }
    }
}
