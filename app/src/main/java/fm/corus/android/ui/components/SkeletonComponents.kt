package fm.corus.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Async image that shows a shimmer skeleton while loading and fades in when ready.
 * Use this everywhere an image is loaded from a URL to get consistent loading UX.
 *
 * When Coil serves the image from its memory cache the shimmer and fade are
 * skipped entirely so that recycled grid items appear instantly.
 */
@Composable
fun ShimmerAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RectangleShape,
) {
    var isLoading by remember(model) { mutableStateOf(true) }
    var fromMemoryCache by remember(model) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = if (fromMemoryCache) tween(durationMillis = 0) else tween(durationMillis = 300),
        label = "shimmerFade",
    )

    Box(modifier = modifier.clip(shape)) {
        if (imageAlpha < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shimmer()
                    .background(CorusColors.Skeleton),
            )
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .alpha(imageAlpha),
            contentScale = contentScale,
            onSuccess = { state ->
                fromMemoryCache = state.result.dataSource == coil3.decode.DataSource.MEMORY_CACHE
                isLoading = false
            },
            onError = { isLoading = false },
        )
    }
}

@Composable
fun SkeletonPostCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        // Header: avatar circle + username bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(CorusSpacing.avatarSmall)
                    .clip(CircleShape)
                    .background(CorusColors.Skeleton)
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        // Full-bleed square album art placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(CorusColors.Skeleton)
        )

        // Song info bars
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg)
                .padding(top = CorusSpacing.md, bottom = CorusSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }
    }
}

@Composable
fun SkeletonUserRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(CorusSpacing.avatarMedium)
                .clip(CircleShape)
                .background(CorusColors.Skeleton)
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Spacer(modifier = Modifier.height(CorusSpacing.xs))
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }
    }
}

// SkeletonSearchUserRow — matches SuggestedUserRow in SearchScreen:
// 44dp avatar · display name / @username / subtitle column · 30dp pill follow button
@Composable
fun SkeletonSearchUserRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar (44dp matches SuggestedUserRow)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CorusColors.Skeleton)
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Display name + @username
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        // Follow pill (matches Button height=30dp, pillCornerRadius)
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(CorusSpacing.pillCornerRadius))
                .background(CorusColors.Skeleton)
        )
    }
}

// SkeletonSearchSongRow — matches SongSearchRow in SearchScreen:
// 56dp thumbnail · title/artist column · optional duration
@Composable
fun SkeletonSearchSongRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        // Album art (56dp matches CorusSpacing.albumArtThumbnail)
        Box(
            modifier = Modifier
                .size(CorusSpacing.albumArtThumbnail)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                .background(CorusColors.Skeleton)
        )

        // Title + artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        // Duration
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )
    }
}

// SkeletonSuggestedUserRow — matches SuggestedUserRow in ExploreScreen exactly:
// 36dp avatar · username/subtitle column · trailing pill follow button
@Composable
fun SkeletonSuggestedUserRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(CorusSpacing.avatarMedium)
                .clip(CircleShape)
                .background(CorusColors.Skeleton)
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Username + subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        // Follow pill (matches Button height=32dp, pillCornerRadius)
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(CorusSpacing.pillCornerRadius))
                .background(CorusColors.Skeleton)
        )
    }
}

