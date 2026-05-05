package fm.corus.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SpeakerNotesOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.data.model.CommentsAudience

/**
 * Subtle inline picker for the compose screen. Shown beneath the caption,
 * styled as muted secondary text + chevron — matches the web/iOS treatment
 * so the picker doesn't compete with the primary CTA. Tap opens a small
 * dropdown anchored to the chip.
 *
 * Default selection is `EVERYONE` so first-time use reads as "no
 * restriction" without nagging the author.
 */
@Composable
fun CommentsAudiencePicker(
    selection: CommentsAudience,
    onSelect: (CommentsAudience) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = selection.icon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(selection.summaryStringRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // List in restriction order — most-open first — so the dropdown
            // matches the social hierarchy users expect.
            listOf(
                CommentsAudience.EVERYONE,
                CommentsAudience.FOLLOWERS,
                CommentsAudience.FOLLOWING,
                CommentsAudience.OFF,
            ).forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.summaryStringRes())) },
                    leadingIcon = {
                        Icon(
                            imageVector = option.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (option == selection) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            // Reserve trailing slot so menu rows don't reflow when
                            // the user changes selection.
                            Box(modifier = Modifier.width(16.dp))
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Replaces the comment composer when the viewer can't write. Comment list
 * above stays visible — only the input area swaps to this notice.
 */
@Composable
fun CommentsLockedNotice(
    reason: CommentsBlockReason,
    authorUsername: String?,
    modifier: Modifier = Modifier,
) {
    val message = when (reason) {
        CommentsBlockReason.OFF -> stringResource(R.string.comments_locked_off)
        CommentsBlockReason.FOLLOWERS -> if (authorUsername != null) {
            stringResource(R.string.comments_locked_followers_known, authorUsername)
        } else {
            stringResource(R.string.comments_locked_followers_unknown)
        }
        CommentsBlockReason.FOLLOWING -> if (authorUsername != null) {
            stringResource(R.string.comments_locked_authorfollow_known, authorUsername)
        } else {
            stringResource(R.string.comments_locked_authorfollow_unknown)
        }
    }
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = reason.icon(),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

enum class CommentsBlockReason { OFF, FOLLOWERS, FOLLOWING }

private fun CommentsAudience.icon(): ImageVector = when (this) {
    CommentsAudience.EVERYONE -> Icons.Outlined.Public
    CommentsAudience.FOLLOWERS -> Icons.Outlined.Person
    CommentsAudience.FOLLOWING -> Icons.Outlined.Group
    CommentsAudience.OFF -> Icons.Outlined.SpeakerNotesOff
}

private fun CommentsBlockReason.icon(): ImageVector = when (this) {
    CommentsBlockReason.OFF -> Icons.Outlined.SpeakerNotesOff
    CommentsBlockReason.FOLLOWERS -> Icons.Outlined.Person
    CommentsBlockReason.FOLLOWING -> Icons.Outlined.Group
}

private fun CommentsAudience.summaryStringRes(): Int = when (this) {
    CommentsAudience.EVERYONE -> R.string.comments_audience_everyone
    CommentsAudience.FOLLOWERS -> R.string.comments_audience_followers
    CommentsAudience.FOLLOWING -> R.string.comments_audience_following
    CommentsAudience.OFF -> R.string.comments_audience_off
}
