package fm.corus.android.ui.screens.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Merges a refreshed first page into the already-loaded thread list. Threads
 * not in the refreshed page keep their positions below it (they're older than
 * everything in the newest page), so the list never shrinks and the scroll
 * position holds.
 */
internal fun mergeRefreshedThreads(
    existing: List<CymbalThread>,
    refreshed: List<CymbalThread>,
): List<CymbalThread> {
    val refreshedIds = refreshed.map { it.id }.toSet()
    return refreshed + existing.filter { it.id !in refreshedIds }
}

/** Result of folding a live inbox snapshot into the loaded list. */
internal data class LiveThreadMerge(
    /** Loaded list with known threads' previews updated, sorted newest-first. */
    val merged: List<CymbalThread>,
    /** Live summaries for threads not yet loaded — need profile resolution. */
    val newThreads: List<CymbalThread>,
)

/**
 * Fold a live snapshot of thread summaries into the already-loaded list. Known
 * threads get their preview fields (text/type/time/sender/unread) refreshed
 * while keeping their resolved `otherUser`; threads absent from the loaded list
 * are returned separately so the caller can resolve their profiles. The merged
 * list is sorted by most-recent message so a new message floats to the top.
 */
internal fun applyLiveThreadUpdates(
    existing: List<CymbalThread>,
    live: List<CymbalThread>,
    pageSize: Int,
): LiveThreadMerge {
    val byId = existing.associateBy { it.id }.toMutableMap()
    val newThreads = mutableListOf<CymbalThread>()
    for (lt in live) {
        // Skip threads with no message yet (mirrors the loadThreads filter).
        if (lt.lastMessageFromUserId == null) continue
        val ex = byId[lt.id]
        if (ex != null) {
            byId[lt.id] = ex.copy(
                lastMessageText = lt.lastMessageText,
                lastMessageType = lt.lastMessageType,
                lastMessageAt = lt.lastMessageAt,
                lastMessageFromUserId = lt.lastMessageFromUserId,
                unreadCount = lt.unreadCount,
                // Reflect group edits (rename / new photo / membership) made here
                // or by another member. The live mirror carries the new name/photo
                // and memberIds but not the resolved member profiles, so keep those.
                groupName = lt.groupName ?: ex.groupName,
                groupPhotoURL = lt.groupPhotoURL ?: ex.groupPhotoURL,
                memberIds = if (lt.memberIds.isNotEmpty()) lt.memberIds else ex.memberIds,
                createdBy = ex.createdBy ?: lt.createdBy,
            )
        } else {
            newThreads.add(lt)
        }
    }
    // Prune threads that vanished from the live window — left, removed by someone
    // else, or deleted on another device. The snapshot is the newest `pageSize`
    // threads by recency, so it's authoritative for that window; older paginated
    // threads (below it) are kept.
    val liveIds = live.map { it.id }.toSet()
    val snapshotComplete = live.size < pageSize
    val windowOldest = live.minOfOrNull { it.lastMessageAt.time }
    if (windowOldest != null) {
        val kept = byId.filterValues { t ->
            liveIds.contains(t.id) || !(snapshotComplete || t.lastMessageAt.time >= windowOldest)
        }
        byId.clear()
        byId.putAll(kept)
    }
    val merged = byId.values.sortedByDescending { it.lastMessageAt.time }
    return LiveThreadMerge(merged, newThreads)
}

