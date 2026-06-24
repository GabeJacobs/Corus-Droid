package fm.corus.android.ui.screens.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reusable multi-select people picker (create a group, or add people to one),
 * mirroring the iOS NewMessagePickerView. Lambda-driven so it works for either
 * host VM. In create mode (`showNameField`) one selection means a 1:1 DM and
 * two+ means a group. Anyone is selectable — a not-yet-updated member just gets
 * the old 1:1-style experience until they update (rollout is gated until then).
 */
@Composable
internal fun MultiUserPickerContent(
    title: String,
    showNameField: Boolean,
    excludeIds: Set<String>,
    loadSuggestions: suspend () -> List<CymbalUser>,
    search: suspend (String) -> List<CymbalUser>,
    onCancel: () -> Unit,
    onConfirm: (selected: List<CymbalUser>, groupName: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var searchText by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<CymbalUser>?>(null) }
    var results by remember { mutableStateOf<List<CymbalUser>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<CymbalUser>() }
    var groupName by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        suggestions = loadSuggestions().filter { it.id !in excludeIds }
    }

    LaunchedEffect(searchText) {
        val q = searchText.trim()
        if (q.isEmpty()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(250)
        results = runCatching { search(q) }.getOrDefault(emptyList()).filter { it.id !in excludeIds }
        searching = false
    }

    val displayed = if (searchText.isBlank()) (suggestions ?: emptyList()) else results

    val isLoading = (searchText.isBlank() && suggestions == null) || (searchText.isNotBlank() && searching)
    val selectedIds = selected.map { it.id }.toSet()

    val confirmLabel = when {
        !showNameField -> stringResource(id = R.string.messaging_group_add_cta)
        selected.size >= 2 -> stringResource(id = R.string.messaging_group_create)
        else -> stringResource(id = R.string.messaging_group_message_cta)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = R.string.messaging_group_cancel), style = CorusFont.body, color = CorusColors.Accent)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(title, style = CorusFont.screenTitle, color = CorusColors.Text)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(64.dp))
        }

        // Selected chips
        if (selected.isNotEmpty()) {
            FlowChips(selected = selected, onRemove = { u -> selected.removeAll { it.id == u.id } })
        }

        // Optional group name (create mode, 2+ selected).
        if (showNameField && selected.size >= 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
            ) {
                if (groupName.isEmpty()) {
                    Text(
                        stringResource(id = R.string.messaging_group_name_optional),
                        style = CorusFont.body,
                        color = CorusColors.Tertiary,
                    )
                }
                BasicTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(60) },
                    textStyle = CorusFont.body.copy(color = CorusColors.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HorizontalDivider(color = CorusColors.Divider)
        }

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CorusColors.CardBackground)
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = CorusColors.Tertiary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Box(modifier = Modifier.weight(1f)) {
                if (searchText.isEmpty()) {
                    Text(stringResource(id = R.string.messaging_list_search_placeholder), style = CorusFont.body, color = CorusColors.Tertiary)
                }
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    textStyle = CorusFont.body.copy(color = CorusColors.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (searchText.isNotEmpty()) {
                IconButton(onClick = { searchText = "" }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = CorusColors.Tertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
        HorizontalDivider(color = CorusColors.Divider)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CorusColors.Secondary)
                }
            } else if (displayed.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchText.isNotBlank()) stringResource(id = R.string.search_no_users_found)
                        else stringResource(id = R.string.messaging_list_no_suggestions),
                        style = CorusFont.body, color = CorusColors.Secondary,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(displayed, key = { it.id }) { user ->
                        val checked = user.id in selectedIds
                        MultiPickRow(
                            user = user,
                            checked = checked,
                            onClick = {
                                if (checked) selected.removeAll { it.id == user.id } else selected.add(user)
                            },
                        )
                    }
                }
            }
        }

        // Confirm button
        HorizontalDivider(color = CorusColors.Divider)
        Box(modifier = Modifier.fillMaxWidth().padding(CorusSpacing.lg)) {
            val canConfirm = selected.isNotEmpty() && !submitting
            Text(
                text = if (submitting) "…" else confirmLabel,
                style = CorusFont.button,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (canConfirm) CorusColors.Accent else CorusColors.Accent.copy(alpha = 0.5f))
                    .clickable(enabled = canConfirm) {
                        submitting = true
                        scope.launch {
                            onConfirm(selected.toList(), groupName.trim().ifBlank { null })
                            submitting = false
                        }
                    }
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FlowChips(selected: List<CymbalUser>, onRemove: (CymbalUser) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        selected.forEach { u ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(CorusColors.CardBackground)
                    .clickable { onRemove(u) }
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                UserAvatarView(avatarURL = u.avatarURL, avatarThumbURL = u.avatarThumbURL, displayName = u.displayName, username = u.username, size = 22.dp)
                Text("@${u.username}", style = CorusFont.caption, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.Close, contentDescription = null, tint = CorusColors.Tertiary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun MultiPickRow(user: CymbalUser, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, avatarThumbURL = user.avatarThumbURL, displayName = user.displayName, username = user.username, size = CorusSpacing.avatarMedium)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text("@${user.username}", style = CorusFont.username, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                user.displayName,
                style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) CorusColors.Accent else Color.Transparent)
                .then(if (checked) Modifier else Modifier.border(1.dp, CorusColors.Divider, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
