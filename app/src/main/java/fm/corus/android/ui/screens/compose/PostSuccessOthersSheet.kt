package fm.corus.android.ui.screens.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.PostSuccessOthersPayload
import fm.corus.android.domain.PostSuccessOthersPerson
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun PostSuccessOthersSheet(
    payload: PostSuccessOthersPayload,
    onDone: () -> Unit,
    onOpenProfile: (CymbalUser) -> Unit,
    onFollow: (CymbalUser) -> Unit,
    onLike: (postId: String, nowLiked: Boolean) -> Unit = { _, _ -> },
    onSeeAll: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        ConfirmHeader(
            title = payload.media.title,
            subtitle = payload.media.subtitle,
            artURL = payload.media.artURL,
            isPoster = payload.media.isPoster,
            modifier = Modifier
                .padding(horizontal = CorusSpacing.xl)
                .padding(top = CorusSpacing.lg, bottom = CorusSpacing.lg),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xl)
                .padding(bottom = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.post_success_others_title,
                    payload.otherCount,
                    payload.otherCount,
                ),
                style = CorusFont.sectionHeader,
                color = CorusColors.Secondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.destination_see_all),
                style = CorusFont.captionMedium,
                color = CorusColors.Accent,
                modifier = Modifier.clickable(onClick = onSeeAll),
            )
        }
        payload.people.forEach { person ->
            PostSuccessOthersRow(
                person = person,
                onOpenProfile = { onOpenProfile(person.user) },
                onFollow = { onFollow(person.user) },
                onLike = { nowLiked -> onLike(person.postId, nowLiked) },
            )
        }
        TextButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.post_success_others_done),
                style = CorusFont.button.copy(fontSize = 15.sp),
                color = CorusColors.Secondary,
            )
        }
    }
}

@Composable
private fun ConfirmHeader(
    title: String,
    subtitle: String,
    artURL: String?,
    isPoster: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        AsyncImage(
            model = artURL,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = if (isPoster) {
                Modifier
                    .width(40.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            } else {
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CorusFont.songTitle,
                color = CorusColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = CorusFont.artistName,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PostSuccessOthersRow(
    person: PostSuccessOthersPerson,
    onOpenProfile: () -> Unit,
    onFollow: () -> Unit,
    onLike: (nowLiked: Boolean) -> Unit,
) {
    var following by remember(person.user.id) { mutableStateOf(false) }
    var liked by remember(person.postId) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            UserAvatarView(
                avatarURL = person.user.avatarURL,
                avatarThumbURL = person.user.avatarThumbURL,
                displayName = person.user.displayName,
                username = person.user.username,
                size = CorusSpacing.avatarMedium,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@${person.user.username}",
                    style = CorusFont.username,
                    color = CorusColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (person.caption.isNotEmpty()) {
                    Text(
                        text = person.caption,
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (person.postId.isNotEmpty()) {
            Icon(
                imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(R.string.post_card_cd_like),
                tint = if (liked) CorusColors.Like else CorusColors.Secondary,
                modifier = Modifier
                    .size(36.dp)
                    .clickable {
                        liked = !liked
                        onLike(liked)
                    }
                    .padding(7.dp),
            )
        }
        StayVisibleFollowPill(
            following = following,
            onTap = {
                if (!following) {
                    following = true
                    onFollow()
                }
            },
        )
    }
}

/** Same plus→check accent capsule as the feed Follow pill, but it stays
 *  on "Following" so the user can follow more than one person before Done. */
@Composable
private fun StayVisibleFollowPill(
    following: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(CorusColors.Accent)
            .clickable(
                enabled = !following,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = if (following) Icons.Filled.Check else Icons.Filled.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(
                if (following) R.string.likes_button_following else R.string.likes_button_follow,
            ),
            style = CorusFont.caption.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}