@HiltViewModel
class ThreadListViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val remoteConfigService: fm.corus.android.service.RemoteConfigService,
    private val analyticsService: fm.corus.android.service.AnalyticsService,
) : ViewModel() {

    val groupMessagingEnabled: Boolean
        get() = remoteConfigService.groupMessagingEnabled

    private val _threads = MutableStateFlow<List<CymbalThread>>(emptyList())
    val threads: StateFlow<List<CymbalThread>> = _threads.asStateFlow()

    // Resolved member profiles for group rows (stacked avatars + titles). The
    // live mirror carries only memberIds, so missing profiles are fetched here.
    private val _groupMembersById = MutableStateFlow<Map<String, CymbalUser>>(emptyMap())
    val groupMembersById: StateFlow<Map<String, CymbalUser>> = _groupMembersById.asStateFlow()

    init {
        // Drop a group from the inbox the instant the user leaves it.
        viewModelScope.launch {
            messageRepository.leftThreads.collect { id ->
                _threads.value = _threads.value.filterNot { it.id == id }
            }
        }
        viewModelScope.launch {
            _threads.collect { list ->
                val groups = list.filter { it.isGroup }
                if (groups.isEmpty()) return@collect
                val resolved = _groupMembersById.value.toMutableMap()
                // Seed from any members already resolved by the callable rows.
                for (g in groups) for (m in g.members) resolved.putIfAbsent(m.id, m)
                val missing = groups.flatMap { it.memberIds }.toSet()
                    .filter { it != currentUserId && it !in resolved }
                for (id in missing) {
                    runCatching { userRepository.fetchUserProfile(id) }.getOrNull()?.let { resolved[id] = it }
                }
                if (resolved.size != _groupMembersById.value.size) _groupMembersById.value = resolved
            }
        }
    }

    suspend fun createGroup(userIds: List<String>, name: String?): String {
        val threadId = messageRepository.createGroupThread(userIds, name)
        analyticsService.logGroupCreated(memberCount = userIds.size + 1, hasName = !name.isNullOrBlank())
        return threadId
    }

    suspend fun checkAddable(userIds: List<String>): Map<String, fm.corus.android.data.remote.CloudFunctionsDataSource.GroupAddability> =
        messageRepository.checkGroupAddable(userIds)

    /** Suggestion + search helpers for the multi-select group picker. */
    suspend fun fetchSuggestionsList(): List<CymbalUser> {
        val userId = authRepository.currentUserId ?: return emptyList()
        val contacts = runCatching { messageRepository.listThreads(userId) }
            .getOrDefault(emptyList()).mapNotNull { it.otherUser }
        if (contacts.isNotEmpty()) return contacts.take(20)
        runCatching { userRepository.prefetchFollowingSet(userId) }
        return userRepository.followingIds.value.take(20)
            .mapNotNull { runCatching { userRepository.fetchUserProfile(it) }.getOrNull() }
            .filter { !it.isBot }
    }

    suspend fun searchUsersList(query: String): List<CymbalUser> =
        runCatching { userRepository.searchUsers(query, limit = 15, includeFollowed = true) }
            .getOrDefault(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMoreThreads = MutableStateFlow(false)
    val hasMoreThreads: StateFlow<Boolean> = _hasMoreThreads.asStateFlow()

    private val pageSize = 30

    /** Cursor for the next page — the `updatedAt` of the last thread fetched, in millis. */
    private var nextCursor: Long? = null

    /**
     * The screen's LaunchedEffect re-runs every time it's recomposed after a
     * back navigation. After the first successful load, reloads refresh in
     * place instead of resetting to the first page, which would drop
     * paginated-in threads and yank the scroll position.
     */
    private var hasLoadedThreads = false

    // New Message picker state
    private val _suggestedContacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    val suggestedContacts: StateFlow<List<CymbalUser>> = _suggestedContacts.asStateFlow()

    private val _isLoadingSuggestions = MutableStateFlow(false)
    val isLoadingSuggestions: StateFlow<Boolean> = _isLoadingSuggestions.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val searchResults: StateFlow<List<CymbalUser>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId

    private var searchJob: Job? = null
    private var threadSummaryJob: Job? = null

    // Inbox search. The local filter in the screen only sees paged-in threads, so
    // a debounced backend `searchThreads` backfills matches from the full history.
    // Null = no backend answer yet for the current query (fall back to local filter).
    private val _inboxSearchResults = MutableStateFlow<List<CymbalThread>?>(null)
    val inboxSearchResults: StateFlow<List<CymbalThread>?> = _inboxSearchResults.asStateFlow()

    private val _isSearchingInbox = MutableStateFlow(false)
    val isSearchingInbox: StateFlow<Boolean> = _isSearchingInbox.asStateFlow()

    private var inboxSearchJob: Job? = null

    fun loadThreads() {
        val userId = authRepository.currentUserId ?: return
        if (hasLoadedThreads) {
            viewModelScope.launch { refreshThreads(userId) }
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val page = messageRepository.listThreadsPage(userId, limit = pageSize)
                val left = messageRepository.recentlyLeftThreadIds()
                _threads.value = page.threads.filter { it.lastMessageFromUserId != null && it.id !in left }
                nextCursor = page.nextCursor
                _hasMoreThreads.value = page.hasMore && page.nextCursor != null
                hasLoadedThreads = true
                startThreadSummaryListener(userId)
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    /**
     * Keep the first page of the inbox live: merge a real-time snapshot of the
     * caller's threads so previews/timestamps/unread update without leaving the
     * screen. Started once after the first load; the paginated callable still
     * loads older threads on scroll. Profiles for brand-new threads (someone
     * messaging you for the first time while you sit here) are resolved on the
     * side and folded in.
     */
    private fun startThreadSummaryListener(userId: String) {
        if (threadSummaryJob != null) return
        threadSummaryJob = viewModelScope.launch {
            messageRepository.listenToThreadSummaries(userId, pageSize.toLong()).collect { liveRaw ->
                // Drop threads the user just left before merging: a stale cache
                // snapshot can still carry their mirror doc, and the async profile
                // resolution below would otherwise re-add them after they're gone.
                val left = messageRepository.recentlyLeftThreadIds()
                val live = if (left.isEmpty()) liveRaw else liveRaw.filterNot { it.id in left }
                val (merged, newThreads) = applyLiveThreadUpdates(_threads.value, live, pageSize)
                _threads.value = if (left.isEmpty()) merged else merged.filterNot { it.id in left }
                if (newThreads.isEmpty()) return@collect
                val resolved = newThreads.mapNotNull { lt ->
                    if (lt.isGroup) {
                        // Resolve member profiles for a group someone added you to
                        // while you were sitting on the inbox (1:1 rows resolve the
                        // single otherUser below).
                        val members = lt.memberIds
                            .filter { it != currentUserId }
                            .mapNotNull { runCatching { userRepository.fetchUserProfile(it) }.getOrNull() }
                        lt.copy(members = members)
                    } else {
                        val profile = runCatching {
                            userRepository.fetchUserProfile(lt.otherUserId)
                        }.getOrNull()
                        profile?.let { lt.copy(otherUser = it) }
                    }
                }
                if (resolved.isNotEmpty()) {
                    // resolved goes last so it wins over any stale duplicate.
                    _threads.value = (_threads.value + resolved)
                        .associateBy { it.id }
                        .values
                        .sortedByDescending { it.lastMessageAt.time }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        threadSummaryJob?.cancel()
        searchJob?.cancel()
        inboxSearchJob?.cancel()
    }

    fun pullRefresh() {
        val userId = authRepository.currentUserId ?: return
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshThreads(userId)
            _isRefreshing.value = false
        }
    }

    private suspend fun refreshThreads(userId: String) {
        try {
            val page = messageRepository.listThreadsPage(userId, limit = pageSize)
            val left = messageRepository.recentlyLeftThreadIds()
            val refreshed = page.threads.filter { it.lastMessageFromUserId != null && it.id !in left }
            val merged = mergeRefreshedThreads(_threads.value, refreshed).filterNot { it.id in left }
            val tailEmpty = merged.size == refreshed.size
            _threads.value = merged
            if (tailEmpty) {
                // Everything loaded fits in the refreshed page, so its cursor is
                // the list's cursor. With a tail, the existing cursor still
                // points at the last fetched thread (the tail's end) and stays
                // valid.
                nextCursor = page.nextCursor
                _hasMoreThreads.value = page.hasMore && page.nextCursor != null
            }
        } catch (_: Exception) { }
    }

    fun loadMoreThreads() {
        val userId = authRepository.currentUserId ?: return
        if (_isLoadingMore.value || !_hasMoreThreads.value) return
        val cursor = nextCursor ?: return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = messageRepository.listThreadsPage(userId, limit = pageSize, startAfter = cursor)
                val existingIds = _threads.value.map { it.id }.toSet()
                val newThreads = page.threads
                    .filter { it.lastMessageFromUserId != null }
                    .filter { it.id !in existingIds }
                _threads.value = _threads.value + newThreads
                nextCursor = page.nextCursor
                // The recency cursor strictly decreases each page, so it can't loop;
                // stop when the server reports no more or the cursor didn't advance.
                _hasMoreThreads.value = page.hasMore && page.nextCursor != null && page.nextCursor != cursor
            } catch (_: Exception) {
                _hasMoreThreads.value = false
            }
            _isLoadingMore.value = false
        }
    }

    fun loadSuggestedContacts() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoadingSuggestions.value = true
            try {
                // Use recent DM contacts (same as share sheet)
                val threads = messageRepository.listThreads(userId)
                val contacts = threads.mapNotNull { it.otherUser }

                if (contacts.isNotEmpty()) {
                    _suggestedContacts.value = contacts
                } else {
                    // Fallback: following users (exclude bots)
                    userRepository.prefetchFollowingSet(userId)
                    val followIds = userRepository.followingIds.value
                    val users = followIds.take(20).mapNotNull { uid ->
                        try { userRepository.fetchUserProfile(uid) } catch (_: Exception) { null }
                    }.filter { !it.isBot }
                    _suggestedContacts.value = users
                }
            } catch (_: Exception) {
                _suggestedContacts.value = emptyList()
            }
            _isLoadingSuggestions.value = false
        }
    }

    fun searchUsers(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(300)
            try {
                _searchResults.value = userRepository.searchUsers(query, limit = 15, includeFollowed = true)
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            }
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    /**
     * Debounced backend inbox search. Cancels any in-flight search, waits briefly
     * so we don't fire on every keystroke, then queries the full thread history
     * (not just the paged-in threads the screen's local filter can see).
     */
    fun searchInbox(query: String) {
        inboxSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _inboxSearchResults.value = null
            _isSearchingInbox.value = false
            return
        }
        // Drop stale results so the instant local filter shows for the new query
        // while the server answers.
        _inboxSearchResults.value = null
        _isSearchingInbox.value = true
        inboxSearchJob = viewModelScope.launch {
            delay(300)
            val userId = authRepository.currentUserId
            if (userId == null) {
                _isSearchingInbox.value = false
                return@launch
            }
            try {
                _inboxSearchResults.value = messageRepository.searchThreads(userId, trimmed)
                    .filter { it.lastMessageFromUserId != null }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Leave results null so the local filter stays visible on failure.
                _inboxSearchResults.value = null
            }
            _isSearchingInbox.value = false
        }
    }

    suspend fun getOrCreateThread(otherUserId: String): String {
        val userId = authRepository.currentUserId ?: throw IllegalStateException("Not signed in")
        return messageRepository.getOrCreateThread(userId, otherUserId)
    }
}
