package fm.corus.android.ui.screens.destination

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.R
import fm.corus.android.data.model.CorusUserLink
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.FlairStyle
import fm.corus.android.data.model.TrackCorusStats
import fm.corus.android.data.model.UserLite
import fm.corus.android.domain.CatalogPlaybackOrigin
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.QueuedTrack
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils
import kotlinx.coroutines.launch

/** Scroll so [index] ends up vertically CENTERED in the viewport, matching iOS's
 *  `proxy.scrollTo(anchor: .center)` — rather than pinned to the top, which is
 *  what a bare `animateScrollToItem` does. [rowHeightPx] is the catalog row's
 *  height; the estimate's imprecision is imperceptible against a full-screen
 *  viewport, and the scroll clamps naturally near the list edges. */
internal suspend fun LazyListState.animateScrollToItemCentered(index: Int, rowHeightPx: Int) {
    val viewportHeight = layoutInfo.viewportSize.height
    // A negative offset places the row's top BELOW the viewport top by half the
    // free space, so the row sits in the middle instead of at the top edge.
    val centerOffset = -((viewportHeight - rowHeightPx) / 2).coerceAtLeast(0)
    animateScrollToItem(index, centerOffset)
}

/** K/M count formatting shared by the destination pages ("Shared by 1.2K
 *  people"). Mirrors SongDetailScreen's formatUserCount. */
internal fun formatDestinationCount(count: Int): String = when {
    count < 1000 -> "$count"
    count < 1_000_000 -> String.format("%.2fK", count / 1000.0)
    else -> String.format("%.2fM", count / 1_000_000.0)
}

/** [CatalogTrackRow] subtitle: "{album} · {year}" on artist "Popular" (art)
 *  rows, the artist name on album-tracklist (numbered) rows. Extracted pure so
 *  the metadata line is unit-testable. Mirrors web + iOS. */
internal fun catalogRowSubtitle(track: CymbalTrack, number: Int?): String =
    if (number == null) {
        val year = track.releaseDate?.take(4).orEmpty()
        listOf(track.albumName, year).filter { it.isNotBlank() }.joinToString(" · ")
    } else {
        track.artistName
    }

/** The Corus share count shown in [CatalogTrackRow]'s trailing slot in place of
 *  the duration, or null when the track has no shares. Shown on both artist
 *  Popular (art) and album tracklist (numbered) rows. Mirrors web + iOS. */
internal fun catalogRowSharedCount(corusStats: TrackCorusStats?): Int? =
    corusStats?.count?.takeIf { it > 0 }

/** Whether the trailing slot falls back to the duration when there's no share
 *  count. Album tracklist (numbered) rows stay blank instead — mirroring web's
 *  album tracklist; artist Popular (art, [number] == null) rows keep the
 *  duration. Mirrors web + iOS. */
internal fun catalogRowShowsDuration(number: Int?, durationMs: Int): Boolean =
    number == null && durationMs > 0

/** Section header ("Popular" / "Discography" / "Recent posts"…) with an
 *  optional trailing "See all". Matches the song-detail header treatment. */
@Composable
internal fun DestinationSectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .padding(top = CorusSpacing.lg, bottom = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = CorusFont.sectionHeader, color = CorusColors.Secondary)
        Spacer(modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            Text(
                text = stringResource(R.string.destination_see_all),
                style = CorusFont.captionMedium,
                color = CorusColors.Accent,
                modifier = Modifier.clickable(onClick = onSeeAll),
            )
        }
    }
}

/** "Shared by N people" row: up to 5 overlapping avatars + count + chevron.
 *  Whole row pushes the posts see-all screen. Hidden by callers when N == 0. */
