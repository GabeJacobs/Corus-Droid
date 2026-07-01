package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.model.SharedMoviePreview
import fm.corus.android.data.model.SharedPreviewKind
import fm.corus.android.data.model.SharedTrackPreview
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars

/**
 * Profile teaser pill — three overlapping album-art thumbs + shaded Venn icon
 * + truncated label like "Letrux, Gilberto Gil +2" + chevron. Tapping anywhere
 * on the capsule opens [TasteMatchSheet] with the full breakdown. Mirrors the
 * iOS `TasteMatchTeaser`.
 */
@Composable
fun TasteMatchTeaser(
    match: MusicMatchData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbs = remember(match) { thumbnailURLs(match).take(3) }
    val names = remember(match) { sharedNames(match) }
    val totalSheetItems = remember(match) { totalSheetItems(match) }
    val measurer = rememberTextMeasurer()
    val labelStyle = CorusFont.button
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(100))
            .border(1.dp, CorusColors.Divider, RoundedCornerShape(100))
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        if (thumbs.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-8).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                thumbs.forEachIndexed { _, url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                            .background(CorusColors.Divider),
                    )
                }
            }
        }
        VennDiagramIcon(
            size = 16.dp,
            color = CorusColors.Secondary,
            shadedIntersection = true,
        )
        // Names truncate to whatever fits; the "+N" count is always kept.
        // chooseTeaserLabel drops from two names to one (folding the dropped
        // name into the count) before any name is chopped mid-word, mirroring
        // the iOS ViewThatFits behavior.
        if (names.isEmpty()) {
            Text(
                text = emptyLabel(match),
                style = labelStyle,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val maxWidthPx = constraints.maxWidth
                val (namesText, extraText) = remember(names, totalSheetItems, maxWidthPx) {
                    chooseTeaserLabel(names, totalSheetItems, maxWidthPx) { candidate ->
                        measurer.measure(candidate, labelStyle, maxLines = 1).size.width
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = namesText,
                        style = labelStyle,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (extraText != null) {
                        Text(
                            text = extraText,
                            style = labelStyle,
                            color = CorusColors.Secondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CorusColors.Secondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Tile entry on the sheet — represents either a track or a film preview. */
private sealed class PreviewItem {
    abstract val imageUrl: String?
    abstract val title: String
    abstract val subtitle: String
    abstract val postId: String?
    abstract val key: String

    data class Track(val preview: SharedTrackPreview) : PreviewItem() {
        override val imageUrl: String? get() = preview.albumArtURL
        override val title: String get() = preview.trackName
        override val subtitle: String get() = preview.artistName
        override val postId: String? get() = preview.postId
        override val key: String get() = "t-${preview.trackId}-${preview.albumArtURL.orEmpty()}"
    }

    data class Movie(val preview: SharedMoviePreview) : PreviewItem() {
        override val imageUrl: String? get() = preview.posterURL
        override val title: String get() = preview.movieTitle
        override val subtitle: String get() = preview.directorName
        override val postId: String? get() = preview.postId
        override val key: String get() = "m-${preview.movieId}"
    }
}

/**
 * Bottom sheet with up to four labeled sections of shared content. Mirrors the
 * iOS `TasteMatchDetailSheet`. Tapping a tile dismisses the sheet and routes
 * to the target's post via [onSelectPost].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasteMatchSheet(
    username: String,
    match: MusicMatchData,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onSelectPost: (postId: String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CorusColors.Background,
        dragHandle = null,
        // Clear the display cutout (punch-hole/notch), not just the status bar,
        // so the hero header stops just under the safe zone instead of riding up
        // into the camera. Default windowInsets only accounts for system bars.
        contentWindowInsets = { WindowInsets.systemBars.union(WindowInsets.displayCutout) },
    ) {
        CorusSystemBars()
        val sharedSongs = match.sharedTrackPreviews.filter { it.kind == SharedPreviewKind.SHARED_SONG }
        val artistFillSongs = match.sharedTrackPreviews.filter { it.kind == SharedPreviewKind.SHARED_ARTIST }
        val sharedFilms = match.sharedMoviePreviews.filter { it.kind == SharedPreviewKind.SHARED_SONG }
        val directorFillFilms = match.sharedMoviePreviews.filter { it.kind == SharedPreviewKind.SHARED_ARTIST }

        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(top = CorusSpacing.xxl, bottom = CorusSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xl),
        ) {
            // Hero header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                VennDiagramIcon(size = 40.dp, color = CorusColors.Text, shadedIntersection = true)
                Text(
                    text = "TASTE MATCH",
                    style = CorusFont.sectionHeader.copy(letterSpacing = 1.5.sp),
                    color = CorusColors.Secondary,
                )
                Text(
                    text = "You and @$username",
                    style = CorusFont.displayName,
                    color = CorusColors.Text,
                )
            }

            if (sharedSongs.isNotEmpty()) {
                Section(
                    title = "Songs in common",
                    items = sharedSongs.map(PreviewItem::Track),
                    onSelectPost = onSelectPost,
                    onDismiss = onDismiss,
                )
            }
            if (sharedFilms.isNotEmpty()) {
                Section(
                    title = "Films in common",
                    items = sharedFilms.map(PreviewItem::Movie),
                    onSelectPost = onSelectPost,
                    onDismiss = onDismiss,
                )
            }
            if (artistFillSongs.isNotEmpty()) {
                Section(
                    title = "Artists in common",
                    items = artistFillSongs.map(PreviewItem::Track),
                    nameFirst = true,
                    onSelectPost = onSelectPost,
                    onDismiss = onDismiss,
                )
            }
            if (directorFillFilms.isNotEmpty()) {
                Section(
                    title = "Directors in common",
                    items = directorFillFilms.map(PreviewItem::Movie),
                    nameFirst = true,
                    onSelectPost = onSelectPost,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    items: List<PreviewItem>,
    onSelectPost: (String) -> Unit,
    onDismiss: () -> Unit,
    nameFirst: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        Text(
            text = title,
            style = CorusFont.sectionHeader,
            color = CorusColors.Secondary,
        )
        // Flow the tiles into a 3-column grid via wrapping Rows. LazyVGrid
        // would conflict with the parent verticalScroll, so we chunk manually.
        items.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                row.forEach { item ->
                    Tile(
                        item = item,
                        modifier = Modifier.weight(1f),
                        nameFirst = nameFirst,
                        onClick = {
                            val postId = item.postId
                            if (postId != null) {
                                onDismiss()
                                onSelectPost(postId)
                            }
                        },
                    )
                }
                // Pad the last row so partial rows align left.
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Tile(
    item: PreviewItem,
    modifier: Modifier = Modifier,
    nameFirst: Boolean = false,
    onClick: () -> Unit,
) {
    // When true (artist/director sections), lead with the shared name and put
    // the specific song/film they posted underneath, so the bold line always
    // names the thing in common.
    val leadWithName = nameFirst && item.subtitle.isNotEmpty()
    val primary = if (leadWithName) item.subtitle else item.title
    val secondary = if (leadWithName) item.title else item.subtitle
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(tasteMatchTileAspectRatio(item is PreviewItem.Movie))
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .background(CorusColors.Divider),
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = primary,
            style = CorusFont.songTitle,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (secondary.isNotEmpty()) {
            Text(
                text = secondary,
                style = CorusFont.artistName,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun thumbnailURLs(match: MusicMatchData): List<String> {
    val urls = mutableListOf<String>()
    for (p in match.sharedTrackPreviews) p.albumArtURL?.let { urls += it }
    for (p in match.sharedMoviePreviews) p.posterURL?.let { urls += it }
    return urls
}

/** Up to two deduped artist/director names from the shared previews. */
private fun sharedNames(match: MusicMatchData): List<String> {
    val names = mutableListOf<String>()
    val seen = HashSet<String>()
    for (p in match.sharedTrackPreviews) {
        val trimmed = p.artistName.trim()
        if (trimmed.isEmpty() || !seen.add(trimmed.lowercase())) continue
        names += trimmed
        if (names.size >= 2) break
    }
    if (names.size < 2) {
        for (p in match.sharedMoviePreviews) {
            val trimmed = p.directorName.trim()
            if (trimmed.isEmpty() || !seen.add(trimmed.lowercase())) continue
            names += trimmed
            if (names.size >= 2) break
        }
    }
    return names
}

/** Every distinct thing the sheet would surface, the base for the "+N" count.
 *  One tile per shared post (a multi-artist track collapses to a single tile),
 *  so this counts the discrete things the user actually sees. */
internal fun totalSheetItems(match: MusicMatchData): Int =
    match.sharedTrackPreviews.size + match.sharedMoviePreviews.size

/** Artwork aspect ratio for a sheet tile: films keep their native 2:3 poster,
 *  songs and artists stay square. Mirrors iOS (MoviePosterView vs AlbumArtView)
 *  and web (poster variant). */
internal fun tasteMatchTileAspectRatio(isMovie: Boolean): Float = if (isMovie) 2f / 3f else 1f

/** Fallback label when there are no usable names to lead with. */
private fun emptyLabel(match: MusicMatchData): String = when {
    match.sharedArtists == 1 -> "1 artist"
    match.sharedArtists > 0 -> "${match.sharedArtists} artists"
    else -> "Taste match"
}

/**
 * Decide the teaser label. Prefer two names; drop to one (folding the hidden
 * name into the "+N" overflow count) before any name is truncated mid-word.
 * Width measurement is injected so the decision is pure and unit-testable.
 * Mirrors the iOS `ViewThatFits`. Returns (namesText, extraText-or-null);
 * extraText is null when there is no overflow.
 */
internal fun chooseTeaserLabel(
    names: List<String>,
    totalSheetItems: Int,
    maxWidthPx: Int,
    measure: (String) -> Int,
): Pair<String, String?> {
    val maxNames = names.size.coerceAtMost(2)
    for (count in maxNames downTo 1) {
        val shown = names.take(count)
        val extras = (totalSheetItems - count).coerceAtLeast(0)
        val joined = shown.joinToString(", ")
        val full = if (extras > 0) "$joined +$extras" else joined
        if (measure(full) <= maxWidthPx || count == 1) {
            return joined to (if (extras > 0) "+$extras" else null)
        }
    }
    return "" to null
}

