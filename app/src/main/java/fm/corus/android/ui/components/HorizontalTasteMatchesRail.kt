package fm.corus.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.horizontalRailCardWidth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Horizontally-scrolling, paginated rail of taste matches (people who share your
 * music/film taste). Backed by [UserRepository.getTasteMatchesPage], so it pages
 * through your *full* strength-ranked list as you scroll rather than a capped
 * top-15 preview.
 *
 * Mirrors [HorizontalPopularUsersRail]: same card, same header + filter toggle,
 * same prefetch. The "Unfollowed" filter narrows to people you don't follow yet;
 * because the backing query returns followed + unfollowed together, we filter
 * client-side and keep paging until unfollowed matches surface (capped so we
 * never loop the whole list).
 *
 * Following someone must not reflow the rail: the order comes from the backend
 * ranking and is never re-derived on follow — only the card's button flips and,
 * under the "Unfollowed" filter, the followed card drops out.
 */
@Composable
fun HorizontalTasteMatchesRail(
    // Pass the followed-id set (not a lambda) so this rail recomposes when the
    // viewer follows/unfollows someone — matches the Popular rail rationale.
    followedIds: Set<String>,
    onUserTap: (SuggestedUserMatch) -> Unit,
    onFollowTap: (fm.corus.android.data.model.CymbalUser) -> Unit,
    modifier: Modifier = Modifier,
    // Off = show every match (default). On = only people you don't follow yet.
    filterUnfollowed: Boolean = false,
    /** Clears the "Unfollowed" filter back to "All". Wired to the followed-all
     *  empty state's "Show all" action; mirrors the iOS rail. */
    onClearFilter: () -> Unit = {},
    onSeeAll: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    viewModel: TasteMatchesRailViewModel = hiltViewModel(),
) {
    val matches by viewModel.matches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val endReached by viewModel.endReached.collectAsState()
    val isFilling by viewModel.isFilling.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadInitial()
    }

    // The subset the rail actually renders. Under the "Unfollowed" filter we drop
    // people the viewer already follows; otherwise show everything loaded.
    val visibleMatches = remember(matches, filterUnfollowed, followedIds) {
        if (!filterUnfollowed) matches
        else matches.filter { it.user.id !in followedIds }
    }

    // When the toggle flips to "Unfollowed", set isFilling synchronously (via the
    // ViewModel) so the skeleton — not the empty state — shows this frame, then
    // auto-page until unfollowed matches surface (or we run out).
    LaunchedEffect(filterUnfollowed) {
        if (filterUnfollowed && !endReached) {
            viewModel.markFilling()
        }
        viewModel.fillIfNeeded(
            shouldFilter = { filterUnfollowed },
            isVisibleEmpty = {
                filterUnfollowed && viewModel.matches.value.none { it.user.id !in followedIds }
            },
        )
    }

    val cardWidth = horizontalRailCardWidth()

    val listState = rememberLazyListState()
    LaunchedEffect(listState, endReached, isLoading) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (!endReached && !isLoading && total > 0 && lastVisible >= total - 3) {
                    viewModel.loadMore()
                    viewModel.fillIfNeeded(
                        shouldFilter = { filterUnfollowed },
                        isVisibleEmpty = {
                            filterUnfollowed && viewModel.matches.value.none { it.user.id !in followedIds }
                        },
                    )
                }
            }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        fm.corus.android.ui.screens.search.SectionHeader(
            icon = "sparkles",
            title = "TASTE MATCHES",
            showSeeAll = onSeeAll != null,
            onSeeAll = onSeeAll ?: {},
            trailingAction = trailingAction,
        )

        if (matches.isEmpty() && isLoading) {
            SkeletonRow(cardWidth = cardWidth)
        } else if (visibleMatches.isNotEmpty()) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                items(visibleMatches, key = { it.user.id }) { match ->
                    TasteMatchCard(
                        match = match,
                        isFollowing = match.user.id in followedIds,
                        onUserTap = { onUserTap(match) },
                        onFollowTap = { onFollowTap(match.user) },
                        modifier = Modifier.width(cardWidth),
                    )
                }
                if (isLoading) {
                    item("loading") {
                        Box(
                            modifier = Modifier.size(width = 48.dp, height = cardWidth),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = CorusColors.Accent,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        } else if (filterUnfollowed && isFilling) {
            // Still paging through followed matches to surface unfollowed ones.
            SkeletonRow(cardWidth = cardWidth)
        } else if (filterUnfollowed && matches.isNotEmpty()) {
            // Exhausted the list and you follow all of your matches.
            FollowedAllState(onShowAll = onClearFilter)
        }
    }
}

