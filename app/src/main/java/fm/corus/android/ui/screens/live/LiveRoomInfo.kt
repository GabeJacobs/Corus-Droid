package fm.corus.android.ui.screens.live

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.Image
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.components.AvatarCropView
import fm.corus.android.ui.components.uriToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.style.TextAlign
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
    initialImageURL: String? = null,
    uploadImage: suspend (ByteArray) -> String,
    onSave: suspend (String, String, String?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var imageURL by rememberSaveable { mutableStateOf(initialImageURL) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var cropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loadingImage by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            loadingImage = true
            try {
                cropBitmap = withContext(Dispatchers.IO) { uriToBitmap(context, uri) }
                if (cropBitmap == null) failed = true
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { failed = true }
            finally { loadingImage = false }
        }
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.isImeVisible
    val normalizedTitle = title.trim().replace(Regex("[\\s\\p{Z}]+"), " ")
    val normalizedDescription = description.trim()
    val changed = normalizedTitle != initialTitle || normalizedDescription != initialDescription || imageURL != initialImageURL || imageBytes != null
    val close = {
        if (!saving && !loadingImage) {
            focus.clearFocus()
            keyboard?.hide()
            if (!editable || !keyboardVisible) {
                if (editable && changed) discard = true else onDismiss()
            }
        }
    }
    LiveSheet(
        stringResource(R.string.live_room_info), close, large = true,
        // Open expanded so listeners can see the room details immediately.
        startsHalfExpanded = false,
        // Only the editor needs keyboard-first dismissal.
        dismissKeyboardFromHeader = editable, dismissEnabled = !saving && !loadingImage,
        // A listener has no draft to protect; an underlying chat IME must not
        // block Material's outside-tap/back/swipe dismissal of room details.
        allowSwipeDismiss = !editable || (!keyboardVisible && !changed),
    ) {
        // The modal sheet has its own focus owner; use the field's owner here.
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        Column(Modifier.fillMaxSize().imePadding().dismissKeyboardOnDownwardDrag()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (editable) {
                    LiveText(stringResource(CorusStrings.live_room_image), CorusFont.usernameLarge)
                    val preview = remember(imageBytes) { imageBytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } }
                    if (preview != null || imageURL != null) Box(Modifier.size(96.dp)) {
                        if (preview != null) Image(preview.asImageBitmap(), null, Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        else ShimmerAsyncImage(model = imageURL, contentDescription = null, modifier = Modifier.size(96.dp), shape = CircleShape)
                        if (editable) IconButton(
                            onClick = { imageURL = null; imageBytes = null },
                            enabled = !saving && !loadingImage,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(44.dp),
                        ) {
                            Box(Modifier.size(28.dp).background(CorusColors.CardBackground, CircleShape).border(1.dp, CorusColors.Divider, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(CorusStrings.live_room_image_remove), modifier = Modifier.size(14.dp), tint = CorusColors.Text)
                            }
                        }
                    }
                    if (editable) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !saving && !loadingImage,
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, CorusColors.Divider),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = CorusColors.SheetControlBackground,
                                    contentColor = CorusColors.Text,
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                onClick = {
                                    focus.clearFocus(); keyboard?.hide()
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(20.dp), tint = CorusColors.Secondary)
                                Spacer(Modifier.width(8.dp))
                                LiveText(stringResource(CorusStrings.live_room_image_change),
                                    color = if (saving || loadingImage) CorusColors.Secondary else CorusColors.Text)
                            }
                            if (loadingImage) CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
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
                            keyboardActions = KeyboardActions(onDone = {
                                focus.clearFocus()
                                keyboard?.hide()
                            }),
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
                    if (imageURL != null || initialTitle.isNotBlank()) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (imageURL != null) ShimmerAsyncImage(
                                model = imageURL,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                shape = CircleShape,
                            )
                            if (initialTitle.isNotBlank()) SelectionContainer {
                                Text(
                                    initialTitle,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = CorusFont.usernameLarge,
                                    color = CorusColors.Text,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    hostContent?.invoke()
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
                LivePrimary(stringResource(actionLabel), !saving && !loadingImage) {
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
                            imageBytes?.let { bytes -> imageURL = uploadImage(bytes); imageBytes = null }
                            onSave(normalizedTitle, normalizedDescription, imageURL)
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
    cropBitmap?.let { bitmap ->
        AvatarCropView(bitmap = bitmap, onConfirm = { bytes -> imageBytes = bytes; cropBitmap = null; failed = false }, onCancel = { cropBitmap = null })
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
