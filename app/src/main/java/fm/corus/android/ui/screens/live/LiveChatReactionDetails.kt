package fm.corus.android.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat

@Composable
internal fun LiveChatReactionDetails(
    groups: Map<String, List<String>>,
    initialEmoji: String,
    loadPeople: suspend (List<String>) -> Map<String, LivePerson>,
    dismiss: () -> Unit,
) {
    var selectedEmoji by remember(initialEmoji) { mutableStateOf(initialEmoji) }
    val emojis = groups.keys.toList()
    val activeEmoji = selectedEmoji.takeIf(groups::containsKey) ?: emojis.first()
    val userIds = groups.getValue(activeEmoji)
    val load by rememberUpdatedState(loadPeople)
    var people by remember(userIds) { mutableStateOf<Map<String, LivePerson>?>(null) }
    var failed by remember(userIds) { mutableStateOf(false) }
    var retry by remember(userIds) { mutableIntStateOf(0) }
    LaunchedEffect(userIds, retry) {
        failed = false
        try {
            people = load(userIds)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }

    val accent = if (LocalCorusDarkTheme.current) Color(0xFF98BAFF) else Color(0xFF3A6BB9)
    val tabs = rememberLazyListState(initialFirstVisibleItemIndex = emojis.indexOf(activeEmoji))
    LiveSheet(title = null, close = dismiss, large = true, startsHalfExpanded = true) {
        LazyRow(state = tabs, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(emojis, key = { it }) { emoji ->
                val selected = emoji == activeEmoji
                Row(
                    Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp).clip(CircleShape)
                        .background(if (selected) accent.copy(alpha = .12f) else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { selectedEmoji = emoji })
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (selected) accent else CorusColors.Secondary
                    LiveText(emoji, color = tint)
                    LiveText(NumberFormat.getIntegerInstance().format(groups.getValue(emoji).size), color = tint)
                }
            }
        }
        HorizontalDivider()
        if (failed) {
            TextButton(onClick = { retry++ }) { LiveText(stringResource(R.string.share_try_again)) }
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(userIds, key = { it }) { id ->
                    val person = people?.get(id)
                    val displayName = person?.profile?.displayName.orEmpty()
                    val handle = person?.username?.removePrefix("@").orEmpty()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiveAvatar(person ?: LivePerson("", "", null), 36.dp)
                        if (people == null) {
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        } else if (person == null || (displayName.isBlank() && handle.isBlank())) {
                            LiveText(stringResource(R.string.other_profile_unavailable_title), color = CorusColors.Secondary)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                LiveText(displayName.ifBlank { "@$handle" })
                                if (displayName.isNotBlank() && handle.isNotBlank()) {
                                    LiveText("@$handle", CorusFont.caption, CorusColors.Secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
