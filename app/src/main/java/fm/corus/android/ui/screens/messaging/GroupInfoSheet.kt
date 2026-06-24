package fm.corus.android.ui.screens.messaging

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Group details bottom sheet (mirrors iOS GroupInfoView): photo + name (any
 * member can edit), member list with a Creator badge, add people, remove member
 * (creator only), and leave group. Driven by [MessageThreadViewModel]; membership
 * changes flow back through its live group-info listener.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupInfoSheet(
    viewModel: MessageThreadViewModel,
    groupInfo: fm.corus.android.data.repository.MessageRepository.GroupThreadInfo,
    membersById: Map<String, CymbalUser>,
    onDismiss: () -> Unit,
    onLeft: () -> Unit,
) {
    val context = LocalContext.current
    val currentUserId = viewModel.currentUserId
    val isCreator = groupInfo.createdBy == currentUserId
    val memberIds = groupInfo.memberIds
    val otherMembers = memberIds.filter { it != currentUserId }.mapNotNull { membersById[it] }

    var name by remember(groupInfo.name) { mutableStateOf(groupInfo.name ?: "") }
    var showAddPeople by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<CymbalUser?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { viewModel.uploadAndSetGroupPhoto(it) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CorusColors.Background,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        CorusSystemBars()
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Text(
                stringResource(id = R.string.messaging_group_info_title),
                style = CorusFont.screenTitle,
                color = CorusColors.Text,
                modifier = Modifier.fillMaxWidth().padding(CorusSpacing.lg),
                textAlign = TextAlign.Center,
            )

            // Photo + name + member count
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (groupInfo.photoURL != null) {
                    UserAvatarView(avatarURL = groupInfo.photoURL, displayName = "", username = "", size = 84.dp)
                } else {
                    StackedGroupAvatar(members = otherMembers, size = 84.dp)
                }
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                Text(
                    stringResource(id = R.string.messaging_group_change_photo),
                    style = CorusFont.body,
                    color = CorusColors.Accent,
                    modifier = Modifier.clickable {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                )
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CorusColors.CardBackground)
                        .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                ) {
                    if (name.isEmpty()) {
                        Text(stringResource(id = R.string.messaging_group_name_label), style = CorusFont.body, color = CorusColors.Tertiary)
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        textStyle = CorusFont.body.copy(color = CorusColors.Text, textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.renameGroup(name) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(CorusSpacing.xs))
                Text(
                    context.getString(R.string.messaging_group_member_count, memberIds.size),
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                )
            }

            Spacer(modifier = Modifier.height(CorusSpacing.md))
            HorizontalDivider(color = CorusColors.Divider)

            // Add people — gated on the RC flag like the inbox create entry
            // (mirrors web group-info), so an existing group still opens when the
            // flag is off but can't be expanded.
            if (viewModel.groupMessagingEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAddPeople = true }
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(CorusColors.CardBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = CorusColors.Accent, modifier = Modifier.size(20.dp))
                    }
                    Text(stringResource(id = R.string.messaging_group_add_people), style = CorusFont.body, color = CorusColors.Text)
                }
            }

            // Member list
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(memberIds, key = { it }) { id ->
                    val u = membersById[id]
                    if (u != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatarView(avatarURL = u.avatarURL, avatarThumbURL = u.avatarThumbURL, displayName = u.displayName, username = u.username, size = CorusSpacing.avatarMedium)
                            Spacer(modifier = Modifier.width(CorusSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("@${u.username}", style = CorusFont.username, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (u.displayName.isNotBlank()) {
                                    Text(u.displayName, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (groupInfo.createdBy == id) {
                                Text(stringResource(id = R.string.messaging_group_creator), style = CorusFont.caption, color = CorusColors.Tertiary)
                            } else if (isCreator) {
                                IconButton(onClick = { confirmRemove = u }) {
                                    Icon(Icons.Filled.PersonRemove, contentDescription = stringResource(id = R.string.messaging_group_remove), tint = CorusColors.Tertiary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = CorusColors.Divider)
            Text(
                text = stringResource(id = R.string.messaging_group_leave),
                style = CorusFont.button,
                color = Color.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { confirmLeave = true }
                    .padding(vertical = CorusSpacing.md),
            )
        }
    }

    if (showAddPeople) {
        val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddPeople = false },
            sheetState = addSheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            CorusSystemBars()
            MultiUserPickerContent(
                title = stringResource(id = R.string.messaging_group_add_people),
                showNameField = false,
                excludeIds = (memberIds + (currentUserId ?: "")).toSet(),
                loadSuggestions = { viewModel.fetchSuggestionsList() },
                search = { viewModel.searchUsersList(it) },
                onCancel = { showAddPeople = false },
                onConfirm = { selectedUsers, _ ->
                    viewModel.addGroupMembers(selectedUsers.map { it.id })
                    showAddPeople = false
                },
            )
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(id = R.string.messaging_group_leave_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.leaveGroup { onLeft() }
                }) { Text(stringResource(id = R.string.messaging_group_leave), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(stringResource(id = R.string.messaging_group_cancel))
                }
            },
            containerColor = CorusColors.Background,
        )
    }

    confirmRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(id = R.string.messaging_group_remove)) },
            text = { Text("@${target.username}") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    viewModel.removeGroupMember(target.id)
                }) { Text(stringResource(id = R.string.messaging_group_remove), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) {
                    Text(stringResource(id = R.string.messaging_group_cancel))
                }
            },
            containerColor = CorusColors.Background,
        )
    }
}
