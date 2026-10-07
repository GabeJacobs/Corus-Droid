package fm.corus.android.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure policy. The known profile total wins over stale/paginated artwork. */
internal object ProfileShareEligibility {
    fun count(profile: ShareProfileSubject) = max(0, profile.postCount ?: profile.artworkUrls.size)
    fun usesV2(flag: Boolean, profile: ShareProfileSubject) = flag && count(profile) >= 9
    fun available(profile: ShareProfileSubject, layout: ProfileStoryGridSize) = count(profile) >= layout.artworkLimit
    fun defaultLayout(profile: ShareProfileSubject): ProfileStoryGridSize =
        if (profile.featuredMoviePosterUrl == null && count(profile) >= 28)
            ProfileStoryGridSize.FULL else ProfileStoryGridSize.STANDARD
}

internal val profileShareBackgroundChoices = listOf(
    ProfileStoryBackground.CORUS_BLUE, ProfileStoryBackground.PURPLE, ProfileStoryBackground.ROSE,
    ProfileStoryBackground.ORANGE, ProfileStoryBackground.GREEN, ProfileStoryBackground.DARK, ProfileStoryBackground.LIGHT,
)

internal val ProfileStoryBackground.analyticsValue: String get() = when (this) {
    ProfileStoryBackground.CORUS_BLUE -> "blue"
    ProfileStoryBackground.INVITATION_BLUE -> "invitation_blue"
    ProfileStoryBackground.LIGHT -> "white"
    ProfileStoryBackground.DARK -> "black"
    else -> name.lowercase(java.util.Locale.ROOT)
}
internal val ProfileStoryGridSize.analyticsValue: String get() = when (artworkLimit) {
    28 -> "full"; 16 -> "4x4"; else -> "3x3"
}

/** Slots are never removed after a failed decode: one missing image must not shift the grid. */
internal data class PreparedProfileShareArt(val slots: List<Bitmap?>)

