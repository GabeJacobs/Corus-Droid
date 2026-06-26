package fm.corus.android.ui.screens.messaging

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import fm.corus.android.ui.components.AvatarCropView
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.uriToBitmap
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onNavigateToProfile: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val decodeScope = rememberCoroutineScope()
    // Picked photo awaiting crop. The crop overlay is hosted INSIDE this sheet's own
    // window (below): a Compose Dialog or a screen-root overlay would render behind
    // the bottom sheet, and a Dialog also zeroes out the nav-bar insets the crop
    // needs (see CorusDraggableSheet).
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val currentUserId = viewModel.currentUserId
    val isCreator = groupInfo.createdBy == currentUserId
    val memberIds = groupInfo.memberIds
    val otherMembers = memberIds.filter { it != currentUserId }.mapNotNull { membersById[it] }

    var name by remember(groupInfo.name) { mutableStateOf(groupInfo.name ?: "") }
    // Rename is a dialog (mirrors iOS .alert), not inline editing.
    var showRename by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var showAddPeople by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<CymbalUser?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val uploadingPhoto by viewModel.isUploadingGroupPhoto.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Decode + EXIF rotation runs on IO; the bitmap is sent up to the crop overlay
    // (the actual upload happens once the crop is confirmed).
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            decodeScope.launch {
                val bmp = withContext(Dispatchers.IO) { uriToBitmap(context, uri) }
                if (bmp != null) cropBitmap = bmp
            }
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
        // Back returns from the add-people panel to the group info (not the whole sheet).
        BackHandler(enabled = showAddPeople) { showAddPeople = false }
        // Add-people is an IN-SHEET sliding panel (mirrors the create-group flow), not a
        // second ModalBottomSheet stacked on top: stacking two modal sheet windows made
        // the open animation stutter (pause at half height). Both panels share one fixed
        // height so the sheet never resizes between them.
        AnimatedContent(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            targetState = showAddPeople,
            transitionSpec = {
                if (targetState) {
                    slideInHorizontally(tween(280), initialOffsetX = { it }) togetherWith
                        slideOutHorizontally(tween(280), targetOffsetX = { -it / 3 })
                } else {
                    slideInHorizontally(tween(280), initialOffsetX = { -it / 3 }) togetherWith
                        slideOutHorizontally(tween(280), targetOffsetX = { it })
                }
            },
            label = "groupInfoAddPeopleSlide",
        ) { addingPeople ->
            if (addingPeople) {
                MultiUserPickerContent(
                    title = stringResource(id = R.string.messaging_group_add_people),
                    showNameField = false,
                    excludeIds = (memberIds + (currentUserId ?: "")).toSet(),
                    loadSuggestions = { viewModel.fetchSuggestionsList() },
                    search = { viewModel.searchUsersList(it) },
                    onCancel = { showAddPeople = false },
                    onConfirm = { selectedUsers, _ ->
                        viewModel.addGroupMembers(selectedUsers)
                        showAddPeople = false
                    },
                )
            } else {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title-less top bar with just a trailing "Done" (Instagram-style): the
            // group name below acts as the header.
            Box(modifier = Modifier.fillMaxWidth().padding(CorusSpacing.lg)) {
                Text(
                    stringResource(id = R.string.messaging_group_done),
                    style = CorusFont.button,
                    color = CorusColors.Accent,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { onDismiss() },
                )
            }

            // Photo + name + member count
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                fun launchPhotoPicker() {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                // Tappable avatar → photo; a spinner replaces it while uploading.
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !uploadingPhoto) { launchPhotoPicker() },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        uploadingPhoto -> Box(
                            modifier = Modifier.fillMaxSize().clip(CircleShape).background(CorusColors.CardBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = CorusColors.Secondary, strokeWidth = 2.dp)
                        }
                        groupInfo.photoURL != null -> UserAvatarView(avatarURL = groupInfo.photoURL, displayName = "", username = "", size = 84.dp)
                        else -> StackedGroupAvatar(members = otherMembers, size = 84.dp)
                    }
                }
                Spacer(modifier = Modifier.height(CorusSpacing.sm))

                // The group name is the header (Instagram-style): custom name, else
                // the members' names, wrapping up to two lines. Renaming is a dialog.
                Text(
                    groupDisplayTitle(name.ifBlank { null }, otherMembers, context),
                    style = CorusFont.displayName,
                    color = CorusColors.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.sm))

                Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg)) {
                    Text(
                        stringResource(id = R.string.messaging_group_change_name),
                        style = CorusFont.body, color = CorusColors.Accent,
                        modifier = Modifier.clickable {
                            renameDraft = name
                            showRename = true
                        },
                    )
                    Text(
                        stringResource(id = R.string.messaging_group_change_photo),
                        style = CorusFont.body, color = CorusColors.Accent,
                        modifier = Modifier.clickable(enabled = !uploadingPhoto) { launchPhotoPicker() },
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onNavigateToProfile(id)
                                }
                                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
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
        // Non-blocking spinner while the leave request is in flight (swipe-down
        // still dismisses the sheet, so it can't lock).
        if (leaving) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CorusColors.Accent)
            }
        }

        // Square crop overlay (parity with iOS + the profile-photo flow). Rendered
        // as the last child so it covers the group-info content, and inside this
        // sheet's window so its nav-bar insets resolve correctly. On confirm the
        // cropped bytes are uploaded; cancel returns to the group info.
        cropBitmap?.let { bmp ->
            Box(modifier = Modifier.matchParentSize()) {
                AvatarCropView(
                    bitmap = bmp,
                    onConfirm = { bytes ->
                        cropBitmap = null
                        viewModel.uploadAndSetGroupPhoto(bytes)
                    },
                    onCancel = { cropBitmap = null },
                )
            }
        }
            }
        }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(id = R.string.messaging_group_name_label)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(60) },
                    placeholder = { Text(stringResource(id = R.string.messaging_group_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        name = renameDraft.trim()
                        viewModel.renameGroup(name)
                        showRename = false
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    name = renameDraft.trim()
                    viewModel.renameGroup(name)
                    showRename = false
                }) { Text(stringResource(id = R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(id = R.string.messaging_group_cancel))
                }
            },
            containerColor = CorusColors.Background,
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(id = R.string.messaging_group_leave_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    leaving = true
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