@Composable
private fun FollowedAllState(onShowAll: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // No em dashes in user-facing copy (repo rule).
            text = "You've followed all your taste matches!",
            style = CorusFont.body,
            color = CorusColors.Tertiary,
        )
        Text(
            text = "Show all",
            style = CorusFont.captionMedium,
            color = CorusColors.Accent,
            modifier = Modifier.clickable(onClick = onShowAll),
        )
    }
}

@Composable
private fun SkeletonRow(cardWidth: androidx.compose.ui.unit.Dp) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        items(4) {
            SkeletonTasteMatchCard(modifier = Modifier.width(cardWidth))
        }
    }
}

// ── ViewModel ──

@HiltViewModel
class TasteMatchesRailViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _matches = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val matches: StateFlow<List<SuggestedUserMatch>> = _matches.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _endReached = MutableStateFlow(false)
    val endReached: StateFlow<Boolean> = _endReached.asStateFlow()

    /** True while auto-paging to find unfollowed matches (drives the skeleton so
     *  the empty state doesn't flash before the pages arrive). */
    private val _isFilling = MutableStateFlow(false)
    val isFilling: StateFlow<Boolean> = _isFilling.asStateFlow()

    private var cursor: String? = null
    private val loadedIds = mutableSetOf<String>()
    private var hasLoadedInitial = false
    private val pageSize = 15

    /** True once the user has scrolled past the first page. A background refresh
     *  only reconciles the visible list when this is false, so we never yank
     *  content out from under an active scroll. */
    private var pagedBeyondFirst = false

    /** Set when the most recent [fetchPage] threw (as opposed to returning an
     *  empty page). Lets the cold-start path unlatch and retry rather than
     *  latching an empty rail after a transient failure. */
    private var loadFailed = false

    fun loadInitial() {
        if (hasLoadedInitial) return
        hasLoadedInitial = true
        viewModelScope.launch {
            // Stale-while-revalidate: paint the cached first page instantly (no
            // skeleton), then refresh in the background. Only a genuine cold start
            // (no usable cache) shows the shimmer.
            val cached = userRepository.getCachedTasteMatchesFirstPage()
            if (cached != null) {
                applyFirstPage(cached.matches, cached.nextCursor, cached.hasMore)
                // Skip the refresh while the cache is still fresh so rapid re-opens
                // of Search don't re-hit the callable.
                if (!userRepository.isTasteMatchesCacheFresh()) {
                    revalidateFirstPage()
                }
                return@launch
            }

            fetchPage()
            // A failed first page with nothing loaded shouldn't latch: unlatch so
            // the next appearance silently retries instead of showing an empty rail.
            if (loadFailed && _matches.value.isEmpty()) {
                hasLoadedInitial = false
            }
        }
    }

    /** Resets the visible list to exactly [pageMatches] (the first page). Used to
     *  paint the cache and to reconcile after a background refresh. */
    private fun applyFirstPage(pageMatches: List<SuggestedUserMatch>, nextCursor: String?, hasMore: Boolean) {
        loadedIds.clear()
        loadedIds.addAll(pageMatches.map { it.user.id })
        _matches.value = pageMatches
        cursor = nextCursor
        _isLoading.value = false
        _endReached.value = !hasMore || nextCursor == null
        pagedBeyondFirst = false
    }

    /**
     * Background refresh of the first page while cached matches are already on
     * screen. Never shows the skeleton (matches is non-empty) and never wipes the
     * list on failure — a bad refresh just leaves the cached list in place. An
     * empty refresh is treated as "no update" (cached matches beat an abrupt
     * empty; a real lasting loss is caught when the cache ages past MAX_AGE and a
     * cold fetch returns empty).
     */
    private suspend fun revalidateFirstPage() {
        val page = runCatching {
            userRepository.getTasteMatchesPage(cursor = null, limit = pageSize)
        }.getOrElse {
            // Keep the cached matches on error; nothing else to do (no loadFailed
            // flag surfaced to the UI here — the cached list stays put).
            return
        }
        if (page.matches.isEmpty()) return
        userRepository.cacheTasteMatchesFirstPage(page)
        // Reconcile the visible list only if the user hasn't paged deeper;
        // otherwise the cache write above is enough and their scrolled content is
        // left untouched.
        if (!pagedBeyondFirst) {
            applyFirstPage(page.matches, page.nextCursor, page.hasMore)
        }
    }

    /** Test-only hook to exercise [revalidateFirstPage] without the fresh-window
     *  gate in [loadInitial]. */
    @androidx.annotation.VisibleForTesting
    internal suspend fun revalidateFirstPageForTest() = revalidateFirstPage()

    fun loadMore() {
        if (_isLoading.value || _endReached.value) return
        viewModelScope.launch { fetchPage() }
    }

    /** Flip the filling flag on synchronously (from the filter's onChange) so the
     *  skeleton renders the same frame the toggle flips. */
    fun markFilling() {
        if (!_endReached.value) _isFilling.value = true
    }

    /**
     * When filtering to unfollowed, a whole page can be people you already
     * follow, leaving nothing visible. Keep paging until some unfollowed matches
     * show or we run out (capped at 10 pages so we never loop the whole list).
     *
     * [shouldFilter] reports whether the "Unfollowed" filter is currently on and
     * [isVisibleEmpty] whether nothing is visible under it; both are re-read each
     * loop so a toggle-off mid-fill stops the paging.
     */
    suspend fun fillIfNeeded(shouldFilter: () -> Boolean, isVisibleEmpty: () -> Boolean) {
        try {
            if (!shouldFilter() || !isVisibleEmpty() || _endReached.value) return
            _isFilling.value = true
            var pagesLoaded = 0
            while (shouldFilter() && isVisibleEmpty() && !_endReached.value && !_isLoading.value && pagesLoaded < 10) {
                pagesLoaded++
                fetchPage()
            }
        } finally {
            _isFilling.value = false
        }
    }

    private suspend fun fetchPage() {
        // Capture BEFORE mutating cursor below so we know whether to cache this as
        // the first page or mark that we've paged beyond it.
        val isFirstPage = cursor == null
        _isLoading.value = true
        try {
            val page = runCatching {
                userRepository.getTasteMatchesPage(cursor = cursor, limit = pageSize)
            }.getOrElse {
                loadFailed = true
                _endReached.value = true
                return
            }
            loadFailed = false
            if (page.matches.isEmpty()) {
                _endReached.value = true
                return
            }
            cursor = page.nextCursor
            val appended = page.matches.filter { loadedIds.add(it.user.id) }
            if (appended.isNotEmpty()) {
                _matches.value = _matches.value + appended
            }
            if (!page.hasMore || page.nextCursor == null) {
                _endReached.value = true
            }
            if (isFirstPage) {
                // Persist the first page for instant paint on the next open.
                userRepository.cacheTasteMatchesFirstPage(page)
            } else {
                pagedBeyondFirst = true
            }
        } finally {
            _isLoading.value = false
        }
    }
}
