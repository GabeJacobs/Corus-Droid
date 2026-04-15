package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        // 2x2 grid of shared media with gaps
        if (previewImages.isNotEmpty()) {
            val gridShape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(gridShape),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs)) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
                    ) {
                        GridTile(url = previewImages.getOrNull(0), modifier = Modifier.weight(1f).fillMaxHeight())
                        GridTile(url = previewImages.getOrNull(1), modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
                    ) {
                        GridTile(url = previewImages.getOrNull(2), modifier = Modifier.weight(1f).fillMaxHeight())
                        GridTile(url = previewImages.getOrNull(3), modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        // User info row: avatar on left, text on right (matches iOS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            UserAvatarView(
                avatarURL = user.avatarURL,
                displayName = user.displayName,
                size = CorusSpacing.avatarSmall,
            )

            Column(modifier = Modifier.weight(1f)) {
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

                // Match flavor text
                val flavorText = buildMatchFlavorText(matchData)
                if (flavorText.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (matchData?.isHighMatch == true) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = CorusColors.Accent,
                                modifier = Modifier.size(10.dp),
                            )
                            Spacer(modifier = Modifier.width(CorusSpacing.xs))
                        }
                        Text(
                            text = flavorText,
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
                if (isFollowing) "Following" else "Follow",
                style = CorusFont.buttonSmall,
            )
        }
    }
}

@Composable
private fun GridTile(url: String?, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(2.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(2.dp))
                .background(CorusColors.Divider),
        )
    }
}

private fun buildMatchFlavorText(matchData: fm.corus.android.data.model.MusicMatchData?): String {
    if (matchData == null) return ""
    val totalTracks = matchData.totalSharedTracks
    val totalMovies = matchData.totalSharedMovies

    // Both music and film: show whichever count is higher
    if (totalTracks > 0 && totalMovies > 0) {
        return if (totalMovies > totalTracks) {
            if (totalMovies == 1) "1 film match" else "$totalMovies film matches"
        } else {
            if (totalTracks == 1) "1 song match" else "$totalTracks song matches"
        }
    }
    // Music only
    if (totalTracks > 0) return if (totalTracks == 1) "1 song match" else "$totalTracks song matches"
    if (matchData.sharedArtists > 0) return if (matchData.sharedArtists == 1) "1 artist match" else "${matchData.sharedArtists} artist matches"
    // Film only
    if (totalMovies > 0) return if (totalMovies == 1) "1 film match" else "$totalMovies film matches"
    if (matchData.sharedDirectors > 0) return if (matchData.sharedDirectors == 1) "1 director match" else "${matchData.sharedDirectors} director matches"
    if (matchData.adjacentArtists > 0) return "similar taste"
    return ""
}
