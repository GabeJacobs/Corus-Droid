package fm.corus.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valentinilk.shimmer.shimmer
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusMotion
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Async image that keeps a shimmer bone underneath and fades the bitmap over it.
 * Matches iOS: art never pops, never flashes a black/divider hole, and never
 * drops to a dead grey between skeleton and image.
 *
 * [skipFadeOnMemoryCache] is for recycled avatars that should appear instantly
 * after the first decode. Album art / posters leave it off so a prefetch cache
 * hit still fades over the shimmering bone.
 *
 * Search sets [LocalSkipImageRevealWhenCached] so on-device (memory or disk)
 * art paints immediately — no shimmer after a quit/reopen.
 */
val LocalSkipImageRevealWhenCached = compositionLocalOf { false }

private fun coilCacheKey(model: Any?): String? = when (model) {
    is ImageRequest -> model.data?.toString()
    is String -> model
    else -> model?.toString()
}

@Composable
fun ShimmerAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RectangleShape,
    /** Solid primary@0.16 circle/rect instead of shimmer — frosted player surfaces. */
    usesSolidLoadingPlaceholder: Boolean = false,
    skipFadeOnMemoryCache: Boolean = false,
    colorFilter: ColorFilter? = null,
    /** Invoked after the paint succeeds; used by callers that size from intrinsic aspect. */
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
) {
    // Key loading state on a stable identity. Callers often pass a freshly-built
    // ImageRequest each recomposition, so keying on `model` directly would reset
    // isLoading every frame and flash the shimmer over already-loaded images.
    val modelKey = when (model) {
        is ImageRequest -> model.data
        else -> model
    }
    val skipRevealWhenCached = LocalSkipImageRevealWhenCached.current
    val context = LocalContext.current
    // Coil's loader-level crossfade paints a black frame in dark mode before
    // our alpha fade. Kill it here so every caller fades over the bone only.
    val request = remember(modelKey) {
        when (val m = model) {
            is ImageRequest -> m.newBuilder().crossfade(false).build()
            else -> ImageRequest.Builder(context).data(m).crossfade(false).build()
        }
    }
    val cachedOnDevice = remember(modelKey, skipRevealWhenCached) {
        if (!skipRevealWhenCached) {
            false
        } else {
            val key = coilCacheKey(modelKey) ?: return@remember false
            val loader = SingletonImageLoader.get(context)
            if (loader.memoryCache?.get(MemoryCache.Key(key)) != null) {
                true
            } else {
                loader.diskCache?.openSnapshot(key)?.use { true } ?: false
            }
        }
    }
    var isLoading by remember(modelKey) { mutableStateOf(!cachedOnDevice) }
    var fromMemoryCache by remember(modelKey) { mutableStateOf(cachedOnDevice) }
    val skipFade = cachedOnDevice ||
        ((skipFadeOnMemoryCache || skipRevealWhenCached) && fromMemoryCache)
    val imageAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (skipFade) 0 else CorusMotion.IMAGE_REVEAL_MS,
        ),
        label = "shimmerFade",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (usesSolidLoadingPlaceholder) {
                    Modifier.background(CorusColors.Text.copy(alpha = 0.16f))
                } else {
                    Modifier.background(CorusColors.Skeleton)
                },
            ),
    ) {
        if (imageAlpha < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (usesSolidLoadingPlaceholder) {
                            Modifier.background(CorusColors.Text.copy(alpha = 0.16f))
                        } else {
                            Modifier
                                .shimmer()
                                .background(CorusColors.Skeleton)
                        },
                    ),
            )
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .alpha(imageAlpha),
            contentScale = contentScale,
            colorFilter = colorFilter,
            onSuccess = { state ->
                val source = state.result.dataSource
                fromMemoryCache = source == coil3.decode.DataSource.MEMORY_CACHE
                if (skipRevealWhenCached &&
                    (source == coil3.decode.DataSource.MEMORY_CACHE ||
                        source == coil3.decode.DataSource.DISK)
                ) {
                    fromMemoryCache = true
                }
                isLoading = false
                onSuccess?.invoke(state)
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
        // Same row as PostCard: 28dp avatar, name, 28dp "..." slot.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.postHeaderVertical),
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
                    .width(96.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(CorusSpacing.avatarSmall))
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
fun SkeletonProfileView(
    // Own ProfileScreen has a 40dp customize/settings icon band above the name,
    // so its skeleton reserves it. OtherProfileScreen keeps those icons in the
    // TopAppBar and renders the name as a plain centered Text flush at the top —
    // passing false drops the icon band so the name doesn't sit ~20dp too low.
    showIconHeaderRow: Boolean = true,
) {
    val isWideHeader = LocalConfiguration.current.screenWidthDp >= 400
    val headerHPad = if (isWideHeader) 28.dp else CorusSpacing.xl
    val headerAvatarSize = if (isWideHeader) CorusSpacing.avatarLarge else 68.dp
    val avatarHPad = headerHPad + 8.dp
    val usernameStartPad = avatarHPad
    val usernameEndPad = avatarHPad

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(),
    ) {
        if (showIconHeaderRow) {
            // Header row — mirrors the loaded OWN-profile header (40dp customize
            // icon, centered display name, 24dp settings icon) so the skeleton
            // reserves the same ~64dp top band and content doesn't jump on load.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Reserve the 40dp customize-icon slot (no shimmer — it's an action
                // button, not loading content) so the header height still matches.
                Spacer(modifier = Modifier.size(40.dp))
                // Display name placeholder
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CorusColors.Skeleton)
                )
                // Trailing chrome placeholder — playlist (24) + gap + settings (24)
                // matches the loaded own-profile title row so content doesn't jump.
                Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(CorusColors.Skeleton)
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(CorusColors.Skeleton)
                    )
                }
            }
        } else {
            // OtherProfileScreen header: the real layout is just a centered display
            // name Text at the very top (icons are in the TopAppBar). Match it — a
            // centered placeholder with the displayName line-height footprint, no
            // icon band — so the skeleton name lines up with the loaded name.
            // Mirrors SkeletonProfileWithAvatar's name placeholder.
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(140.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        // Avatar + stats row. Own ProfileScreen's loaded row has no vertical
        // padding (its gap comes from the Spacer below), but OtherProfileScreen's
        // loaded row uses vertical = md — so match each to keep the avatar aligned.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = avatarHPad,
                    vertical = if (showIconHeaderRow) 0.dp else CorusSpacing.md,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar circle — size matches the real header
            Box(
                modifier = Modifier
                    .size(headerAvatarSize)
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
                        // Stat placeholder — wrapper boxes match the real Text line
                        // heights (stat 18sp ≈ 24dp, statLabel 11sp ≈ 14dp) so the
                        // overall column height matches the loaded header exactly.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.height(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CorusColors.Skeleton)
                                )
                            }
                            Box(
                                modifier = Modifier.height(14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
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
                }

                Spacer(modifier = Modifier.height(CorusSpacing.sm))

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

        Spacer(modifier = Modifier.height(CorusSpacing.md))

        // Username + bio
        Column(
            modifier = Modifier
                .padding(start = usernameStartPad, end = usernameEndPad),
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
    val isWideHeader = LocalConfiguration.current.screenWidthDp >= 400
    val headerHPad = if (isWideHeader) 28.dp else CorusSpacing.xl
    val headerAvatarSize = if (isWideHeader) CorusSpacing.avatarLarge else 68.dp
    val avatarHPad = headerHPad + 8.dp
    val usernameStartPad = avatarHPad
    val usernameEndPad = avatarHPad

    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        // Display name shimmer — vertical footprint matches CorusFont.displayName line height
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .align(Alignment.CenterHorizontally)
                .width(140.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
                .background(CorusColors.Skeleton)
        )

        // Avatar + stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = avatarHPad, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Real avatar (pre-loaded from feed)
            UserAvatarView(
                avatarURL = avatarURL,
                avatarThumbURL = avatarThumbURL,
                size = headerAvatarSize,
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
                        // See SkeletonProfileView — same line-height matching.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.height(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CorusColors.Skeleton)
                                )
                            }
                            Box(
                                modifier = Modifier.height(14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
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
                }

                Spacer(modifier = Modifier.height(CorusSpacing.sm))

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

        Spacer(modifier = Modifier.height(6.dp))

        // Username + bio shimmer
        Column(
            modifier = Modifier
                .padding(start = usernameStartPad, end = usernameEndPad)
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
    isFilmStyle: Boolean = false,
    frameStyle: fm.corus.android.data.model.FrameStyle = fm.corus.android.data.model.FrameStyle.BLACK,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (showFeatured) {
            if (isFilmStyle) {
                // Film featured area renders the real frame asset; shimmer is
                // applied internally (poster opening + title bars) so the frame
                // itself isn't shimmered.
                SkeletonFeaturedMoviePoster(frameStyle = frameStyle)
            } else {
                SkeletonFeaturedCymbal()
            }
        }

        // 3-column grid — cells use 2:3 posters for film, 1:1 for music
        val cellCount = if (showFeatured) 6 else 15
        val cellAspect = if (isFilmStyle) 2f / 3f else 1f
        Column(modifier = Modifier.shimmer()) {
            for (row in 0 until (cellCount + 2) / 3) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 3) {
                        val index = row * 3 + col
                        if (index < cellCount) {
                            SkeletonAlbumGridCell(
                                modifier = Modifier.weight(1f),
                                aspectRatio = cellAspect,
                            )
                        }
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

// 4. SkeletonTrendingSongRow — matches TrendingSongRow in SearchScreen exactly:
// rank 24dp · 44dp album art · title/artist column · trailing count
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
        // Rank placeholder: 12dp bar in a 24dp-wide cell (matches Text width)
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Square album art (44dp matches SongPreviewArtwork size)
        Box(
            modifier = Modifier
                .size(44.dp)
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

// 8b. SkeletonTrendingFilmRow — matches TrendingFilmRow in SearchScreen exactly:
// rank 24dp · 33×44 poster · title/director column · trailing count
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
        // Rank placeholder: 12dp bar in a 24dp-wide cell (matches Text width)
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Poster (33 × 44 matches real AsyncImage size)
        Box(
            modifier = Modifier
                .width(33.dp)
                .height(44.dp)
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
            // CardBackground (matching the real TasteMatchCard), NOT Skeleton:
            // painting the whole card skeleton-gray hides the gray internal
            // placeholders and the card reads as one oddly tall flat slab.
            .background(CorusColors.CardBackground)
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
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
        }

        // Subtitle — TWO lines, matching the 2-line artist subtitle the real
        // TasteMatchCard reserves in the grid, so the card doesn't grow taller
        // when the real data replaces this skeleton.
        Column(verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton)
            )
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(CorusSpacing.avatarMedium)
                    .clip(CircleShape)
                    .shimmer()
                    .background(CorusColors.Skeleton)
            )

            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
                    .background(CorusColors.Skeleton)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Optional small square (album art or follow button)
            if (showAlbumArt) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                        .shimmer()
                        .background(CorusColors.Skeleton)
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(CorusSpacing.pillCornerRadius))
                        .shimmer()
                        .background(CorusColors.Skeleton)
                )
            }
        }
        // Same pulse as the bones, just a thinner / dimmer line.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CorusSpacing.lg + CorusSpacing.avatarMedium + CorusSpacing.md)
                .height(1.dp)
                .shimmer()
                .background(CorusColors.Skeleton.copy(alpha = 0.4f))
        )
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