// 1. SkeletonProfileView — Full profile header
@Composable
fun SkeletonProfileView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        // Display name
        Box(
            modifier = Modifier
                .padding(top = CorusSpacing.xs)
                .align(Alignment.CenterHorizontally)
                .width(140.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )

        Spacer(modifier = Modifier.height(CorusSpacing.md))

        // Avatar + stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar (72dp circle)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Skeleton)
            )

            Spacer(modifier = Modifier.width(CorusSpacing.md))

            Column(
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Stats row (3 columns)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xxl),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    repeat(3) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CorusColors.Skeleton)
                            )
                            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CorusColors.Skeleton)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                // Edit profile button bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg)
                        .height(30.dp)
                        .clip(RoundedCornerShape(CorusSpacing.pillCornerRadius))
                        .background(CorusColors.Skeleton)
                )
            }
        }

        // Username + bio
        Column(
            modifier = Modifier
                .padding(start = 28.dp, end = CorusSpacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

// 1b. SkeletonProfileWithAvatar — Profile header skeleton with a pre-loaded avatar
@Composable
fun SkeletonProfileWithAvatar(
    avatarURL: String?,
    avatarThumbURL: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        // Display name shimmer
        Box(
            modifier = Modifier
                .padding(top = CorusSpacing.xs)
                .align(Alignment.CenterHorizontally)
                .width(140.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
                .background(CorusColors.Skeleton)
        )

        Spacer(modifier = Modifier.height(CorusSpacing.md))

        // Avatar + stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Real avatar (pre-loaded from feed)
            UserAvatarView(
                avatarURL = avatarURL,
                avatarThumbURL = avatarThumbURL,
                size = CorusSpacing.avatarLarge,
            )

            Spacer(modifier = Modifier.width(CorusSpacing.md))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .shimmer(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Stats row (3 columns)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xxl),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    repeat(3) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CorusColors.Skeleton)
                            )
                            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CorusColors.Skeleton)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                // Follow button bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg)
                        .height(30.dp)
                        .clip(RoundedCornerShape(CorusSpacing.pillCornerRadius))
                        .background(CorusColors.Skeleton)
                )
            }
        }

        // Username + bio shimmer
        Column(
            modifier = Modifier
                .padding(start = 28.dp, end = CorusSpacing.lg)
                .shimmer(),
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

// 2. SkeletonProfileGrid — Featured post + 3-column grid
@Composable
fun SkeletonProfileGrid(
    showFeatured: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        if (showFeatured) {
            SkeletonFeaturedCymbal()
        }

        // 3-column grid of 6 cells (1:1 aspect)
        val cellCount = if (showFeatured) 6 else 15
        for (row in 0 until (cellCount + 2) / 3) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    if (index < cellCount) {
                        SkeletonAlbumGridCell(
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// 3. SkeletonCommentRow — Avatar + username + comment text + timestamp
@Composable
fun SkeletonCommentRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            // Avatar (36dp — avatarMedium)
            Box(
                modifier = Modifier
                    .size(CorusSpacing.avatarMedium)
                    .clip(CircleShape)
                    .background(CorusColors.Skeleton)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                // Username
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CorusColors.Skeleton)
                )
                // Comment line 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CorusColors.Skeleton)
                )
                // Comment line 2 (partial)
                Row {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CorusColors.Skeleton)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Reply placeholder
        Box(
            modifier = Modifier
                .padding(
                    start = CorusSpacing.lg + CorusSpacing.avatarMedium + CorusSpacing.sm,
                    bottom = CorusSpacing.xs,
                )
                .width(36.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )
    }
}

// 4. SkeletonTrendingSongRow — matches TrendingSongRow in ExploreScreen exactly:
// rank 28dp width · 48dp album art · title/artist column · trailing count
@Composable
fun SkeletonTrendingSongRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rank placeholder: 14dp bar centered inside a 28dp-wide cell (matches Text width)
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Square album art
        Box(
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                .background(CorusColors.Skeleton)
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Title + artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(75.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        // Cymbal count
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )
    }
}

// 5. SkeletonHashtagCard — 2x2 grid + hashtag name + post count
@Composable
fun SkeletonHashtagCard() {
    Column(
        modifier = Modifier
            .width(140.dp)
            .shimmer(),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        // 2x2 grid of small squares
        val gridSize = 140.dp
        val gap = 2.dp
        val tileSize = (140 - 2) / 2 // 69dp

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Box(modifier = Modifier.size(tileSize.dp).background(CorusColors.Skeleton))
                Box(modifier = Modifier.size(tileSize.dp).background(CorusColors.Skeleton))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Box(modifier = Modifier.size(tileSize.dp).background(CorusColors.Skeleton))
                Box(modifier = Modifier.size(tileSize.dp).background(CorusColors.Skeleton))
            }
        }

        // Hashtag name
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(13.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )

        // Post count
        Box(
            modifier = Modifier
                .width(55.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )
    }
}

// 6. SkeletonAlbumGridCell — Single rectangle with configurable aspect ratio
@Composable
fun SkeletonAlbumGridCell(
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
) {
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .background(CorusColors.Skeleton)
    )
}

// 7. SkeletonSongRow — matches SongSearchRow / compose SearchResultRow:
// 48dp album art · title/artist column · trailing duration
@Composable
fun SkeletonSongRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        // Square album art (matches CorusSpacing.albumArtSearch = 48dp)
        Box(
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                .background(CorusColors.Skeleton)
        )

        // Title + artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        // Duration
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )
    }
}

// 8. SkeletonFilmRow — matches FilmSearchResultRow:
// 40×60 poster · title/director column
@Composable
fun SkeletonFilmRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        // Poster rectangle (40dp wide, 2:3 ratio = 60dp tall)
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                .background(CorusColors.Skeleton)
        )

        // Title + year
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }
    }
}

// 8b. SkeletonTrendingFilmRow — matches TrendingMovieRow in ExploreScreen exactly:
// rank 28dp cell · 48×72 poster · title/director column · trailing count
@Composable
fun SkeletonTrendingFilmRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rank placeholder: 14dp bar centered inside a 28dp-wide cell (matches Text width)
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Poster (48 × 72 matches real AsyncImage size)
        Box(
            modifier = Modifier
                .size(width = CorusSpacing.albumArtSearch, height = 72.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                .background(CorusColors.Skeleton)
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Title + director
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        // Cymbal count
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )
    }
}

// 8c. SkeletonFilmDetailHeader — Poster + title + buttons for film detail page
@Composable
fun SkeletonFilmDetailHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(top = CorusSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Poster placeholder
        Box(
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(CorusColors.Skeleton),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.md))

        // Title placeholder
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.xs))

        // Director placeholder
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.md))

        // Button placeholders
        Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md)) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CorusColors.Skeleton),
            )
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CorusColors.Skeleton),
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.md))
    }
}

// 9. SkeletonSectionHeader — Shimmer placeholder for section headers (matches iOS)
@Composable
fun SkeletonSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )
        Spacer(modifier = Modifier.width(CorusSpacing.xs))
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )
    }
}

