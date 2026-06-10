package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Card showing a suggested user with taste match data.
 * Displays a 2x2 grid of shared media (albums/movies) plus user info and follow button.
 * Matches iOS TasteMatchCard layout.
 */
@Composable
fun TasteMatchCard(
    match: SuggestedUserMatch,
    isFollowing: Boolean,
    onUserTap: () -> Unit = {},
    onFollowTap: () -> Unit = {},
    modifier: Modifier = Modifier,
    showPreviewButton: Boolean = false,
    isPreviewLoading: Boolean = false,
    isPreviewing: Boolean = false,
    /** Overrides the auto-derived flavor text. Used by the Popular rail to
     *  show "X followers" and by Mutual Connections to show "via @user1, …". */
    subtitle: String? = null,
    /** Fixed line count reserved for the subtitle. Defaults to 2 so cards in
     *  a grid stay the same height when subtitles wrap variably; pass 1 from
     *  rails whose subtitle is guaranteed to fit on one line. */
    subtitleLines: Int = 2,
) {
    val user = match.user
    val matchData = match.matchData

    // Collect up to 4 preview images, interleaving tracks and movies for visual variety
    val previewImages = buildList {
        val trackURLs = matchData?.sharedTrackPreviews?.mapNotNull { it.displayImageURL } ?: emptyList()
        val movieURLs = matchData?.sharedMoviePreviews?.mapNotNull { it.posterURL } ?: emptyList()
        val seen = mutableSetOf<String>()
        val uniqueTrack = trackURLs.filter { seen.add(it) }
        val uniqueMovie = movieURLs.filter { seen.add(it) }
        var ti = 0; var mi = 0
        while (size < 4) {
            if (ti < uniqueTrack.size) { add(uniqueTrack[ti]); ti++ }
            if (size < 4 && mi < uniqueMovie.size) { add(uniqueMovie[mi]); mi++ }
            if (ti >= uniqueTrack.size && mi >= uniqueMovie.size) break
        }
    }

    val cardShape = RoundedCornerShape(CorusSpacing.cornerRadiusLarge)

    Column(
        modifier = modifier
            .shadow(elevation = 2.dp, shape = cardShape, ambientColor = Color.Black.copy(alpha = 0.04f))
            .clip(cardShape)
            .background(CorusColors.CardBackground)
            .border(0.5.dp, CorusColors.Divider, cardShape)
            .clickable(onClick = onUserTap)
            .padding(CorusSpacing.sm),
    ) {
        // 2x2 grid of shared media with gaps. Always rendered — even when the
        // user has no shared artwork — so empty tiles fall through to GridTile's
        // logo placeholder and the card keeps its normal (square-collage) height.
        // Matches iOS TasteMatchCard, where albumArtGrid always lays out 4 tiles.
        val gridShape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(gridShape),
        ) {
            // Edge-to-edge — matches iOS `gap = 0` in TasteMatchCard.
            Column {
                Row(modifier = Modifier.weight(1f)) {
                    GridTile(url = previewImages.getOrNull(0), modifier = Modifier.weight(1f).fillMaxHeight())
                    GridTile(url = previewImages.getOrNull(1), modifier = Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f)) {
                    GridTile(url = previewImages.getOrNull(2), modifier = Modifier.weight(1f).fillMaxHeight())
                    GridTile(url = previewImages.getOrNull(3), modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }

            if (showPreviewButton) {
                PreviewButton(
                    isLoading = isPreviewLoading,
                    isPlaying = isPreviewing,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(CorusSpacing.sm),
                )
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        // User info: centered name + taste text under the collage. The 2x2 art
        // grid already identifies the user, so the small left-aligned avatar
        // read as cluttered — dropped it and centered the block.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Username + flair badge
            UsernameWithFlair(
                username = user.username,
                isBot = user.isBot,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                flairStyle = user.flairStyle,
                style = CorusFont.username,
                color = CorusColors.Text,
            )

            val flavorText = buildFlavorText(subtitle, matchData)
            if (!flavorText.isNullOrBlank()) {
                Text(
                    text = flavorText,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                    minLines = subtitleLines,
                    maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        // Follow button
        Button(
            onClick = onFollowTap,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) CorusColors.CardBackground else CorusColors.Accent,
                contentColor = if (isFollowing) CorusColors.Secondary else Color.White,
            ),
            border = if (isFollowing) androidx.compose.foundation.BorderStroke(1.dp, CorusColors.Divider) else null,
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
        ) {
            Text(
                stringResource(
                    if (isFollowing) fm.corus.android.R.string.likes_button_following
                    else fm.corus.android.R.string.likes_button_follow
                ),
                style = CorusFont.buttonSmall,
            )
        }
    }
}

/**
 * Small white circle with play/pause icon (or loading spinner). Matches the iOS overlay
 * shown on the bottom-right of music-bot album-art grids.
 */
@Composable
private fun PreviewButton(
    isLoading: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f))
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = CorusColors.Accent,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                tint = CorusColors.Accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun GridTile(url: String?, modifier: Modifier = Modifier) {
    // Tiles abut flush — outer parent Box clips with gridShape, so per-tile
    // rounding would only create unwanted gaps inside the rounded card.
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(CorusColors.Divider),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(fm.corus.android.R.drawable.logo_no_background),
                contentDescription = null,
                colorFilter = ColorFilter.tint(CorusColors.Tertiary),
                modifier = Modifier.fillMaxSize(0.38f),
            )
        }
    }
}

