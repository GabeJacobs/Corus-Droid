package fm.corus.android.ui.screens.messaging

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars

/** Maps a stored reaction key (an emoji char, or a legacy named key) to its emoji. */
internal fun reactionEmojiChar(key: String): String =
    REACTION_EMOJIS.getOrNull(REACTION_KEYS.indexOf(key)) ?: key

/** Distinct emoji chars present in [reactions], ordered by the picker (extras last). */
internal fun orderedReactionEmojiChars(reactions: Map<String, List<String>>): List<String> =
    reactions.filterValues { it.isNotEmpty() }
        .keys
        .map { reactionEmojiChar(it) }
        .distinct()
        .sortedBy { REACTION_EMOJIS.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }

private data class ReactionRowData(
    val emojiKey: String,
    val emojiChar: String,
    val user: CymbalUser?,
    val userId: String,
    val isMine: Boolean,
)

/** What tapping a reactions row does. */
internal enum class ReactionRowTap { Remove, OpenProfile, Inert }

/**
 * Your own row removes your reaction ("Tap to remove"); anyone else's opens
 * their profile, but only once their profile has resolved (otherwise inert).
 */
internal fun reactionRowTap(isMine: Boolean, hasResolvedUser: Boolean): ReactionRowTap = when {
    isMine -> ReactionRowTap.Remove
    hasResolvedUser -> ReactionRowTap.OpenProfile
    else -> ReactionRowTap.Inert
}

/**
 * Instagram-style "Reactions" list for a group message: everyone who reacted,
 * each with the emoji they used. The viewer's own row is tappable to remove
 * their reaction ("Tap to remove"); tapping anyone else's row opens their
 * profile. Opened by tapping a group message's collapsed reaction badge; 1:1
 * reactions toggle inline and never open this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageReactionsBottomSheet(
    message: CymbalMessage,
    membersById: Map<String, CymbalUser>,
    currentUserId: String,
    onRemove: (emojiKey: String) -> Unit,
    onUserClick: (userId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CorusColors.Background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        CorusSystemBars()
        BackHandler { onDismiss() }

        // Flatten { key: [uid] } into one ordered list, viewer pinned to the top.
        val rows = buildList {
            val orderedKeys = message.reactions
                .filterValues { it.isNotEmpty() }
                .keys
                .sortedBy { key ->
                    val ki = REACTION_KEYS.indexOf(key)
                    if (ki >= 0) ki
                    else REACTION_EMOJIS.indexOf(key).let { ei -> if (ei < 0) Int.MAX_VALUE else ei }
                }
            for (key in orderedKeys) {
                for (uid in message.reactions[key].orEmpty()) {
                    add(
                        ReactionRowData(
                            emojiKey = key,
                            emojiChar = reactionEmojiChar(key),
                            user = membersById[uid],
                            userId = uid,
                            isMine = uid == currentUserId,
                        )
                    )
                }
            }
        }.sortedByDescending { it.isMine }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 360.dp),
        ) {
            Text(
                text = "Reactions",
                style = CorusFont.screenTitle,
                color = CorusColors.Text,
                modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            )
            HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(rows, key = { "${it.userId}_${it.emojiKey}" }) { row ->
                    ReactionRow(
                        row = row,
                        onRemove = { onRemove(row.emojiKey) },
                        onOpenProfile = { onUserClick(row.userId) },
                    )
                }
                item(key = "expand_spacer") {
                    Spacer(modifier = Modifier.defaultMinSize(minHeight = 1.dp))
                }
            }
        }
    }
}

@Composable
private fun ReactionRow(row: ReactionRowData, onRemove: () -> Unit, onOpenProfile: () -> Unit) {
    val u = row.user
    val onClick: (() -> Unit)? = when (reactionRowTap(row.isMine, hasResolvedUser = u != null)) {
        ReactionRowTap.Remove -> onRemove
        ReactionRowTap.OpenProfile -> onOpenProfile
        ReactionRowTap.Inert -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(
            avatarURL = u?.avatarURL,
            displayName = u?.displayName ?: "",
            size = CorusSpacing.avatarMedium + 8.dp,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (u != null) {
                UsernameWithFlair(
                    username = u.username,
                    isVerified = u.isVerified,
                    isClubMember = u.isClubMember,
                    flairStyle = u.flairStyle,
                    isBot = u.isBot,
                )
            } else {
                Text(text = "Someone", style = CorusFont.bodyMedium, color = CorusColors.Text)
            }
            if (row.isMine) {
                Text(
                    text = "Tap to remove",
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Text(text = row.emojiChar, fontSize = 22.sp)
    }
}
