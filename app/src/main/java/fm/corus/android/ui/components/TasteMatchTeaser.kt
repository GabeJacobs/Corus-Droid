package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
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
    val label = remember(match) { teaserLabel(match) }
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
        Text(
            text = label,
            style = CorusFont.button,
            color = CorusColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
                    title = "From artists in common",
                    items = artistFillSongs.map(PreviewItem::Track),
                    onSelectPost = onSelectPost,
                    onDismiss = onDismiss,
                )
            }
            if (directorFillFilms.isNotEmpty()) {
                Section(
                    title = "From directors in common",
                    items = directorFillFilms.map(PreviewItem::Movie),
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
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
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
            text = item.title,
            style = CorusFont.songTitle,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.subtitle.isNotEmpty()) {
            Text(
                text = item.subtitle,
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

private fun teaserLabel(match: MusicMatchData): String {
    val names = mutableListOf<String>()
    val seen = HashSet<String>()
    for (p in match.sharedTrackPreviews) {
        val trimmed = p.artistName.trim()
        if (trimmed.isEmpty()) continue
        val key = trimmed.lowercase()
        if (!seen.add(key)) continue
        names += trimmed
        if (names.size >= 2) break
    }
    if (names.size < 2) {
        for (p in match.sharedMoviePreviews) {
            val trimmed = p.directorName.trim()
            if (trimmed.isEmpty()) continue
            val key = trimmed.lowercase()
            if (!seen.add(key)) continue
            names += trimmed
            if (names.size >= 2) break
        }
    }
    val totalSheetItems = match.sharedTrackPreviews.size + match.sharedMoviePreviews.size
    val extras = maxOf(0, totalSheetItems - names.size)
    if (names.isEmpty()) {
        return when {
            match.sharedArtists == 1 -> "1 artist"
            match.sharedArtists > 0 -> "${match.sharedArtists} artists"
            else -> "Taste match"
        }
    }
    val joined = names.joinToString(", ")
    return if (extras > 0) "$joined +$extras" else joined
}