@Composable
internal fun SharedByPeopleRow(
    posters: List<UserLite>,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shown = posters.take(5)
        if (shown.isNotEmpty()) {
            Box {
                shown.forEachIndexed { index, poster ->
                    Box(
                        modifier = Modifier
                            .offset(x = (index * 18).dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(CorusColors.Background),
                        contentAlignment = Alignment.Center,
                    ) {
                        UserAvatarView(
                            avatarURL = poster.avatarUrl,
                            displayName = poster.displayName,
                            size = 26.dp,
                        )
                    }
                }
                // Reserve the overlapped width so the label doesn't overdraw.
                Spacer(modifier = Modifier.width(((shown.size - 1) * 18 + 30).dp))
            }
            Spacer(modifier = Modifier.width(CorusSpacing.md))
        }
        Text(
            text = pluralStringResource(
                R.plurals.destination_shared_by_people,
                count,
                formatDestinationCount(count),
            ),
            style = CorusFont.bodyMedium,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * "{displayName} is on Corus" badge — a tappable Accent-tinted card shown when a
 * catalog artist has been manually linked to a Corus user account. Placed below
 * the hero and above "Shared by N people", mirroring web. Tapping opens the
 * linked user's profile. Hidden by callers when there is no linked user.
 */
@Composable
internal fun ArtistOnCorusBadge(
    user: CorusUserLink,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(CorusColors.Accent.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = CorusColors.Accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(
            avatarURL = user.avatarUrl,
            displayName = user.displayName,
            size = 40.dp,
        )

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.destination_artist_is_on_corus,
                    user.displayName,
                ),
                style = CorusFont.username,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(CorusSpacing.xs))
            UsernameWithFlair(
                username = user.username,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                flairStyle = FlairStyle.from(user.profileFlair),
                isBot = user.isBot,
                showAtPrefix = true,
                style = CorusFont.captionMedium,
                color = CorusColors.Secondary,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Space-reserving shimmer for [SharedByPeopleRow] while the social fetch is in
 * flight. Mirrors the row's exact geometry (five overlapped 30dp circles + a
 * label-height bar) so content below doesn't shift when the real row lands —
 * most tapped artists ARE shared by someone, so reserving the space is the
 * right default. Collapses only in the rare count == 0 case.
 */
@Composable
internal fun SkeletonSharedByPeopleRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            repeat(5) { index ->
                Box(
                    modifier = Modifier
                        .offset(x = (index * 18).dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(CorusColors.Skeleton),
                )
            }
            Spacer(modifier = Modifier.width((4 * 18 + 30).dp))
        }
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton),
        )
    }
}

/**
 * Skeleton for the album page's NUMBERED tracklist rows. [CatalogTrackRow]
 * with a number leads with a 32dp number slot, not 52dp album art — the
 * skeleton must mirror what actually loads or the layout visibly morphs when
 * the tracklist lands.
 */
@Composable
internal fun SkeletonNumberedTrackRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shimmer()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton),
            )
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.Skeleton),
            )
        }
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton),
        )
    }
}

/**
 * A posted-by row for the destination pages: avatar (→ profile), @username +
 * flair, a compact track/film chip, caption in quotes, relative timestamp +
 * chevron. Whole row opens the post; child clickables (avatar/name, film chip)
 * consume their own taps — same two-zone pattern as SongDetailScreen's
 * PostedByRow.
 */