/** Subtitle priority for a taste card. Mirrors iOS `matchFlavorText`:
 *   1. Explicit `subtitle` override (Popular = "X followers",
 *      Mutual Connections = "via @x, @y +N").
 *   2. Shared artist/director names (list format).
 *   3. Song/film match count ("2 song matches" / "1 film match").
 *
 *  There is intentionally NO `artistsInCommonCount` fallback. A bare
 *  "N artists in common" with no names is a stale-index ghost — the overlap
 *  index still lists it, but the viewer's live profile no longer backs it
 *  (e.g. they deleted the post). The backend now nulls that count, so taste
 *  cards show shared names or nothing — never a nameless count. */
internal fun buildFlavorText(
    subtitle: String?,
    matchData: fm.corus.android.data.model.MusicMatchData?,
): String? =
    subtitle?.takeIf { it.isNotBlank() }
        ?: buildSharedNamesSubtitle(matchData)
        ?: buildBestMatchLabel(matchData)

/** Comma-joined artist + director names (deduped, order-preserving) the viewer
 *  *actually shares* with this user. Mirrors iOS `sharedNames`. Returns null if
 *  there are no similarity signals or no names available.
 *
 *  Reads the backend's authoritative `sharedArtistNames` / `sharedDirectorNames`
 *  — NOT the preview tiles. The tiles are padded with the candidate's recent
 *  (non-shared) posts to fill the 2x2 art grid, so deriving names from them
 *  surfaced artists the viewer never posted. */
internal fun buildSharedNamesSubtitle(
    matchData: fm.corus.android.data.model.MusicMatchData?,
): String? {
    if (matchData == null || !matchData.hasSimilarityData) return null
    val seen = mutableSetOf<String>()
    val names = mutableListOf<String>()
    (matchData.sharedArtistNames + matchData.sharedDirectorNames).forEach { raw ->
        val name = raw.trim()
        if (name.isNotEmpty() && seen.add(name.lowercase())) names.add(name)
    }
    return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/** Count-based fallback label. Mirrors iOS `bestMatchLabel`. */
private fun buildBestMatchLabel(
    matchData: fm.corus.android.data.model.MusicMatchData?,
): String? {
    if (matchData == null) return null
    val totalTracks = matchData.totalSharedTracks
    val totalMovies = matchData.totalSharedMovies

    if (totalTracks > 0 && totalMovies > 0) {
        return if (totalMovies > totalTracks) {
            if (totalMovies == 1) "1 film match" else "$totalMovies film matches"
        } else {
            if (totalTracks == 1) "1 song match" else "$totalTracks song matches"
        }
    }
    if (totalTracks > 0) return if (totalTracks == 1) "1 song match" else "$totalTracks song matches"
    if (matchData.sharedArtists > 0) return if (matchData.sharedArtists == 1) "1 artist match" else "${matchData.sharedArtists} artist matches"
    if (totalMovies > 0) return if (totalMovies == 1) "1 film match" else "$totalMovies film matches"
    if (matchData.sharedDirectors > 0) return if (matchData.sharedDirectors == 1) "1 director match" else "${matchData.sharedDirectors} director matches"
    if (matchData.adjacentArtists > 0) return "similar taste"
    return null
}
