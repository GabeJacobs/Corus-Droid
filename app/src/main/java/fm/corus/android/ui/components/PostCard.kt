package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.request.ImageRequest
import coil3.size.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.NunitoFamily
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.filled.PlayArrow

@Composable
fun PostCard(
    post: CymbalPost,
    likeCount: Int = post.likeCount,
    commentCount: Int = post.commentCount,
    isLiked: Boolean = post.isLiked,
    isSaved: Boolean = false,
    onLikeTap: () -> Unit = {},
    onSaveTap: () -> Unit = {},
    onUserTap: () -> Unit = {},
    onPostTap: () -> Unit = {},
    isPreviewLoading: Boolean = false,
    isPreviewPlaying: Boolean = false,
    onPreviewTap: () -> Unit = {},
    onTrailerTap: () -> Unit = {},
    onCommentTap: () -> Unit = {},
    onShareTap: () -> Unit = {},
    onMenuTap: () -> Unit = {},
    onSpotifyTap: () -> Unit = {},
    onLikesTap: () -> Unit = {},
    onMentionTap: (String) -> Unit = {},
    onHashtagTap: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var showFilmOverlay by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 1. POST HEADER: avatar + username with badges + repost indicator + menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatarView(
                avatarURL = post.user.avatarURL,
                avatarThumbURL = post.user.avatarThumbURL,
                size = 28.dp,
                modifier = Modifier.clickable(onClick = onUserTap),
            )

            Spacer(modifier = Modifier.width(CorusSpacing.sm))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onUserTap),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                UsernameWithFlair(
                    username = post.user.username,
                    isBot = post.user.isBot,
                    isVerified = post.user.isVerified,
                    isClubMember = post.user.isClubMember,
                    flairStyle = post.user.flairStyle,
                    isFirstPoster = post.isFirstPoster,
                    style = CorusFont.username,
                    color = CorusColors.Text,
                )

                // Repost indicator
                if (!post.repostedFromUsername.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = CorusColors.Secondary,
                        )
                        Text(
                            text = "reposted from",
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                        )
                        Text(
                            text = "@${post.repostedFromUsername}",
                            style = CorusFont.captionMedium,
                            color = CorusColors.Secondary,
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "More options",
                modifier = Modifier
                    .size(14.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMenuTap,
                    ),
                tint = CorusColors.Secondary,
            )
        }

        // 2. ALBUM ART / MOVIE POSTER: full-bleed, no corner radius, double-tap to like
        val aspectRatio = if (post.isMovie) 2f / 3f else 1f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .pointerInput(post.id, post.isTrack, post.isMovie) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!isLiked) onLikeTap()
                            showDoubleTapHeart = true
                            scope.launch {
                                heartScale.snapTo(0f)
                                heartAlpha.snapTo(1f)
                                heartScale.animateTo(1f, animationSpec = tween(300))
                                kotlinx.coroutines.delay(400)
                                heartAlpha.animateTo(0f, animationSpec = tween(300))
                                showDoubleTapHeart = false
                            }
                        },
                        onTap = {
                            when {
                                post.isTrack -> onPreviewTap()
                                post.isMovie -> showFilmOverlay = !showFilmOverlay
                                else -> onPostTap()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(post.displayImageLargeURL ?: post.displayImageURL)
                    .size(Size.ORIGINAL)
                    .build(),
                contentDescription = post.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // Preview loading/playing overlay (track posts only)
            if (post.isTrack && (isPreviewLoading || isPreviewPlaying)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPreviewLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                }
            }

            // Film overlay with action buttons
            androidx.compose.animation.AnimatedVisibility(
                visible = post.isMovie && showFilmOverlay,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showFilmOverlay = false },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        modifier = Modifier.width(IntrinsicSize.Max),
                    ) {
                        // View Film Page button
                        Button(
                            onClick = { onPostTap() },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                            ),
                            contentPadding = PaddingValues(
                                horizontal = CorusSpacing.xl,
                                vertical = CorusSpacing.md,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Movie,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = "View Film Page",
                                    style = CorusFont.button,
                                )
                            }
                        }

                        // Watch Trailer button (only if trailer exists)
                        if (post.trailerURL != null) {
                            Button(
                                onClick = { onTrailerTap() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White,
                                ),
                                border = BorderStroke(1.75.dp, Color.White),
                                contentPadding = PaddingValues(
                                    horizontal = CorusSpacing.xl,
                                    vertical = CorusSpacing.md,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Text(
                                        text = "Watch Trailer",
                                        style = CorusFont.button,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Heart animation overlay
            if (showDoubleTapHeart) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = CorusColors.Like,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(heartScale.value)
                        .alpha(heartAlpha.value),
                )
            }
        }

        // 3. SONG INFO ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg)
                .padding(top = CorusSpacing.md, bottom = CorusSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = post.displayTitle,
                    style = CorusFont.songTitle, // ExtraBold 16sp
                    color = CorusColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = post.displaySubtitle,
                    style = CorusFont.artistName, // Medium 14sp
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(CorusSpacing.sm))

            if (!post.isMovie) {
                Image(
                    painter = painterResource(R.drawable.spotify_logo),
                    contentDescription = "Play on Spotify",
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSpotifyTap,
                        ),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // 4. ENGAGEMENT ROW — naturally sized buttons matching iOS HStack(spacing: .lg)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg)
                .padding(vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
        ) {
            // Like button
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLikeTap,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    modifier = Modifier.size(22.dp),
                    tint = if (isLiked) CorusColors.Like else CorusColors.Text,
                )
                if (likeCount > 0) {
                    Text(
                        text = likeCount.toString(),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                    )
                }
            }

            // Comment button
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCommentTap,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Comment",
                    modifier = Modifier.size(20.dp),
                    tint = CorusColors.Text,
                )
                if (commentCount > 0) {
                    Text(
                        text = commentCount.toString(),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                    )
                }
            }

            // Share button
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = "Share",
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onShareTap,
                    ),
                tint = CorusColors.Text,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Save button
            Icon(
                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Save",
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSaveTap,
                    ),
                tint = CorusColors.Text,
            )
        }

        // 5. LIKED BY
        LikedBySection(
            likers = post.likers,
            likeCount = likeCount,
            onLikesTap = onLikesTap,
        )

        // 6. CAPTION or VOICE NOTE
        if (!post.voiceNoteURL.isNullOrBlank()) {
            VoiceNotePlayerView(
                voiceNoteURL = post.voiceNoteURL!!,
                username = post.user.username,
                onUsernameTap = onUserTap,
                modifier = Modifier
                    .padding(horizontal = CorusSpacing.lg)
                    .padding(bottom = CorusSpacing.xs),
            )
        } else if (!post.caption.isNullOrBlank()) {
            val captionAnnotated = buildCaptionAnnotatedString(
                username = post.user.username,
                caption = post.caption,
            )

            var isExpanded by remember { mutableStateOf(false) }

            TappableMentionText(
                text = captionAnnotated,
                style = CorusFont.body,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                onMentionTap = { onMentionTap(it) },
                onHashtagTap = { onHashtagTap(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg)
                    .padding(bottom = CorusSpacing.xs),
            )
        }

        // 7. COMMENT PREVIEW (max 2)
        if (post.comments.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg)
                    .padding(bottom = CorusSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                post.comments.take(2).forEach { comment ->
                    val commentText = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                            )
                        ) {
                            append(comment.user.username)
                        }
                        append(" ")
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp,
                            )
                        ) {
                            append(comment.text)
                        }
                    }
                    Text(
                        text = commentText,
                        style = CorusFont.body,
                        color = CorusColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // 8. TIMESTAMP
        Text(
            text = relativeTimeString(post.timestamp),
            style = CorusFont.caption, // Normal 12sp
            color = CorusColors.Secondary,
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg)
                .padding(bottom = CorusSpacing.sm),
        )
    }
}


/**
 * Produces a human-readable relative time string (e.g. "2h", "3d", "1w").
 */
private fun relativeTimeString(date: java.util.Date): String {
    val now = System.currentTimeMillis()
    val diff = now - date.time
    if (diff < 0) return "now"

    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        seconds < 60 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        days < 30 -> "${days / 7}w"
        days < 365 -> "${days / 30}mo"
        else -> "${days / 365}y"
    }
}
