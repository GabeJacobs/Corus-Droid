package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.parseMentionQuery
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCaptionSheet(
    postId: String,
    initialCaption: String,
    albumArtURL: String? = null,
    viewModel: EditCaptionViewModel = hiltViewModel(),
    onDismiss: () -> Unit = {},
    onSaved: (String) -> Unit = {},
) {
    var caption by remember { mutableStateOf(initialCaption) }
    val isSaving by viewModel.isSaving.collectAsState()
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var mentionSearchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Caption", style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", style = CorusFont.body, color = CorusColors.Secondary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveCaption(
                                postId = postId,
                                caption = caption,
                                oldCaption = initialCaption,
                                postAlbumArtURL = albumArtURL,
                            ) {
                                onSaved(caption)
                                onDismiss()
                            }
                        },
                        enabled = !isSaving,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CorusColors.Accent,
                            )
                        } else {
                            Text("Save", style = CorusFont.button, color = CorusColors.Accent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TextField(
                value = caption,
                onValueChange = { newValue ->
                    caption = newValue
                    // Check for @mention
                    mentionSearchJob?.cancel()
                    mentionSearchJob = scope.launch {
                        delay(200)
                        val query = parseMentionQuery(newValue)
                        if (query != null) {
                            viewModel.searchMentions(query)
                        } else {
                            viewModel.clearMentions()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("Write a caption...", style = CorusFont.body, color = CorusColors.Tertiary) },
                textStyle = CorusFont.body.copy(color = CorusColors.Text),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = CorusColors.Accent,
                ),
                minLines = 3,
                maxLines = 10,
            )

            // Mention suggestions
            if (mentionSuggestions.isNotEmpty()) {
                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
                mentionSuggestions.take(4).forEachIndexed { index, user ->
                    MentionSuggestionRow(
                        user = user,
                        onClick = {
                            // Replace the @query with @username
                            val lastAtIndex = caption.lastIndexOf("@")
                            if (lastAtIndex >= 0) {
                                caption = caption.substring(0, lastAtIndex) + "@${user.username} "
                            }
                            viewModel.clearMentions()
                        },
                    )
                    if (index < mentionSuggestions.take(4).lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = (CorusSpacing.lg + 28.dp + CorusSpacing.sm)),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MentionSuggestionRow(
    user: CymbalUser,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, size = 28.dp)
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column {
            Text(
                text = user.username,
                style = CorusFont.username,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.displayName.isNotBlank() && user.displayName.lowercase() != user.username.lowercase()) {
                Text(
                    text = user.displayName,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