@Composable
internal fun DestinationPostRow(
    post: CymbalPost,
    onUserTap: () -> Unit,
    onPostTap: () -> Unit,
    /** Film chips push the film page when provided; track chips stay part of
     *  the row's post tap (mirrors web, where the chip isn't a link). */
    onFilmChipTap: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPostTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(
            avatarURL = post.user.avatarURL,
            displayName = post.user.displayName,
            size = CorusSpacing.avatarMedium,
            modifier = Modifier
                .clickable(onClick = onUserTap)
                .align(Alignment.Top),
        )

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            UsernameWithFlair(
                username = post.user.username,
                isVerified = post.user.isVerified,
                isClubMember = post.user.isClubMember,
                flairStyle = post.user.flairStyle,
                isBot = post.user.isBot,
                isFirstPoster = post.isFirstPoster,
                showAtPrefix = true,
                style = CorusFont.username,
                color = CorusColors.Text,
                modifier = Modifier.clickable(onClick = onUserTap),
            )

            Spacer(modifier = Modifier.height(CorusSpacing.xs))

            // Compact media chip — rounded card that hugs its content, like the
            // DM share chip. Film chips are independently tappable when wired.
            val chipShape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium)
            val chipModifier = Modifier
                .clip(chipShape)
                .background(CorusColors.CardBackground)
                .border(0.5.dp, CorusColors.Divider, chipShape)
                .let { if (post.isMovie && onFilmChipTap != null) it.clickable(onClick = onFilmChipTap) else it }
                .padding(CorusSpacing.xs)
            Row(
                modifier = chipModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val art = if (post.isMovie) {
                    post.posterURL ?: post.posterLargeURL
                } else {
                    post.track.albumArtURL ?: post.track.albumArtLargeURL
                }
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    modifier = Modifier
                        .let {
                            if (post.isMovie) it.width(32.dp).height(48.dp)
                            else it.size(40.dp)
                        }
                        .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Column {
                    Text(
                        text = post.displayTitle,
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (post.displaySubtitle.isNotBlank()) {
                        Text(
                            text = post.displaySubtitle,
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(CorusSpacing.xs))
            }

            val caption = post.caption?.trim()
            if (!caption.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(CorusSpacing.xs))
                Text(
                    text = "“$caption”",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DateUtils.relativeTime(LocalContext.current, post.timestamp),
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
            )
            Spacer(modifier = Modifier.width(CorusSpacing.xs))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CorusColors.Tertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * A catalog track row shared by the artist page ("Popular", leading album art
 * with a play overlay) and the album page (tracklist, leading track number that
 * swaps to the play state). Row tap opens the song page; the leading control
 * plays/pauses the preview via the app's existing preview pipeline.
 */
@Composable
internal fun CatalogTrackRow(
    track: CymbalTrack,
    nowPlaying: NowPlayingManager,
    /** null → 52dp art leading (artist Popular); non-null → this 1-based
     *  number leading (album tracklist). */
    number: Int? = null,
    /** The surrounding list (artist Popular or the album tracklist) as a queue,
     *  so starting playback here rolls on through the rest — autoplay + a live
     *  mini-player Next button. Empty falls back to single-track playback. */
    queue: List<QueuedTrack> = emptyList(),
    /** The artist/album page this row lives on, stamped onto the played track so
     *  the mini-player can return here and scroll to the song. Null elsewhere. */
    origin: CatalogPlaybackOrigin? = null,
    /** Corus share stats for this Popular row. When present with count > 0 on an
     *  ART row (number == null), the trailing slot shows a people glyph + count in
     *  place of the duration, while the subtitle keeps album·year. Matches web +
     *  iOS. */
    corusStats: TrackCorusStats? = null,
    playbackEnabled: Boolean = true,
    onRowTap: () -> Unit,
    /** Fired when a NEW preview starts (not on pause/resume of the current
     *  track) — the analytics hook. */
    onPreviewStarted: () -> Unit,
) {
    val state by nowPlaying.state.collectAsState()
    val loadingTrackId by nowPlaying.loadingTrackId.collectAsState()
    val isCurrent = state.trackId == track.id
    val isLoadingThis = loadingTrackId == track.id
    val isPlayingThis = isCurrent && state.isPlaying

    val togglePlay: () -> Unit = togglePlay@{
        if (!playbackEnabled) return@togglePlay
        if (isCurrent) {
            nowPlaying.togglePlayPause()
        } else {
            nowPlaying.routePlayTap(
                track = track,
                queue = queue,
                onPreview = {
                    if (queue.isEmpty()) {
                        nowPlaying.play(
                            trackId = track.id,
                            trackName = track.name,
                            artistName = track.artistName,
                            albumArtURL = track.albumArtURL,
                            albumArtLargeURL = track.albumArtLargeURL,
                            previewUrl = track.previewUrl,
                            spotifyURI = track.spotifyURI.ifBlank { null },
                            spotifyWebURL = track.spotifyWebURL.ifBlank { null },
                            isrc = track.isrc,
                            source = track.source,
                            soundcloudId = track.soundcloudId,
                            soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
                        )
                    } else {
                        nowPlaying.play(track = track.toQueuedTrack(origin), queue = queue)
                    }
                },
            )
            onPreviewStarted()
        }
    }

    // Both row types now share the album tap map: the ROW plays and the trailing
    // duration+chevron cluster opens the song page. `number` only picks the
    // leading visual (album art vs track number). Mirrors iOS + web.
    val rowTapPlays = true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (playbackEnabled) 1f else 0.55f }
            .clickable(onClick = if (rowTapPlays && playbackEnabled) togglePlay else onRowTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        val playCd = stringResource(
            if (isPlayingThis) R.string.song_detail_cd_pause_preview
            else R.string.song_detail_cd_play_preview
        )
        if (number == null) {
            // 52dp album art with a persistent play/pause overlay badge.
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                    .clickable(onClick = togglePlay),
            ) {
                AsyncImage(
                    model = track.albumArtURL ?: track.albumArtLargeURL,
                    contentDescription = playCd,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isLoadingThis -> CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = Color.White,
                        )
                        isPlayingThis -> Icon(
                            Icons.Filled.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        else -> Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        } else {
            // Track number that becomes the play state while current.
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = togglePlay),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoadingThis -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = CorusColors.Text,
                    )
                    isPlayingThis -> Icon(
                        Icons.Filled.Pause,
                        contentDescription = playCd,
                        tint = CorusColors.Text,
                        modifier = Modifier.size(16.dp),
                    )
                    isCurrent -> Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = playCd,
                        tint = CorusColors.Text,
                        modifier = Modifier.size(16.dp),
                    )
                    else -> Text(
                        text = "$number",
                        style = CorusFont.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = CorusColors.Tertiary,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Text(
                text = track.name,
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Artist "Popular" rows keep the full "{album} · {year}" here; the
            // share count lives in the trailing slot now (people glyph + N).
            val subtitle = catalogRowSubtitle(track, number)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Shared rows (count > 0) show the social count in the trailing slot in
        // place of the duration — on artist Popular and album tracklist rows.
        // Album (numbered) rows stay blank when unshared; artist rows fall back to
        // the duration (catalogRowShowsDuration).
        val sharedCount = catalogRowSharedCount(corusStats)
        val showsDuration = catalogRowShowsDuration(number, track.durationMs)
        if (rowTapPlays) {
            // Navigation lives here on numbered rows: duration/shared + chevron
            // cluster, mirroring the posted-by rows' timestamp + chevron. Padding
            // widens the target so navigating doesn't require pixel-perfect aim.
            Row(
                modifier = Modifier
                    .clickable(onClick = onRowTap)
                    .padding(start = CorusSpacing.md, top = CorusSpacing.xs, bottom = CorusSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                if (sharedCount != null) {
                    // A people glyph + N ("people 13") in place of the duration,
                    // matching web + iOS.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.People,
                            contentDescription = stringResource(
                                R.string.destination_n_shared,
                                formatDestinationCount(sharedCount),
                            ),
                            tint = CorusColors.Tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = formatDestinationCount(sharedCount),
                            style = CorusFont.caption.copy(fontFeatureSettings = "tnum"),
                            color = CorusColors.Tertiary,
                        )
                    }
                } else if (showsDuration) {
                    Text(
                        text = track.formattedDuration,
                        style = CorusFont.caption.copy(fontFeatureSettings = "tnum"),
                        color = CorusColors.Tertiary,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.destination_see_all),
                    tint = CorusColors.Tertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else if (showsDuration) {
            Text(
                text = track.formattedDuration,
                style = CorusFont.caption.copy(fontFeatureSettings = "tnum"),
                color = CorusColors.Tertiary,
            )
        }
    }
}

/**
 * A catalog track as a [QueuedTrack] for now-playing. Catalog tracks have no
 * source post, so the queue resolves and advances by track id (feed posts,
 * which carry a `sourcePostId`, use the CymbalPost overloads instead). Mirrors
 * the single-track play args in [CatalogTrackRow] so behaviour is identical
 * whether or not a queue is supplied.
 */
internal fun CymbalTrack.toQueuedTrack(origin: CatalogPlaybackOrigin? = null) = QueuedTrack(
    trackId = id,
    trackName = name,
    artistName = artistName,
    albumArtURL = albumArtURL,
    albumArtLargeURL = albumArtLargeURL,
    previewUrl = previewUrl,
    spotifyURI = spotifyURI.ifBlank { null },
    spotifyWebURL = spotifyWebURL.ifBlank { null },
    isrc = isrc,
    sourcePostId = null,
    source = source,
    soundcloudId = soundcloudId,
    soundcloudPermalinkUrl = soundcloudPermalinkUrl,
    audiomackUrl = audiomackUrl,
    catalogOrigin = origin,
)

/**
 * Muted data-credit footer. [spotifyRow] renders the "Open in Spotify" line
 * (Spotify Developer Terms: mark + link back to the content — a credit, not a
 * playback CTA); pass null to omit it (director pages, `am:` albums).
 */
@Composable
internal fun DestinationAttributionFooter(
    attribution: String,
    onOpenSpotify: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .padding(top = CorusSpacing.xl, bottom = CorusSpacing.md),
    ) {
        Text(
            text = attribution,
            style = CorusFont.caption,
            color = CorusColors.Tertiary,
        )
        if (onOpenSpotify != null) {
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
            Row(
                modifier = Modifier.clickable(onClick = onOpenSpotify),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.spotify_logo),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(CorusSpacing.xs))
                Text(
                    text = stringResource(R.string.destination_open_in_spotify),
                    style = CorusFont.caption,
                    color = CorusColors.Tertiary,
                )
            }
        }
    }
}
