package fm.corus.android.ui.screens.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.InboxSubscriptionRefused
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
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

/**
 * The rows the inbox is allowed to draw. Each is put to [mayShowThread], the
 * same rule that decides whether the conversation itself may be opened.
 *
 * The rule reads two sources for a block because each covers rows the other
 * cannot see here: the row's own stamp reaches this client only through the live
 * listener's window, while the device's block set covers every row whatever path
 * it arrived by — the paginated tail below that window, and the reopen cache
 * that re-seeds it.
 */
internal fun visibleInboxRows(
    threads: List<CymbalThread>,
    blockedIds: Set<String>,
    isBanned: (String) -> Boolean,
): List<CymbalThread> = threads.filter { mayShowThread(it, blockedIds, isBanned) }

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
            // A thread's last-message time only moves forward in normal use, so a
            // strictly-older live snapshot is a stale replica — typically
            // Firestore's offline cache replaying the row on attach before it
            // learns about a message sent on another device. Don't let it
            // overwrite/reorder the fresher data the callable already loaded;
            // doing so regressed the inbox order on cold open until a refresh.
            if (lt.lastMessageAt.time < ex.lastMessageAt.time) continue
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
                blocked = lt.blocked,
                updatedAt = lt.updatedAt,
            )
        } else {
            newThreads.add(lt)
        }
    }
    // Prune threads that vanished from the live window — left, removed by someone
    // else, or deleted on another device. The window is the newest `pageSize`
    // rows by `updatedAt`, so that is the only dimension it speaks for: a row is
    // covered when its own `updatedAt` reaches the window's floor. Reading an old
    // conversation bumps `updatedAt` alone, which pulls it into the window still
    // carrying its ancient last-message time — measuring the floor in that
    // dimension instead would collapse it and delete every newer row below.
    // Rows the callable loaded carry no `updatedAt` and are outside the window's
    // word entirely. A short snapshot is the exception: it holds every thread the
    // user has, so anything missing from it is gone whatever its timestamps say.
    val liveIds = live.map { it.id }.toSet()
    val snapshotComplete = live.size < pageSize
    val windowFloor = live.mapNotNull { it.updatedAt?.time }.minOrNull()
    if (live.isNotEmpty()) {
        val kept = byId.filterValues { t ->
            val updated = t.updatedAt?.time
            val covered = snapshotComplete ||
                (windowFloor != null && updated != null && updated >= windowFloor)
            liveIds.contains(t.id) || !covered
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

    /**
     * Last-rendered inbox from the singleton repository, scoped to the current
     * user. Non-null when reopening Messages within the same process: the
     * ViewModel seeds its threads, cursor and "loaded" flags from it so the very
     * first composed frame is already the list, with resolved profiles the live
     * mirror docs don't carry. Null after a process restart or a user switch,
     * where the live listener's first cached snapshot takes over instead.
     */
    private val seededInbox: MessageRepository.CachedInbox? =
        messageRepository.cachedInbox?.takeIf { it.userId == authRepository.currentUserId }

    /**
     * Every row the screen draws passes through here, so a row the inbox may not
     * show cannot reach it from any source — the reopen cache, the live snapshot,
     * the paginated callable or search — and a row that becomes unshowable while
     * loaded is dropped by the next publish rather than lingering.
     */
    private fun visible(threads: List<CymbalThread>): List<CymbalThread> =
        visibleInboxRows(threads, userRepository.blockedIds.value) {
            userRepository.isUserBannedLocally(it)
        }

    private val _threads = MutableStateFlow(visible(seededInbox?.threads ?: emptyList()))
    val threads: StateFlow<List<CymbalThread>> = _threads.asStateFlow()

    /**
     * Counts publishes rather than compares lists, so a publish that restates the
     * same rows still registers: it is the act of publishing that says another
     * source has spoken since, which is what the live listener needs to know
     * before it overwrites the list with a decision it made earlier.
     */
    private var publishCount = 0L

    private fun publishThreads(threads: List<CymbalThread>) {
        publishCount++
        _threads.value = visible(threads)
    }

    // Resolved member profiles for group rows (stacked avatars + titles). The
    // live mirror carries only memberIds, so missing profiles are fetched here.
    private val _groupMembersById = MutableStateFlow<Map<String, CymbalUser>>(emptyMap())
    val groupMembersById: StateFlow<Map<String, CymbalUser>> = _groupMembersById.asStateFlow()

    init {
        // Drop a group from the inbox the instant the user leaves it.
        viewModelScope.launch {
            messageRepository.leftThreads.collect { id ->
                publishThreads(_threads.value.filterNot { it.id == id })
            }
        }
        // Re-test the list whenever the block set changes. Blocking someone is
        // the moment their row must go, and for a row outside the live window
        // that is the only word that will ever arrive.
        viewModelScope.launch {
            userRepository.blockedIds.collect { publishThreads(_threads.value) }
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

    // Permission to show the skeleton, not a claim that anything is pending: the
    // screen draws it only while the list is also still empty. Seeding from the
    // reopen cache withholds it outright; otherwise the live listener normally
    // fills the list from its cache first and the skeleton never gets a frame.
    private val _isLoading = MutableStateFlow(seededInbox == null)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMoreThreads = MutableStateFlow(seededInbox?.hasMore ?: false)
    val hasMoreThreads: StateFlow<Boolean> = _hasMoreThreads.asStateFlow()

    private val pageSize = 30

    /** Cursor for the next page — the `updatedAt` of the last thread fetched, in millis. */
    private var nextCursor: Long? = seededInbox?.nextCursor

    /**
     * Whether the callable has ever answered for this list. Its only remaining
     * job is to decide whether the skeleton is allowed while the list is empty;
     * both paths reconcile identically. Starts true when seeded from the
     * cross-instance inbox cache, whose contents came from a previous answer.
     */
    private var hasLoadedThreads = seededInbox != null

    init {
        // Mirror the live list into the singleton repository so the next reopen
        // can seed from it instantly. Keyed by userId so a different user never
        // inherits it. Only writes once a real load has populated the list.
        viewModelScope.launch {
            _threads.collect { list ->
                if (!hasLoadedThreads) return@collect
                val uid = authRepository.currentUserId ?: return@collect
                messageRepository.cacheInbox(
                    MessageRepository.CachedInbox(uid, list, nextCursor, _hasMoreThreads.value)
                )
            }
        }
    }

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

    /**
     * The inbox's live source, subscribed while the ViewModel is being built so
     * nothing on the presentation path waits on a network call: Firestore raises
     * the first snapshot from its on-disk cache in milliseconds, which is what
     * the screen renders. Attaching it here rather than inside [loadThreads] is
     * also what makes "the list ends up with no live listener" unreachable — no
     * callable outcome can skip it.
     */
    private var threadSummaryJob: Job? =
        authRepository.currentUserId?.let { startThreadSummaryListener(it) }

    // Inbox search. The local filter in the screen only sees paged-in threads, so
    // a debounced backend `searchThreads` backfills matches from the full history.
    // Null = no backend answer yet for the current query (fall back to local filter).
    private val _inboxSearchResults = MutableStateFlow<List<CymbalThread>?>(null)
    val inboxSearchResults: StateFlow<List<CymbalThread>?> = _inboxSearchResults.asStateFlow()

    private val _isSearchingInbox = MutableStateFlow(false)
    val isSearchingInbox: StateFlow<Boolean> = _isSearchingInbox.asStateFlow()

    private var inboxSearchJob: Job? = null

    /**
     * Reconciles the inbox against the paginated callable. This is never what
     * the screen waits on — the live listener is already running and already
     * rendering — so the only difference between a cold open and a re-entry is
     * whether the skeleton is allowed while the list is still empty.
     */
    fun loadThreads() {
        val userId = authRepository.currentUserId ?: return
        if (threadSummaryJob == null) {
            threadSummaryJob = startThreadSummaryListener(userId)
        }
        if (hasLoadedThreads) {
            viewModelScope.launch { refreshThreads(userId) }
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            refreshThreads(userId)
            // An empty page is a real inbox for a new account — don't wait
            // on a live snapshot that may never confirm "zero" from cache.
            _isLoading.value = false
        }
    }

    /**
     * Keep the first page of the inbox live: merge a real-time snapshot of the
     * caller's threads so previews/timestamps/unread update without leaving the
     * screen. The paginated callable still loads older threads on scroll.
     * Profiles for threads not yet in the list — every thread on a cold open,
     * or someone messaging you for the first time while you sit here — are
     * resolved in one batched read and folded in.
     */
    private fun startThreadSummaryListener(userId: String): Job {
        return viewModelScope.launch {
            messageRepository.listenToThreadSummaries(userId, pageSize.toLong()).retryWhen { cause, attempt ->
                // Re-subscribe after a dropped listener, backing off to 30s. A
                // refusal is not a drop — nothing about attaching again changes
                // the answer, so retrying it is a wakeup every few seconds behind
                // a screen nobody is looking at, forever.
                if (cause is InboxSubscriptionRefused) return@retryWhen false
                delay(minOf(30_000L, 1_000L shl attempt.coerceAtMost(5L).toInt()))
                true
            }.catch { cause ->
                // Ending the subscription is the whole response: there is no inbox
                // to keep live for a caller who may no longer read it, and letting
                // it out of the collector would take the process with it.
                if (cause !is InboxSubscriptionRefused) throw cause
            }.collect { liveRaw ->
                // Drop threads the user just left before merging: a stale cache
                // snapshot can still carry their mirror doc, and the async profile
                // resolution below would otherwise re-add them after they're gone.
                val left = messageRepository.recentlyLeftThreadIds()
                val live = if (left.isEmpty()) liveRaw else liveRaw.filterNot { it.id in left }
                val (merged, newRows) = applyLiveThreadUpdates(_threads.value, live, pageSize)
                val mergedFiltered = if (left.isEmpty()) merged else merged.filterNot { it.id in left }
                // Dropped before resolution, so a row the inbox may not draw
                // never costs a profile read on its way to being discarded.
                val newThreads = visible(newRows)

                // The merge can transiently drop everything: a partial snapshot whose
                // only threads are brand-new (still awaiting the profile resolution
                // below) prunes the known rows, leaving nothing yet to show. Publishing
                // that empty list blinks the inbox to "No messages yet" and poisons the
                // reopen cache. Hold the current rows in that case and publish once,
                // after resolution — the final state is identical, minus the flash.
                val deferEmptyPublish = mergedFiltered.isEmpty() && newThreads.isNotEmpty()
                if (!deferEmptyPublish) {
                    publishThreads(mergedFiltered)
                }
                if (newThreads.isEmpty()) return@collect
                val publishesBeforeResolution = publishCount
                // One batched, cache-first read for every unresolved profile in
                // the snapshot. Resolving them one at a time put a serial round
                // trip per row between a cached snapshot and the rows it can
                // draw, which on a cold open is the whole inbox.
                val profiles = runCatching {
                    userRepository.fetchUsersByIdsBatched(
                        newThreads.flatMap { lt ->
                            if (lt.isGroup) lt.memberIds else listOf(lt.otherUserId)
                        }.filter { it.isNotBlank() && it != currentUserId }.distinct()
                    )
                }.getOrDefault(emptyList()).associateBy { it.id }
                val resolved = newThreads.mapNotNull { lt ->
                    if (lt.isGroup) {
                        lt.copy(members = lt.memberIds.mapNotNull { profiles[it] })
                    } else {
                        profiles[lt.otherUserId]?.let { lt.copy(otherUser = it) }
                    }
                }
                if (resolved.isEmpty() && !deferEmptyPublish) return@collect
                // A deferred publish is still holding rows this snapshot decided to
                // prune, so it publishes the snapshot's own result — unless another
                // source published while the profiles were in flight, in which case
                // that is the newer word on what the inbox holds and the resolved
                // rows join it instead of rolling it back. resolved goes last so it
                // wins over any stale duplicate.
                val base = if (deferEmptyPublish && publishCount == publishesBeforeResolution) {
                    mergedFiltered
                } else {
                    _threads.value
                }
                publishThreads(
                    (base + resolved)
                        .associateBy { it.id }
                        .values
                        .sortedByDescending { it.lastMessageAt.time }
                )
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
            publishThreads(merged)
            hasLoadedThreads = true
            // The cursor belongs to whatever is oldest in the list. Rows older
            // than the refreshed page came from pagination, and the existing
            // cursor (the end of that tail) still points past them. Rows the
            // page simply doesn't carry but that fall inside its recency window
            // — the live listener publishes those before the callable answers —
            // are not a tail, so the page's cursor is still the list's cursor.
            val pageOldest = refreshed.minOfOrNull { it.lastMessageAt.time }
            val hasOlderTail = pageOldest != null && merged.any { it.lastMessageAt.time < pageOldest }
            if (!hasOlderTail) {
                nextCursor = page.nextCursor
                _hasMoreThreads.value = page.hasMore && page.nextCursor != null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // The live listener is the inbox's source of truth and is already
            // attached, so a failed reconcile costs pagination state, not the
            // list. Pull-to-refresh retries it.
        }
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
                publishThreads(_threads.value + newThreads)
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
                _inboxSearchResults.value = visible(
                    messageRepository.searchThreads(userId, trimmed)
                        .filter { it.lastMessageFromUserId != null }
                )
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
