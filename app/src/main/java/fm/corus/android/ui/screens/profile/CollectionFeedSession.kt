package fm.corus.android.ui.screens.profile

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.domain.CollectionPlaybackPage
import fm.corus.android.domain.CollectionPlaybackQueue
import fm.corus.android.domain.PlaybackOrigin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Retains the gallery's exact order and cursor while its sheet is dismissed.
 * Full posts resolve on demand, without delaying navigation or refetching page one. */
internal class CollectionFeedSession(
    val profileId: String,
    val gifts: Boolean,
    initial: CollectionPage,
    private val fetchPost: suspend (String) -> CymbalPost?,
    private val fetchPage: suspend (Map<*, *>) -> CollectionPage,
    private val allowed: () -> Boolean,
    private val onPostEvent: (String) -> Unit = {},
) {
    private val _items = MutableStateFlow(initial.items.distinctBy { it.id })
    val items = _items.asStateFlow()
    private val _hasMore = MutableStateFlow(initial.cursor != null)
    val hasMore = _hasMore.asStateFlow()
    private val _failedIds = MutableStateFlow(emptySet<String>())
    val failedIds = _failedIds.asStateFlow()
    private val _pageFailed = MutableStateFlow(false)
    val pageFailed = _pageFailed.asStateFlow()
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()
    private var cursor = initial.cursor
    private val paging = Mutex()
    private val resolving = mutableMapOf<String, Mutex>()
    private val readyReported = mutableSetOf<String>()
    val segment get() = if (gifts) 6 else 5
    fun isAllowed() = allowed()
    fun remove(id: String) { _items.value = _items.value.filterNot { it.id == id } }
    fun updatePosts(transform: (List<CymbalPost>) -> List<CymbalPost>) {
        val updated = transform(_items.value.mapNotNull { it.post }).associateBy { it.id }
        _items.value = _items.value.map { item -> updated[item.id]?.let { item.copy(post = it) } ?: item }
    }

    suspend fun resolve(id: String, retry: Boolean = false): CymbalPost? = resolving.getOrPut(id) { Mutex() }.withLock {
        if (!allowed()) return@withLock null
        val item = _items.value.firstOrNull { it.id == id } ?: return@withLock null
        item.post?.let {
            if (readyReported.add(id)) onPostEvent("load_succeeded")
            return@withLock it
        }
        if (!retry && id in _failedIds.value) return@withLock null
        if (retry && id in _failedIds.value) onPostEvent("retry_tapped")
        _failedIds.value -= id
        try {
            val post = fetchPost(id)
            check(allowed())
            _items.value = if (post == null) _items.value.filterNot { it.id == id }
                else _items.value.map { if (it.id == id) it.copy(post = post) else it }
            if (post == null) onPostEvent("load_failed")
            else if (readyReported.add(id)) onPostEvent("load_succeeded")
            post
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            _failedIds.value += id
            if (allowed()) onPostEvent("load_failed")
            null
        }
    }

    suspend fun loadMore(retry: Boolean = false) = paging.withLock {
        if (!allowed() || (_pageFailed.value && !retry)) return@withLock
        val requested = cursor ?: return@withLock
        _loadingMore.value = true; _pageFailed.value = false
        try {
            val page = fetchPage(requested)
            check(allowed())
            _items.value = (_items.value + page.items).distinctBy { it.id }
            cursor = page.cursor?.takeUnless { it == requested }
            _hasMore.value = cursor != null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { _pageFailed.value = true }
        finally { _loadingMore.value = false }
    }

    val playbackQueue by lazy {
        fun page() = CollectionPlaybackPage(
            _items.value.filter { it.media == "track" }.map { it.id },
            _items.value.mapNotNull { it.post }, _hasMore.value)
        CollectionPlaybackQueue(
            PlaybackOrigin.ProfileCollection(profileId, UUID.randomUUID().toString()), page(),
            fetchPost = { resolve(it, retry = true) },
            fetchPage = {
                loadMore(retry = true)
                check(!_pageFailed.value) { "Collection pagination failed" }
                page()
            }, allowed = allowed,
        )
    }
}

/** The navigation callback runs only once the sheet's hide animation completes. */
internal suspend fun dismissCollectionThenNavigate(
    hide: suspend () -> Unit,
    isHidden: () -> Boolean,
    dismiss: () -> Unit,
    navigate: () -> Unit,
) {
    hide()
    if (isHidden()) { dismiss(); navigate() }
}
