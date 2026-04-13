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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Shared "Liked by **username** and **N others**" row used in both
 * PostCard and PostDetailScreen so styles are defined in exactly one place.
 */
@Composable
fun LikedBySection(
    likers: List<CymbalUser>,
    likeCount: Int,
    onLikesTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (likeCount <= 0 || likers.isEmpty()) return

    val avatarSize = 20.dp
    val avatarOverlap = 8.dp
    val visibleLikers = likers.take(3)
    val avatarStackWidth = avatarSize + (avatarOverlap * maxOf(visibleLikers.size - 1, 0))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onLikesTap)
            .padding(horizontal = CorusSpacing.lg)
            .padding(bottom = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Overlapping avatar stack
        Box(modifier = Modifier.height(avatarSize)) {
            visibleLikers.forEachIndexed { index, liker ->
                UserAvatarView(
                    avatarURL = liker.avatarURL,
                    avatarThumbURL = liker.avatarThumbURL,
                    size = avatarSize,
                    modifier = Modifier
                        .offset(x = avatarOverlap * index)
                        .zIndex((3 - index).toFloat()),
                )
            }
        }

        Spacer(modifier = Modifier.width(avatarStackWidth + CorusSpacing.xs))

        val likedByText = buildAnnotatedString {
            append("Liked by ")
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                append(likers.first().username)
            }
            if (likeCount > 1) {
                append(" and ")
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append("${likeCount - 1} other${if (likeCount - 1 > 1) "s" else ""}")
                }
            }
        }
        Text(
            text = likedByText,
            style = CorusFont.body,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
