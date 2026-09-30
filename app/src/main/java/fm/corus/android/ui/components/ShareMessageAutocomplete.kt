package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.HashtagSuggestion
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.ui.theme.CorusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ShareMessageSearchDependencies {
    fun users(): UserRepository
    fun hashtags(): ExploreRepository
}

/** A non-focusable overlay above the editor; suggestions never resize the sheet. */
@Composable
internal fun ShareMessageAutocomplete(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    active: Boolean,
    searchUsers: (suspend (String) -> List<CymbalUser>)? = null,
    searchHashtags: (suspend (String) -> List<HashtagSuggestion>)? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    var width by remember { mutableIntStateOf(0) }
    var height by remember { mutableIntStateOf(0) }
    var users by remember { mutableStateOf(emptyList<CymbalUser>()) }
    var tags by remember { mutableStateOf(emptyList<HashtagSuggestion>()) }
    var loading by remember { mutableStateOf(false) }
    val mention = if (active && value.selection.collapsed) parseMentionQuery(value.text, value.selection.start) else null
    val hashtag = if (active && value.selection.collapsed && mention == null) parseHashtagQuery(value.text, value.selection.start) else null
    LaunchedEffect(mention, hashtag) {
        users = emptyList()
        tags = emptyList()
        loading = mention != null
        if (mention == null && hashtag == null) return@LaunchedEffect
        try {
            delay(200)
            if (mention != null) {
                val results = searchUsers?.invoke(mention) ?: EntryPointAccessors.fromApplication(context, ShareMessageSearchDependencies::class.java).users().searchUsers(mention, limit = 4)
                coroutineContext.ensureActive()
                users = results
            } else if (hashtag != null) {
                val results = searchHashtags?.invoke(hashtag) ?: EntryPointAccessors.fromApplication(context, ShareMessageSearchDependencies::class.java).hashtags().fetchHashtagSuggestions(hashtag, limit = 3)
                coroutineContext.ensureActive()
                tags = results
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            users = emptyList()
            tags = emptyList()
        } finally {
            if (coroutineContext.isActive) loading = false
        }
    }
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width; height = it.height }) {
        content()
        if (active && ((mention != null && (users.isNotEmpty() || loading)) || (hashtag != null && tags.isNotEmpty()))) {
            Popup(
                alignment = Alignment.BottomStart,
                offset = IntOffset(0, -height),
                properties = PopupProperties(focusable = false),
            ) {
                Column(Modifier.width(with(density) { width.toDp() }).background(CorusColors.Background)) {
                    if (mention != null) MentionSuggestionsList(users, { onValueChange(applyMention(value, it.username)) }, isSearching = loading)
                    if (hashtag != null) HashtagSuggestionsList(tags, { onValueChange(applyHashtag(value, it.name)) })
                }
            }
        }
    }
}
