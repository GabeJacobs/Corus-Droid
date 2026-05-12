package fm.corus.android.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HashtagPeopleListViewModel @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val pageSize = 30

    private val _users = MutableStateFlow<List<CymbalUser>>(emptyList())
    val users: StateFlow<List<CymbalUser>> = _users.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var currentTag: String? = null
    private var currentIsFollowers: Boolean = false
    private var lastDoc: DocumentSnapshot? = null
    private val loadedIds = mutableSetOf<String>()

    val currentUserId: String? get() = authRepository.currentUserId

    fun load(tag: String, isFollowers: Boolean) {
        if (currentTag == tag && currentIsFollowers == isFollowers) return
        currentTag = tag
        currentIsFollowers = isFollowers
        lastDoc = null
        loadedIds.clear()
        _users.value = emptyList()
        _hasMore.value = true
        _isLoading.value = true
        loadPage(initial = true)
    }

    fun loadMore() {
        if (_isLoadingMore.value || _isLoading.value || !_hasMore.value) return
        _isLoadingMore.value = true
        loadPage(initial = false)
    }

    private fun loadPage(initial: Boolean) {
        val tag = currentTag ?: return
        viewModelScope.launch {
            try {
                val (ids, last) = if (currentIsFollowers) {
                    firestoreDataSource.fetchHashtagFollowerIds(tag, pageSize, lastDoc)
                } else {
                    firestoreDataSource.fetchHashtagContributorIds(tag, pageSize, lastDoc)
                }
                lastDoc = last
                if (ids.size < pageSize) _hasMore.value = false
                val newIds = ids.filter { it !in loadedIds }
                if (newIds.isNotEmpty()) {
                    val fetched = firestoreDataSource.fetchUsersByIds(newIds)
                    val byId = fetched.associateBy { it.id }
                    val ordered = newIds.mapNotNull { byId[it] }
                    loadedIds.addAll(ordered.map { it.id })
                    _users.value = _users.value + ordered
                }
            } catch (_: Exception) {
                _hasMore.value = false
            }
            if (initial) _isLoading.value = false
            _isLoadingMore.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagPeopleListScreen(
    hashtag: String,
    isFollowers: Boolean,
    viewModel: HashtagPeopleListViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
) {
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(hashtag, isFollowers) {
        viewModel.load(hashtag, isFollowers)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3 && hasMore && !isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && searchQuery.isBlank()) viewModel.loadMore()
    }

    val filteredUsers = if (searchQuery.isBlank()) users else {
        users.filter {
            it.username.contains(searchQuery, ignoreCase = true) ||
                it.displayName.contains(searchQuery, ignoreCase = true)
        }
    }

    val titleRes = if (isFollowers) {
        fm.corus.android.R.string.hashtag_followers_title
    } else {
        fm.corus.android.R.string.hashtag_contributors_title
    }
    val emptyMessageRes = if (isFollowers) {
        fm.corus.android.R.string.hashtag_followers_empty
    } else {
        fm.corus.android.R.string.hashtag_contributors_empty
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(titleRes, hashtag),
                        style = CorusFont.screenTitle,
                        color = CorusColors.Text,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(fm.corus.android.R.string.common_back),
                            tint = CorusColors.Text,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                placeholder = {
                    Text(
                        stringResource(fm.corus.android.R.string.follow_list_search_placeholder),
                        style = CorusFont.body, color = CorusColors.Tertiary,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = CorusColors.Secondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(fm.corus.android.R.string.follow_list_cd_clear),
                                tint = CorusColors.Secondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CorusColors.CardBackground,
                    unfocusedContainerColor = CorusColors.CardBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = CorusColors.Accent,
                ),
                textStyle = CorusFont.body.copy(color = CorusColors.Text),
            )

            when {
                isLoading && users.isEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(10) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(CorusSpacing.avatarMedium)
                                        .background(CorusColors.CardBackground, RoundedCornerShape(50)),
                                )
                                Spacer(modifier = Modifier.width(CorusSpacing.md))
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 100.dp, height = 12.dp)
                                            .background(CorusColors.CardBackground, RoundedCornerShape(4.dp)),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(width = 70.dp, height = 10.dp)
                                            .background(CorusColors.CardBackground, RoundedCornerShape(4.dp)),
                                    )
                                }
                            }
                        }
                    }
                }
                filteredUsers.isEmpty() && !isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (isFollowers) Icons.Outlined.Group else Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            tint = CorusColors.Tertiary,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        Text(
                            text = if (searchQuery.isNotBlank())
                                stringResource(fm.corus.android.R.string.follow_list_no_results)
                            else stringResource(emptyMessageRes),
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Secondary,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(filteredUsers, key = { it.id }) { user ->
                            HashtagPersonRow(
                                user = user,
                                onTap = { onNavigateToUser(user.id) },
                            )
                        }
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(CorusSpacing.lg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = CorusColors.Accent,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HashtagPersonRow(
    user: CymbalUser,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(
            avatarURL = user.avatarURL,
            displayName = user.displayName,
            size = (CorusSpacing.avatarMedium + 8.dp),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            UsernameWithFlair(
                username = user.username,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                flairStyle = user.flairStyle,
                isBot = user.isBot,
            )
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
