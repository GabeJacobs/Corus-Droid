package fm.corus.android.ui.screens.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.screens.feed.CorusUserListRow
import fm.corus.android.ui.components.dismissKeyboardOnDownwardDrag
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiveRoomInfoSheet(
    initialTitle: String,
    initialDescription: String,
    fallbackTitle: String,
    editable: Boolean,
    onDismiss: () -> Unit,
    hostContent: (@Composable () -> Unit)? = null,
    onSave: suspend (String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.isImeVisible
    val normalizedTitle = title.trim().replace(Regex("[\\s\\p{Z}]+"), " ")
    val normalizedDescription = description.trim()
    val changed = normalizedTitle != initialTitle || normalizedDescription != initialDescription
    val close = {
        if (!saving) {
            focus.clearFocus()
            keyboard?.hide()
            if (!editable || !keyboardVisible) {
                if (editable && changed) discard = true else onDismiss()
            }
        }
    }
    LiveSheet(
        stringResource(R.string.live_room_info), close, large = true,
        startsHalfExpanded = !editable,
        // The outer keyboard gesture intercepts the scrim above a partial sheet.
        // Only the full-height editor needs that keyboard-first behavior.
        dismissKeyboardFromHeader = editable, dismissEnabled = !saving,
        // A listener has no draft to protect; an underlying chat IME must not
        // block Material's outside-tap/back/swipe dismissal of room details.
        allowSwipeDismiss = !editable || (!keyboardVisible && !changed),
    ) {
        Column(Modifier.fillMaxSize().imePadding().dismissKeyboardOnDownwardDrag()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (editable) {
                    val nameLabel = stringResource(R.string.live_room_info_name)
                    val descriptionLabel = stringResource(R.string.live_room_info_description)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveText(nameLabel, CorusFont.usernameLarge)
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = limitLiveCharacters(it, LIVE_ROOM_TITLE_LIMIT); failed = false },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = nameLabel },
                            placeholder = { LiveText(stringResource(R.string.live_room_info_name_placeholder), color = CorusColors.Secondary) },
                            supportingText = { LiveText("${liveCharacterCount(title)} / $LIVE_ROOM_TITLE_LIMIT", CorusFont.caption, CorusColors.Secondary) },
                            shape = RoundedCornerShape(14.dp),
                            textStyle = CorusFont.body, singleLine = true, enabled = !saving,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveText(descriptionLabel, CorusFont.usernameLarge)
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = limitLiveCharacters(it, 500); failed = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).semantics { contentDescription = descriptionLabel },
                            placeholder = { LiveText(stringResource(R.string.live_room_info_description_placeholder), color = CorusColors.Secondary) },
                            supportingText = { LiveText("${liveCharacterCount(description)} / 500", CorusFont.caption, CorusColors.Secondary) },
                            shape = RoundedCornerShape(14.dp),
                            textStyle = CorusFont.body, minLines = 5, enabled = !saving,
                        )
                    }
                } else {
                    hostContent?.invoke()
                    if (initialTitle.isNotBlank()) {
                        LiveText(stringResource(R.string.live_room_info_name), CorusFont.caption, CorusColors.Secondary)
                        SelectionContainer { LiveText(initialTitle, CorusFont.usernameLarge) }
                        HorizontalDivider(color = CorusColors.Divider)
                    }
                    LiveText(stringResource(R.string.live_room_info_description), CorusFont.caption, CorusColors.Secondary)
                    SelectionContainer {
                        LiveText(initialDescription.ifBlank { stringResource(R.string.live_room_info_empty) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (editable) {
                if (failed) LiveText(stringResource(R.string.edit_profile_save_error), CorusFont.caption, CorusColors.Secondary)
                Spacer(Modifier.height(12.dp))
                val actionLabel = when {
                    saving -> R.string.live_room_info_saving
                    changed -> R.string.live_room_info_save
                    else -> R.string.corus_live_done
                }
                LivePrimary(stringResource(actionLabel), !saving) {
                    focus.clearFocus()
                    keyboard?.hide()
                    if (!changed) {
                        onDismiss()
                        return@LivePrimary
                    }
                    saving = true
                    failed = false
                    scope.launch {
                        try {
                            onSave(normalizedTitle, normalizedDescription)
                            onDismiss()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            failed = true
                        } finally { saving = false }
                    }
                }
            }
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        title = { LiveText(stringResource(R.string.live_room_info_discard_question), CorusFont.username) },
        confirmButton = { TextButton(onClick = { discard = false; onDismiss() }) { LiveText(stringResource(R.string.live_room_info_discard)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { LiveText(stringResource(R.string.live_room_info_keep_editing)) } },
    )
}

private fun liveCharacterCount(value: String): Int {
    val iterator = android.icu.text.BreakIterator.getCharacterInstance()
    iterator.setText(value)
    var count = 0
    while (iterator.next() != android.icu.text.BreakIterator.DONE) count++
    return count
}

@Composable
internal fun LiveRoomInfoHost(host: LivePerson, profile: CymbalUser?, onProfile: () -> Unit) {
    Column {
        LiveText(stringResource(R.string.live_host), CorusFont.caption, CorusColors.Secondary)
        CorusUserListRow(
            user = profile ?: CymbalUser(host.uid, host.username.removePrefix("@"), "", avatarURL = host.avatar),
            isFollowing = false,
            isFollower = false,
            onUserTap = onProfile,
            onFollowTap = null,
            contentPadding = PaddingValues(vertical = 8.dp),
        )
        HorizontalDivider(color = CorusColors.Divider)
    }
}