// 13b. SkeletonFeaturedMoviePoster — Framed poster skeleton matching FeaturedMoviePosterView.
//
// Renders the actual frame asset with a shimmering placeholder sitting on top
// of the frame inside the poster opening, so the loading state matches the
// loaded layout exactly (the visual transition is just shimmer → poster
// fade-in).
@Composable
fun SkeletonFeaturedMoviePoster(frameStyle: fm.corus.android.data.model.FrameStyle = fm.corus.android.data.model.FrameStyle.BLACK) {
    // Match FeaturedMoviePosterView section proportions, including any
    // extra title clearance below Marquee.
    val posterXRatio = frameStyle.posterXFrac
    val posterYRatio = frameStyle.posterYFrac
    val posterWRatio = frameStyle.posterWFrac
    val posterHRatio = frameStyle.posterHFrac

    val frameDrawable = frameDrawableRes(frameStyle)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.36f to CorusColors.FeaturedFilmBackgroundTop,
                        1.0f to CorusColors.FeaturedFilmBackgroundBottom,
                    ),
                ),
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(frameStyle.featuredSectionAspect),
        ) {
            val w = maxWidth
            val frameH = w * fm.corus.android.data.model.FrameStyle.CANVAS_HEIGHT /
                fm.corus.android.data.model.FrameStyle.CANVAS_WIDTH

            // 1) Frame asset (drawn first, behind the shimmer).
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(frameDrawable),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(frameH)
                    .align(Alignment.TopCenter),
                contentScale = ContentScale.FillBounds,
            )

            // 2) Shimmer on top of the frame, in the poster opening. Uses a
            //    visible dark-gray base so it shows up against the frame's
            //    bright white mat (CorusColors.Skeleton washes out there).
            Box(
                modifier = Modifier
                    .offset(x = w * posterXRatio, y = frameH * posterYRatio)
                    .size(width = w * posterWRatio, height = frameH * posterHRatio)
                    .shimmer()
                    .background(Color.Black.copy(alpha = 0.12f)),
            )

            // Title shimmer overlaid at the bottom, matching FeaturedMoviePosterView.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        horizontal = CorusSpacing.lg,
                        vertical = CorusSpacing.md,
                    )
                    .shimmer(),
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
}

// 13. SkeletonFeaturedCymbal — Large rectangle for featured post area
@Composable
fun SkeletonFeaturedCymbal() {
    // Single block matching the real FeaturedCymbalView canvas height
    // (w * 448/585). The real view overlays the title/artist row at the BOTTOM
    // of this same area, so it adds no height below the art. Earlier this
    // skeleton stacked two title/artist bars *below* the block, which both
    // floated on white as orphan lines and pushed the grid ~one row down. The
    // block already covers where that text lands, so we drop the stacked bars:
    // the grid now sits flush under the featured area, matching iOS and the
    // loaded layout (no jump when the real art fades in).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(585f / 448f)
            .shimmer()
            .background(CorusColors.Skeleton)
    )
}
