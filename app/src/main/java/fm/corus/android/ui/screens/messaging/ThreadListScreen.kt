package fm.corus.android.ui.screens.messaging

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MessageType
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.util.DateUtils
import kotlinx.coroutines.launch

internal fun filterInboxThreads(threads: List<CymbalThread>, query: String): List<CymbalThread> {
    val q = query.trim()
    if (q.isEmpty()) return threads
    val ql = q.lowercase()
    return threads.filter { thread ->
        (thread.otherUser?.username?.lowercase()?.contains(ql) == true) ||
            (thread.otherUser?.displayName?.lowercase()?.contains(ql) == true) ||
            thread.lastMessageText.lowercase().contains(ql)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    onBack: () -> Unit = {},
    onThreadTap: (String, String) -> Unit = { _, _ -> },
    viewModel: ThreadListViewModel = hiltViewModel(),
) {
    val threads by viewModel.threads.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMoreThreads by viewModel.hasMoreThreads.collectAsState()
    val inboxSearchResults by viewModel.inboxSearchResults.collectAsState()
    val isSearchingInbox by viewModel.isSearchingInbox.collectAsState()
    val groupMembersById by viewModel.groupMembersById.collectAsState()
    var showNewMessagePicker by remember { mutableStateOf(false) }
    var isCreatingThread by remember { mutableStateOf(false) }
    var inboxSearchText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // What the list renders. When searching, prefer the authoritative backend
    // results once they land; until then fall back to the instant local filter
    // over the already-loaded threads.
    val displayedThreads = remember(threads, inboxSearchText, inboxSearchResults) {
        when {
            inboxSearchText.isBlank() -> threads
            inboxSearchResults != null -> inboxSearchResults!!
            else -> filterInboxThreads(threads, inboxSearchText)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadThreads()
    }

    LaunchedEffect(inboxSearchText) {
        viewModel.searchInbox(inboxSearchText)
    }

    Box(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header with back and compose button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CorusSpacing.xs, end = CorusSpacing.lg, top = CorusSpacing.md, bottom = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CorusHeaderIconButton(
                onClick = onBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.common_back),
            )
            Text(
                stringResource(id = R.string.messaging_list_title),
                style = CorusFont.screenTitle,
                color = CorusColors.Text,
                modifier = Modifier.weight(1f),
            )
            CorusHeaderIconButton(
                onClick = { showNewMessagePicker = true },
                painter = painterResource(id = R.drawable.ic_edit_square),
                contentDescription = stringResource(id = R.string.messaging_list_cd_new_message),
            )
        }

        HorizontalDivider(color = CorusColors.Divider)

        if (!isLoading && threads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(id = R.string.messaging_list_empty), style = CorusFont.body, color = CorusColors.Secondary)
            }
        } else {
            InboxSearchBar(
                value = inboxSearchText,
                onValueChange = { inboxSearchText = it },
                onClear = { inboxSearchText = "" },
            )
            HorizontalDivider(color = CorusColors.Divider)

            if (isLoading && threads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CorusColors.Accent)
                }
            } else if (displayedThreads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (inboxSearchText.isNotBlank() && isSearchingInbox) {
                        CircularProgressIndicator(color = CorusColors.Accent)
                    } else {
                        Text(stringResource(id = R.string.messaging_list_no_matches), style = CorusFont.body, color = CorusColors.Secondary)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(displayedThreads, key = { it.id }) { thread ->
                        ThreadRow(
                            thread = thread,
                            onClick = { onThreadTap(thread.id, thread.otherUserId) },
                            membersById = groupMembersById,
                            currentUserId = viewModel.currentUserId,
                        )
                    }

                    // Paginate by recency only when not searching — search filters the
                    // already-loaded set client-side.
                    if (hasMoreThreads && inboxSearchText.isBlank()) {
                        item {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = CorusSpacing.xl)
                                    .wrapContentWidth(Alignment.CenterHorizontally),
                                color = CorusColors.Secondary,
                                strokeWidth = 2.dp,
                            )
                            LaunchedEffect(threads.size) {
                                viewModel.loadMoreThreads()
                            }
                        }
                    }
                }
            }
        }
    }

        // Blocking loading overlay shown while the thread is being created/fetched
        // after a recipient is tapped in the New Message picker.
        if (isCreatingThread) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CorusColors.Background.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = CorusColors.Accent)
            }
        }
    }

    // New Message picker sheet
    if (showNewMessagePicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Within the sheet, the default DM list can hand off to the group-create
        // flow (Instagram-style) without closing.
        var showGroupCreate by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = {
                viewModel.clearSearch()
                showGroupCreate = false
                showNewMessagePicker = false
            },
            sheetState = sheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            CorusSystemBars()
            if (showGroupCreate) {
                // Multi-select: one selection starts a 1:1 DM, two+ creates a group.
                MultiUserPickerContent(
                    title = stringResource(id = R.string.messaging_group_new_title),
                    showNameField = true,
                    excludeIds = setOf(viewModel.currentUserId ?: ""),
                    loadSuggestions = { viewModel.fetchSuggestionsList() },
                    search = { viewModel.searchUsersList(it) },
                    onCancel = { showGroupCreate = false }, // back to the DM list
                    onConfirm = { selectedUsers, name ->
                        showGroupCreate = false
                        showNewMessagePicker = false
                        isCreatingThread = true
                        scope.launch {
                            try {
                                if (selectedUsers.size == 1) {
                                    val u = selectedUsers.first()
                                    val threadId = viewModel.getOrCreateThread(u.id)
                                    onThreadTap(threadId, u.id)
                                } else if (selectedUsers.size >= 2) {
                                    val threadId = viewModel.createGroup(selectedUsers.map { it.id }, name)
                                    onThreadTap(threadId, "")
                                }
                            } catch (_: Exception) {
                            } finally {
                                isCreatingThread = false
                            }
                        }
                    },
                )
            } else {
                NewMessagePickerContent(
                    viewModel = viewModel,
                    groupMessagingEnabled = viewModel.groupMessagingEnabled,
                    onGroupChat = { showGroupCreate = true },
                    onCancel = {
                        viewModel.clearSearch()
                        showNewMessagePicker = false
                    },
                    onUserSelected = { user ->
                        viewModel.clearSearch()
                        showNewMessagePicker = false
                        isCreatingThread = true
                        scope.launch {
                            try {
                                val threadId = viewModel.getOrCreateThread(user.id)
                                onThreadTap(threadId, user.id)
                            } catch (_: Exception) {
                            } finally {
                                isCreatingThread = false
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NewMessagePickerContent(
    viewModel: ThreadListViewModel,
    onCancel: () -> Unit,
    onUserSelected: (CymbalUser) -> Unit,
    groupMessagingEnabled: Boolean = false,
    onGroupChat: () -> Unit = {},
) {
    val suggestedContacts by viewModel.suggestedContacts.collectAsState()
    val isLoadingSuggestions by viewModel.isLoadingSuggestions.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    var searchText by remember { mutableStateOf("") }

    val displayedUsers = if (searchText.isBlank()) suggestedContacts else searchResults

    LaunchedEffect(Unit) {
        viewModel.loadSuggestedContacts()
    }

    LaunchedEffect(searchText) {
        viewModel.searchUsers(searchText.trim())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f),
    ) {
        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = R.string.common_cancel), style = CorusFont.body, color = CorusColors.Accent)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(stringResource(id = R.string.messaging_list_new_message_title), style = CorusFont.screenTitle, color = CorusColors.Text)
            Spacer(modifier = Modifier.weight(1f))
            // Invisible spacer to balance the Cancel button
            Spacer(modifier = Modifier.width(64.dp))
        }

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CorusColors.CardBackground)
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = stringResource(id = R.string.search_cd_search),
                tint = CorusColors.Tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Box(modifier = Modifier.weight(1f)) {
                if (searchText.isEmpty()) {
                    Text(
                        stringResource(id = R.string.messaging_list_search_placeholder),
                        style = CorusFont.body,
                        color = CorusColors.Tertiary,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    textStyle = CorusFont.body.copy(color = CorusColors.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (searchText.isNotEmpty()) {
                IconButton(
                    onClick = {
                        searchText = ""
                        viewModel.clearSearch()
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(id = R.string.search_cd_clear),
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = CorusColors.Divider)

        // Entry to the separate group-creation flow (the default list DMs people
        // one-tap; group capability only restricts who you can put IN a group).
        if (groupMessagingEnabled && searchText.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGroupChat)
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                Box(
                    modifier = Modifier.size(CorusSpacing.avatarMedium).clip(CircleShape).background(CorusColors.CardBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Group, contentDescription = null, tint = CorusColors.Accent, modifier = Modifier.size(20.dp))
                }
                Text(stringResource(id = R.string.messaging_group_chat), style = CorusFont.username, color = CorusColors.Text, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = CorusColors.Tertiary)
            }
            HorizontalDivider(color = CorusColors.Divider)
        }

        // Content
        if (isSearching || isLoadingSuggestions) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = CorusColors.Secondary)
            }
        } else if (displayedUsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (searchText.isNotBlank()) stringResource(id = R.string.search_no_users_found) else stringResource(id = R.string.messaging_list_no_suggestions),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(displayedUsers, key = { it.id }) { user ->
                    UserPickerRow(
                        user = user,
                        onClick = { onUserSelected(user) },
                    )
                    HorizontalDivider(
                        color = CorusColors.Divider,
                        modifier = Modifier.padding(
                            start = CorusSpacing.lg + CorusSpacing.avatarMedium + CorusSpacing.md,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun UserPickerRow(user: CymbalUser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(
            avatarURL = user.avatarURL,
            displayName = user.displayName,
            size = CorusSpacing.avatarMedium,
        )

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Column {
            UsernameWithFlair(
                username = user.username,
                isBot = user.isBot,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                flairStyle = user.flairStyle,
                style = CorusFont.username,
                color = CorusColors.Text,
            )
            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
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

@Composable
private fun InboxSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CorusColors.CardBackground)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = stringResource(id = R.string.search_cd_search),
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    stringResource(id = R.string.messaging_list_inbox_search_placeholder),
                    style = CorusFont.body,
                    color = CorusColors.Tertiary,
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = CorusFont.body.copy(color = CorusColors.Text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(id = R.string.search_cd_clear),
                    tint = CorusColors.Tertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ThreadRow(
    thread: CymbalThread,
    onClick: () -> Unit,
    membersById: Map<String, CymbalUser> = emptyMap(),
    currentUserId: String? = null,
) {
    val context = LocalContext.current
    val isGroup = thread.isGroup
    val otherMembers = if (isGroup) {
        thread.memberIds.filter { it != currentUserId }.mapNotNull { membersById[it] }
    } else emptyList()
    val title = if (isGroup) {
        groupDisplayTitle(thread.groupName, otherMembers, context)
    } else {
        thread.otherUser?.username ?: ""
    }
    // In groups, prefix the sender's first name for other people's messages;
    // system events already carry full text.
    val preview = if (isGroup && thread.lastMessageType != MessageType.SYSTEM) {
        val fromId = thread.lastMessageFromUserId
        val sender = if (fromId != null && fromId != currentUserId) membersById[fromId] else null
        val first = sender?.let { (it.displayName.ifBlank { it.username }).split(" ").firstOrNull() }
        if (!first.isNullOrBlank()) {
            context.getString(R.string.messaging_group_sender_preview, first, thread.lastMessageText)
        } else thread.lastMessageText
    } else thread.lastMessageText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isGroup && thread.groupPhotoURL != null) {
            UserAvatarView(
                avatarURL = thread.groupPhotoURL,
                displayName = title,
                size = CorusSpacing.avatarMedium,
            )
        } else if (isGroup) {
            StackedGroupAvatar(members = otherMembers, size = CorusSpacing.avatarMedium)
        } else {
            UserAvatarView(
                avatarURL = thread.otherUser?.avatarURL,
                displayName = thread.otherUser?.displayName,
                size = CorusSpacing.avatarMedium,
            )
        }

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = CorusFont.username,
                    color = CorusColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = DateUtils.relativeTime(LocalContext.current, thread.lastMessageAt),
                    style = CorusFont.timestamp,
                    color = CorusColors.Secondary,
                )
            }
            Text(
                text = preview,
                style = CorusFont.body,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (thread.unreadCount > 0) {
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Badge(containerColor = CorusColors.Accent) {
                Text(thread.unreadCount.toString())
            }
        }
    }
}
