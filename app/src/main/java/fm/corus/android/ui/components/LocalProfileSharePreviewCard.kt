package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusSpacing

private const val DESIGN_WIDTH = 1200f
private const val CARD_ASPECT = DESIGN_WIDTH / 630f

/**
 * Client-rendered profile share card. Mirrors iOS `LocalProfileSharePreviewCard.swift`
 * and the server OG card in `shareCard.js` (1200×630).
 */
@Composable
fun LocalProfileSharePreviewCard(
    profile: ShareProfileSubject,
    theme: ShareCardTheme,
    modifier: Modifier = Modifier,
) {
    val palette = ProfileSharePalette.forTheme(theme)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = screenWidth - CorusSpacing.lg * 2
    val scale = cardWidth.value / DESIGN_WIDTH

    fun s(design: Float) = (design * scale).dp

    val gridCount = when {
        profile.artworkUrls.size >= 9 -> 9
        profile.artworkUrls.size >= 4 -> 4
        profile.artworkUrls.isNotEmpty() -> 1
        else -> 0
    }
    val artworkUrls = profile.artworkUrls.take(gridCount)
    val titleSize = displayNameSize(profile.displayName ?: profile.username, scale)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = CorusSpacing.lg,
                end = CorusSpacing.lg,
                top = CorusSpacing.sm,
                bottom = CorusSpacing.xs,
            )
            .aspectRatio(CARD_ASPECT)
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(palette.backdrop)
            .border(0.5.dp, CorusColors.Divider, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .padding(s(56f)),
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(s(44f))) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s(18f)),
                ) {
                    if (!profile.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(s(96f))
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    Text(
                        text = "@${profile.username}",
                        fontSize = s(40f).value.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username,
                    fontSize = titleSize,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = s(14f)),
                )

                profile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    Text(
                        text = bio,
                        fontSize = s(24f).value.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.muted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = s(14f)),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s(12f)),
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.logo_no_background),
                        contentDescription = null,
                        modifier = Modifier.size(s(30f)),
                        colorFilter = ColorFilter.tint(palette.ink),
                    )
                    Text(
                        text = "corus.fm",
                        fontSize = s(24f).value.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.dim,
                    )
                }
            }

            ArtColumn(
                urls = artworkUrls,
                gridCount = gridCount,
                palette = palette,
                side = s(484f),
            )
        }
    }
}

@Composable
private fun ArtColumn(
    urls: List<String>,
    gridCount: Int,
    palette: ProfileSharePalette,
    side: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .width(side)
            .fillMaxHeight(),
    ) {
        when {
            gridCount == 0 -> {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.logo_no_background),
                    contentDescription = null,
                    modifier = Modifier
                        .size(side * 0.28f)
                        .align(Alignment.Center),
                    colorFilter = ColorFilter.tint(palette.ink.copy(alpha = 0.14f)),
                )
            }
            gridCount == 1 && urls.isNotEmpty() -> {
                AsyncImage(
                    model = urls.first(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> {
                val cols = if (gridCount == 9) 3 else 2
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(urls) { _, url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxWidth(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

private fun displayNameSize(title: String, scale: Float): androidx.compose.ui.unit.TextUnit {
    val n = title.trim().length
    val design = when {
        n > 44 -> 44f
        n > 26 -> 56f
        else -> 66f
    }
    return (design * scale).sp
}
