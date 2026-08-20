package fm.corus.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fm.corus.android.R
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.reconcileRecentLikers
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Shared "Liked by **username** and **N others**" row used on the feed,
 * post page, and full-player card so styles are defined in exactly one place.
 */
@Composable
fun LikedBySection(
    likers: List<CymbalUser>,
    likeCount: Int,
    onLikesTap: () -> Unit = {},
    onLikerTap: (CymbalUser) -> Unit = {},
    modifier: Modifier = Modifier,
    currentUser: CymbalUser? = null,
    isLiked: Boolean = false,
    /** Skip feed-style outer padding so the row can sit inside an already-padded card. */
    embedded: Boolean = false,
    enabled: Boolean = true,
    textColor: Color = CorusColors.Text,
) {
    if (likeCount <= 0) return

    // Build a likers list that reflects the current user's known like state
    // so the heart's filled state and the "Liked by" subtitle never disagree.
    val effectiveLikers = remember(likers, currentUser, isLiked) {
        reconcileRecentLikers(likers, isLikedByCurrentUser = isLiked, currentUser = currentUser)
    }

    if (effectiveLikers.isEmpty()) return

    val avatarSize = 20.dp
    val avatarOverlap = 8.dp
    val visibleLikers = effectiveLikers.take(3)
    val avatarStackWidth = avatarSize + (avatarOverlap * maxOf(visibleLikers.size - 1, 0))

    val isSingleLiker = likeCount == 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = {
                    if (isSingleLiker) onLikerTap(effectiveLikers.first()) else onLikesTap()
                },
            )
            .then(
                if (embedded) Modifier
                else Modifier
                    .padding(horizontal = CorusSpacing.lg)
                    .padding(bottom = CorusSpacing.xs),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Overlapping avatar stack
        Box(
            modifier = Modifier
                .height(avatarSize)
                .width(avatarStackWidth),
        ) {
            visibleLikers.forEachIndexed { index, liker ->
                UserAvatarView(
                    avatarURL = liker.avatarURL,
                    avatarThumbURL = liker.avatarThumbURL,
                    displayName = liker.displayName,
                    size = avatarSize,
                    modifier = Modifier
                        .offset(x = avatarOverlap * index)
                        .zIndex((3 - index).toFloat()),
                )
            }
        }

        Spacer(modifier = Modifier.width(CorusSpacing.xs))

        val prefix = stringResource(R.string.post_card_liked_by_prefix)
        val andConnector = stringResource(R.string.post_card_liked_by_and)
        val othersText = if (likeCount > 1) {
            pluralStringResource(R.plurals.post_card_others_count, likeCount - 1, likeCount - 1)
        } else null
        val likedByText = buildAnnotatedString {
            append(prefix)
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                append(effectiveLikers.first().username)
            }
            if (othersText != null) {
                append(andConnector)
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(othersText)
                }
            }
        }
        Text(
            text = likedByText,
            style = CorusFont.body,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