internal object ProfileShareArtCache {
    private val permits = Semaphore(4)
    private val cache = object : LinkedHashMap<List<String>, PreparedProfileShareArt>(3, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<String>, PreparedProfileShareArt>?) = size > 2
    }

    // Coil owns cached bitmaps. Do not recycle them when a sheet closes or changes color.
    suspend fun prepare(context: Context, profile: ShareProfileSubject): PreparedProfileShareArt {
        val urls = profile.featuredMoviePosterUrl?.let { listOf(it) } ?: profile.artworkUrls.take(28)
        synchronized(cache) { cache[urls]?.let { return it } }
        val assets = coroutineScope {
            PreparedProfileShareArt(urls.map { url -> async(Dispatchers.IO) {
                permits.withPermit {
                    if (url.isBlank()) return@withPermit null
                    try {
                        val edge = if (profile.featuredMoviePosterUrl != null) 980 else 384
                        val request = ImageRequest.Builder(context).data(url)
                            .size(edge, edge).precision(Precision.EXACT).allowHardware(false)
                            .memoryCacheKey("profile-share-v2:$edge:$url").build()
                        (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }
            } }.awaitAll())
        }
        // Don't retain transient failed network requests; a later opening can retry.
        if (assets.slots.none { it == null }) synchronized(cache) { cache[urls] = assets }
        return assets
    }
}

internal data class ProfileStoryGeometry(val grid: RectF, val columns: Int, val rows: Int, val contentHeight: Float)

internal fun profileStoryGeometry(layout: ProfileStoryGridSize, film: Boolean = false): ProfileStoryGeometry {
    if (layout.artworkLimit == 28 && !film) {
        val side = 1920f / 7f
        return ProfileStoryGeometry(RectF((1080 - side * 4) / 2, 0f, (1080 + side * 4) / 2, 1920f), 4, 7, 1920f)
    }
    val columns = if (layout == ProfileStoryGridSize.LARGE) 4 else 3
    val gridHeight = if (film) 980f else 936f
    // Same VStack spacing and font line heights as the native iOS composition.
    val contentHeight = gridHeight + 64 + 49 + 20 + 87
    val top = (1920 - contentHeight) / 2
    return ProfileStoryGeometry(RectF(if (film) 210f else 72f, top, if (film) 870f else 1008f, top + gridHeight),
        columns, columns, contentHeight)
}

/** Preview, flattened export, transparent foreground and video all call this same canvas. */
internal fun renderProfileShareStory(
    context: Context, profile: ShareProfileSubject, art: PreparedProfileShareArt,
    layout: ProfileStoryGridSize, background: ProfileStoryBackground,
    transparent: Boolean = false, compactForeground: Boolean = false,
): Bitmap {
    val film = profile.featuredMoviePosterUrl != null
    require(art.slots.size >= if (film) 1 else layout.artworkLimit) { "Artwork metadata is not ready" }
    val geometry = profileStoryGeometry(layout, film)
    val full = layout.artworkLimit == 28 && !film
    if (!transparent && !full) {
        // Composite the same foreground used by the preview, rather than drawing
        // antialiased text twice against different surfaces. Even edge pixels match.
        val foreground = renderProfileShareStory(context, profile, art, layout, background,
            transparent = true, compactForeground = compactForeground)
        return Bitmap.createBitmap(1080, foreground.height, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(background.color)
            Canvas(it).drawBitmap(foreground, 0f, 0f, null)
            foreground.recycle()
        }
    }
    val height = if (compactForeground && !full) (geometry.contentHeight + 144).toInt() else 1920
    val bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (!transparent || full) canvas.drawColor(background.color)
    val shift = if (compactForeground && !full) 72 - geometry.grid.top else 0f
    canvas.save()
    canvas.translate(0f, shift)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (film) drawProfileArtwork(canvas, art.slots[0], geometry.grid, paint, fit = true)
    else drawProfileGrid(canvas, art, geometry.grid, geometry.columns, geometry.rows, paint)
    val ink = profileShareInk(background)
    if (full) drawProfileSharePill(context, canvas, profile.username, background, 1080f, 1760f, xExport = false)
    else {
        val handle = shareNunitoPaint(context, 36f, 600,
            if (background == ProfileStoryBackground.CORUS_BLUE) ink else (0x8c shl 24) or (ink and 0xffffff), Paint.Align.CENTER)
        val handleTop = geometry.grid.bottom + 64
        canvas.drawText(profile.username.let { "@$it" }, 540f, handleTop + (49 - handle.fontMetrics.ascent - handle.fontMetrics.descent) / 2, handle)
        val word = shareNunitoPaint(context, 64f, 900, ink)
        val width = 56 + 18 + word.measureText("corus")
        val left = (1080 - width) / 2
        val top = handleTop + 49 + 20
        drawTintedLogo(canvas, context, left, top + (87 - 56) / 2 + 5, 56f, ink)
        canvas.drawText("corus", left + 74, top + (87 - word.fontMetrics.ascent - word.fontMetrics.descent) / 2, word)
    }
    canvas.restore()
    return bitmap
}

internal fun renderProfileShareX(context: Context, profile: ShareProfileSubject, art: PreparedProfileShareArt,
    layout: ProfileStoryGridSize, background: ProfileStoryBackground): Bitmap {
    val film = profile.featuredMoviePosterUrl != null
    val full = layout.artworkLimit == 28 && !film
    require(art.slots.size >= if (film) 1 else layout.artworkLimit)
    val width = if (full) 1400 else 1080
    val height = if (full) 800 else 1080
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        canvas.drawColor(background.color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (film) drawProfileArtwork(canvas, art.slots[0], RectF(60f, 90f, 1020f, 990f), paint, fit = true)
        else {
            val columns = if (full) 7 else if (layout == ProfileStoryGridSize.LARGE) 4 else 3
            drawProfileGrid(canvas, art, RectF(0f, 0f, width.toFloat(), height.toFloat()), columns, if (full) 4 else columns, paint)
        }
        drawProfileSharePill(context, canvas, profile.username, background, width.toFloat(), height - if (film) 16f else 28f, xExport = true)
    }
}

private fun drawProfileGrid(canvas: Canvas, art: PreparedProfileShareArt, bounds: RectF, columns: Int, rows: Int, paint: Paint) {
    val side = bounds.width() / columns
    repeat(rows * columns) { index ->
        // Shared integer boundaries avoid AA exposing a blue hairline between
        // fractional 1920/7 tiles. Covers stay aspect-fill, never stretched.
        val column = index % columns; val row = index / columns
        val left = (bounds.left + column * side).roundToInt().toFloat()
        val top = (bounds.top + row * side).roundToInt().toFloat()
        val right = (bounds.left + (column + 1) * side).roundToInt().toFloat()
        val bottom = (bounds.top + (row + 1) * side).roundToInt().toFloat()
        drawProfileArtwork(canvas, art.slots.getOrNull(index), RectF(left, top, right, bottom), paint)
    }
}

internal fun drawProfileArtwork(canvas: Canvas, bitmap: Bitmap?, bounds: RectF, paint: Paint, fit: Boolean = false) {
    if (bitmap == null) {
        paint.color = 0xffdedee3.toInt()
        canvas.drawRect(bounds, paint)
        return
    }
    val scale = if (fit) min(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
        else max(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
    val width = bitmap.width * scale; val height = bitmap.height * scale
    val target = RectF(bounds.centerX() - width / 2, bounds.centerY() - height / 2, bounds.centerX() + width / 2, bounds.centerY() + height / 2)
    canvas.save(); canvas.clipRect(bounds); canvas.drawBitmap(bitmap, null, target, paint); canvas.restore()
}

internal fun profileShareInk(background: ProfileStoryBackground): Int = when (background) {
    ProfileStoryBackground.CORUS_BLUE -> Color.WHITE
    ProfileStoryBackground.LIGHT -> 0xff1a1a2e.toInt()
    ProfileStoryBackground.DARK -> 0xfff5f5f7.toInt()
    else -> if (background.usesDarkInk) 0xff15151a.toInt() else Color.WHITE
}

private fun drawProfileSharePill(context: Context, canvas: Canvas, username: String, background: ProfileStoryBackground,
    width: Float, bottom: Float, xExport: Boolean) {
    val ink = profileShareInk(background)
    val mark = if (xExport) 26f else 44f
    val markGap = if (xExport) 8f else 12f
    val gap = if (xExport) 24f else 36f
    val hPad = if (xExport) 22f else 40f
    val vPad = if (xExport) 12f else 28f
    val word = shareNunitoPaint(context, if (xExport) 32f else 52f, 900, ink)
    val handle = shareNunitoPaint(context, if (xExport) 30f else 48f, 600, ink)
    val brandWidth = mark + markGap + word.measureText("corus")
    val label = "@$username"
    val maxHandle = width - 144 - 2 * hPad - brandWidth - gap
    if (handle.measureText(label) > maxHandle) handle.textSize *= (maxHandle / handle.measureText(label)).coerceAtLeast(.6f)
    val pillWidth = min(width - 64, brandWidth + gap + handle.measureText(label) + 2 * hPad)
    val contentHeight = if (xExport) 44f else 71f
    val height = contentHeight + 2 * vPad
    val left = (width - pillWidth) / 2; val top = bottom - height
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background.color; setShadowLayer(12f, 0f, 4f, 0x33000000) }
    canvas.drawRoundRect(RectF(left, top, left + pillWidth, bottom), height / 2, height / 2, pillPaint)
    val centerY = top + vPad + contentHeight / 2
    drawTintedLogo(canvas, context, left + hPad - mark * .05f, centerY - mark * .55f + 3, mark * 1.1f, ink)
    val textLeft = left + hPad + mark + markGap
    canvas.drawText("corus", textLeft, centerY - (word.fontMetrics.ascent + word.fontMetrics.descent) / 2, word)
    canvas.save(); canvas.clipRect(left, top, left + pillWidth - hPad, bottom)
    canvas.drawText(label, left + hPad + brandWidth + gap, centerY - (handle.fontMetrics.ascent + handle.fontMetrics.descent) / 2, handle)
    canvas.restore()
}
