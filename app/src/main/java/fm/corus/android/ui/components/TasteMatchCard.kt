package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

    // Collect up to 4 preview images from shared tracks and movies
    val previewImages = buildList {
        matchData?.sharedTrackPreviews?.forEach { add(it.albumArtURL) }
        matchData?.sharedMoviePreviews?.forEach { add(it.posterURL) }
    }.take(4)

    Column(
        modifier = modifier
            .width(160.dp)
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(CorusColors.CardBackground)
            .clickable(onClick = onUserTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 2x2 grid of shared media
        if (previewImages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = CorusSpacing.cornerRadiusMedium, topEnd = CorusSpacing.cornerRadiusMedium)),
            ) {
                val gridItems = previewImages.take(4)
                Column {
                    Row(modifier = Modifier.weight(1f)) {
                        gridItems.getOrNull(0)?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        gridItems.getOrNull(1)?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop,
                            )
                        } ?: Spacer(modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        gridItems.getOrNull(2)?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop,
                            )
                        } ?: Spacer(modifier = Modifier.weight(1f))
                        gridItems.getOrNull(3)?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop,
                            )
                        } ?: Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        // Avatar
        UserAvatarView(
            avatarURL = user.avatarURL,
            size = CorusSpacing.avatarMedium,
        )

        Spacer(modifier = Modifier.height(CorusSpacing.xs))

        // Username + verified
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = CorusSpacing.sm),
        ) {
            Text(
                text = user.username,
                style = CorusFont.username,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.isVerified) {
                Spacer(modifier = Modifier.width(CorusSpacing.xxs))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Verified",
                    tint = CorusColors.Verified,
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        // Match flavor text
        val flavorText = buildMatchFlavorText(matchData)
        if (flavorText.isNotBlank()) {
            Text(
                text = flavorText,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = CorusSpacing.sm),
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        // Follow button
        Button(
            onClick = onFollowTap,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) CorusColors.Divider else CorusColors.Accent,
                contentColor = if (isFollowing) CorusColors.Secondary else Color.White,
            ),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
            modifier = Modifier
                .padding(horizontal = CorusSpacing.sm)
                .fillMaxWidth()
                .height(30.dp),
        ) {
            Text(
                if (isFollowing) "Following" else "Follow",
                style = CorusFont.buttonSmall,
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.md))
    }
}

private fun buildMatchFlavorText(matchData: fm.corus.android.data.model.MusicMatchData?): String {
    if (matchData == null) return ""
    val parts = mutableListOf<String>()
    val totalTracks = matchData.totalSharedTracks
    val totalMovies = matchData.totalSharedMovies
    if (totalTracks > 0) parts.add("$totalTracks shared song${if (totalTracks > 1) "s" else ""}")
    if (totalMovies > 0) parts.add("$totalMovies shared film${if (totalMovies > 1) "s" else ""}")
    if (matchData.sharedArtists > 0) parts.add("${matchData.sharedArtists} shared artist${if (matchData.sharedArtists > 1) "s" else ""}")
    return parts.firstOrNull() ?: ""
}
