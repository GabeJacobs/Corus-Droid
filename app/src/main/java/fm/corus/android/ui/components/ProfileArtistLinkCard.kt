package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.LinkedArtist
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Combined profile module: catalog artist destination on top, optional
 * taste-match row underneath, one outlined card. Shown only when the
 * profile user is linked in `artistLinks`. Mirrors iOS `ProfileArtistLinkCard`.
 */
@Composable
fun ProfileArtistLinkCard(
    artist: LinkedArtist,
    onArtistTap: () -> Unit,
    modifier: Modifier = Modifier,
    match: MusicMatchData? = null,
    onMatchTap: (() -> Unit)? = null,
) {
    val matchRow = match?.takeIf { it.hasDisplayableTiles }
    val displayName = artist.name.ifBlank { stringResource(R.string.profile_artist_fallback) }
    val artistA11y = stringResource(R.string.profile_view_artist_page_a11y, displayName)
    val shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .padding(bottom = CorusSpacing.sm)
            .clip(shape)
            .background(CorusColors.Accent.copy(alpha = 0.08f))
            .border(1.dp, CorusColors.Accent.copy(alpha = 0.35f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onArtistTap)
                .semantics { contentDescription = artistA11y }
                .padding(CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            AsyncImage(
                model = artist.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Divider),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.profile_view_artist_page),
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CorusColors.Tertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        if (matchRow != null && onMatchTap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.md)
                    .height(1.dp)
                    .background(CorusColors.Divider),
            )
            TasteMatchTeaser(
                match = matchRow,
                onClick = onMatchTap,
                showsCapsule = false,
                modifier = Modifier.padding(
                    horizontal = CorusSpacing.md,
                    vertical = CorusSpacing.sm,
                ),
            )
        }
    }
}