// 10. SkeletonTasteMatchCard — 2x2 album grid + avatar + username + follow button in card
@Composable
fun SkeletonTasteMatchCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .shimmer()
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusLarge))
            .border(
                width = 0.5.dp,
                color = CorusColors.Divider,
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusLarge),
            )
            .background(CorusColors.Skeleton)
            .padding(CorusSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        // 2x2 album art grid
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
        ) {
            val gap = CorusSpacing.xxs
            val tileSize = (maxWidth - gap) / 2

            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Box(modifier = Modifier.size(tileSize).background(CorusColors.Skeleton))
                    Box(modifier = Modifier.size(tileSize).background(CorusColors.Skeleton))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Box(modifier = Modifier.size(tileSize).background(CorusColors.Skeleton))
                    Box(modifier = Modifier.size(tileSize).background(CorusColors.Skeleton))
                }
            }
        }

        // User info row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(CorusSpacing.avatarSmall)
                    .clip(CircleShape)
                    .background(CorusColors.Skeleton)
            )
            Column(verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs)) {
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CorusColors.Skeleton)
                )
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CorusColors.Skeleton)
                )
            }
        }

        // Follow button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(CorusSpacing.pillCornerRadius))
                .background(CorusColors.Skeleton)
        )
    }
}

// 10. SkeletonMessageThreadRow — Avatar + username + message preview + timestamp
@Composable
fun SkeletonMessageThreadRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        // Circle avatar (48dp — avatarMedium)
        Box(
            modifier = Modifier
                .size(CorusSpacing.avatarMedium)
                .clip(CircleShape)
                .background(CorusColors.Skeleton)
        )

        // Username + message preview
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        // Timestamp (right-aligned)
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton)
        )
    }
}

// 11. SkeletonNotificationRow — Avatar + text bars + optional small square
@Composable
fun SkeletonNotificationRow(
    showAlbumArt: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        // Circle avatar (40dp — avatarMedium)
        Box(
            modifier = Modifier
                .size(CorusSpacing.avatarMedium)
                .clip(CircleShape)
                .background(CorusColors.Skeleton)
        )

        // 2-line text
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        // Optional small square (album art or follow button)
        if (showAlbumArt) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                    .background(CorusColors.Skeleton)
            )
        } else {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(CorusSpacing.pillCornerRadius))
                    .background(CorusColors.Skeleton)
            )
        }
    }
}

// 12. SkeletonFilmInfoSheet — Loading skeleton for the film info bottom sheet
@Composable
fun SkeletonFilmInfoSheet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        // Header: poster + title + year + genre chip
        Row(
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg)
                .padding(top = CorusSpacing.xl, bottom = CorusSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CorusColors.Skeleton)
            )
            Column(verticalArrangement = Arrangement.spacedBy(CorusSpacing.xs)) {
                Box(
                    modifier = Modifier
                        .width(170.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CorusColors.Skeleton)
                )
                Box(
                    modifier = Modifier
                        .width(85.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CorusColors.Skeleton)
                )
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(CorusColors.Skeleton)
                )
            }
        }

        HorizontalDivider(color = CorusColors.Divider)

        // Country / Language rows
        Column(
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            SkeletonInfoRow(labelWidth = 58.dp, valueWidth = 180.dp)
            SkeletonInfoRow(labelWidth = 65.dp, valueWidth = 60.dp)
        }

        HorizontalDivider(color = CorusColors.Divider)

        // Director / Screenplay rows
        Column(
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            SkeletonInfoRow(labelWidth = 58.dp, valueWidth = 100.dp)
            SkeletonInfoRow(labelWidth = 75.dp, valueWidth = 190.dp)
        }

        HorizontalDivider(color = CorusColors.Divider)

        // Synopsis
        Column(
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .width(66.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        HorizontalDivider(color = CorusColors.Divider)

        // Cast
        Column(
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Spacer(modifier = Modifier.height(2.dp))
            repeat(4) {
                Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm)) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CorusColors.Skeleton)
                    )
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CorusColors.Skeleton)
                    )
                }
            }
        }

        HorizontalDivider(color = CorusColors.Divider)

        // Link buttons
        Row(
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(135.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(CorusColors.Skeleton)
            )
        }
    }
}

@Composable
private fun SkeletonInfoRow(labelWidth: Dp, valueWidth: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm)) {
        Box(
            modifier = Modifier
                .width(labelWidth)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton),
        )
        Spacer(modifier = Modifier.width(80.dp - labelWidth))
        Box(
            modifier = Modifier
                .width(valueWidth)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton),
        )
    }
}

// 13. SkeletonFeaturedCymbal — Large rectangle for featured post area
@Composable
fun SkeletonFeaturedCymbal() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        // Main featured area (~0.76 aspect ratio matching vinyl canvas: 448/585)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(585f / 448f)
                .background(CorusColors.Skeleton)
        )

        // Title + artist bars below
        Column(
            modifier = Modifier.padding(
                horizontal = CorusSpacing.lg,
                vertical = CorusSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }
    }
}
