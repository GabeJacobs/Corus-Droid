package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.ui.components.MentionSuggestionsList
import fm.corus.android.ui.components.applyMention
import fm.corus.android.ui.components.parseMentionQuery
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
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
    var caption by remember {
        mutableStateOf(TextFieldValue(initialCaption, selection = TextRange(initialCaption.length)))
    }
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
                                caption = caption.text,
                                oldCaption = initialCaption,
                                postAlbumArtURL = albumArtURL,
                            ) {
                                onSaved(caption.text)
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
                    val textChanged = newValue.text != caption.text
                    caption = newValue
                    if (textChanged) {
                        mentionSearchJob?.cancel()
                        mentionSearchJob = scope.launch {
                            delay(200)
                            val query = parseMentionQuery(newValue.text)
                            if (query != null) viewModel.searchMentions(query) else viewModel.clearMentions()
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

            MentionSuggestionsList(
                users = mentionSuggestions.take(4),
                onSelect = { user ->
                    caption = applyMention(caption, user.username)
                    viewModel.clearMentions()
                },
            )
        }
    }
}
