package fm.corus.android.ui.screens.messaging

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Identity card at the start of a 1:1 thread — who you're talking to, optional
 * taste overlap, and a quiet path to their profile. Sits at the top of an
 * empty conversation and above the first bubble once messages exist.
 */
@Composable
fun ThreadOpener(
    username: String,
    displayName: String,
    avatarURL: String?,
    avatarThumbURL: String?,
    artistsInCommon: Int?,
    onViewProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = displayName.ifBlank { username }
    val pillShape = RoundedCornerShape(50)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onViewProfile)
            .padding(
                horizontal = CorusSpacing.lg,
                vertical = CorusSpacing.xl,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        UserAvatarView(
            avatarURL = avatarURL,
            avatarThumbURL = avatarThumbURL,
            displayName = title,
            username = username,
            size = CorusSpacing.avatarLarge,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = CorusFont.displayName,
                    color = CorusColors.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (username.isNotBlank()) {
                Text(
                    text = "@$username",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (artistsInCommon != null && artistsInCommon > 0) {
                Text(
                    text = stringResource(
                        id = R.string.notif_taste_match_body_artists,
                        artistsInCommon,
                    ),
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(
            modifier = Modifier
                .height(CorusSpacing.profileActionHeight)
                .border(1.dp, CorusColors.Divider, pillShape)
                .padding(horizontal = CorusSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(id = R.string.messaging_thread_view_profile),
                style = CorusFont.button,
                color = CorusColors.Secondary,
            )
        }
    }
}
