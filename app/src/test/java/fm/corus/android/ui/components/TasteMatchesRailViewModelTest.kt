package fm.corus.android.ui.components

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.TasteMatchesPage
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the paginated Taste Matches rail view model — the core of the
 * iOS-parity port. Covers: default (all matches), the client-side "Unfollowed"
 * filter auto-paging until an unfollowed match surfaces, the [isFilling] flag
 * lifecycle, the followed-all state, and the 10-page fill cap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TasteMatchesRailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userRepository: UserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun match(id: String): SuggestedUserMatch =
        SuggestedUserMatch(
            user = CymbalUser(id = id, username = id, displayName = id),
        )

    private fun page(ids: List<String>, cursor: String?, hasMore: Boolean) =
        TasteMatchesPage(
            matches = ids.map { match(it) },
            nextCursor = cursor,
            hasMore = hasMore,
        )

    private fun vm() = TasteMatchesRailViewModel(userRepository)

    /** Everything currently loaded that the "Unfollowed" filter would show. */
    private fun visibleUnfollowedEmpty(vm: TasteMatchesRailViewModel, followed: Set<String>): Boolean =
        vm.matches.value.none { it.user.id !in followed }

    @Test
    fun `default loads the full first page unfiltered`() = runTest(testDispatcher) {
        whenever(userRepository.getTasteMatchesPage(anyOrNull(), any()))
            .thenReturn(page(listOf("a", "b", "c"), cursor = null, hasMore = false))

        val vm = vm()
        vm.loadInitial()
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), vm.matches.value.map { it.user.id })
        assertFalse(vm.isFilling.value)
        assertTrue(vm.endReached.value)
    }

    @Test
    fun `filtering to unfollowed auto-pages until an unfollowed match surfaces`() =
        runTest(testDispatcher) {
            // Page 1: all followed. Page 2: contains an unfollowed match.
            whenever(userRepository.getTasteMatchesPage(cursor = null, limit = 15))
                .thenReturn(page(listOf("f1", "f2"), cursor = "c1", hasMore = true))
            whenever(userRepository.getTasteMatchesPage(cursor = "c1", limit = 15))
                .thenReturn(page(listOf("f3", "u1"), cursor = null, hasMore = false))

            val followed = setOf("f1", "f2", "f3")
            val vm = vm()
            vm.loadInitial()
            advanceUntilIdle()

            // Only followed loaded so far — nothing visible under the filter.
            assertTrue(visibleUnfollowedEmpty(vm, followed))

            // Flip to "Unfollowed": mark filling synchronously, then fill.
            vm.markFilling()
            assertTrue(vm.isFilling.value)
            vm.fillIfNeeded(
                shouldFilter = { true },
                isVisibleEmpty = { visibleUnfollowedEmpty(vm, followed) },
            )
            advanceUntilIdle()

            // The unfollowed match from page 2 is now loaded and visible.
            assertTrue(vm.matches.value.any { it.user.id == "u1" })
            assertFalse(visibleUnfollowedEmpty(vm, followed))
            assertFalse(vm.isFilling.value)
        }

    @Test
    fun `isFilling is true while paging and false after`() = runTest(testDispatcher) {
        whenever(userRepository.getTasteMatchesPage(cursor = null, limit = 15))
            .thenReturn(page(listOf("f1"), cursor = "c1", hasMore = true))
        whenever(userRepository.getTasteMatchesPage(cursor = "c1", limit = 15))
            .thenReturn(page(listOf("u1"), cursor = null, hasMore = false))

        val followed = setOf("f1")
        val vm = vm()
        vm.loadInitial()
        advanceUntilIdle()

        vm.markFilling()
        assertTrue("filling flips true synchronously on toggle", vm.isFilling.value)

        vm.fillIfNeeded(
            shouldFilter = { true },
            isVisibleEmpty = { visibleUnfollowedEmpty(vm, followed) },
        )
        advanceUntilIdle()

        assertFalse("filling clears once an unfollowed match surfaces", vm.isFilling.value)
    }

    @Test
    fun `followed-all reaches end with everyone followed and clears filling`() =
        runTest(testDispatcher) {
            whenever(userRepository.getTasteMatchesPage(cursor = null, limit = 15))
                .thenReturn(page(listOf("f1", "f2"), cursor = null, hasMore = false))

            val followed = setOf("f1", "f2")
            val vm = vm()
            vm.loadInitial()
            advanceUntilIdle()

            vm.markFilling()
            // endReached was already set by the first page, so markFilling is a no-op.
            assertFalse(vm.isFilling.value)

            vm.fillIfNeeded(
                shouldFilter = { true },
                isVisibleEmpty = { visibleUnfollowedEmpty(vm, followed) },
            )
            advanceUntilIdle()

            // Nothing unfollowed to show, list exhausted → followed-all state.
            assertTrue(visibleUnfollowedEmpty(vm, followed))
            assertTrue(vm.endReached.value)
            assertFalse(vm.isFilling.value)
        }

    private fun cachedPage(ids: List<String>, cursor: String?, hasMore: Boolean, fetchedAt: Long) =
        UserRepository.CachedTasteMatchesPage(
            matches = ids.map { match(it) },
            nextCursor = cursor,
            hasMore = hasMore,
            fetchedAt = fetchedAt,
        )

    // ── Stale-while-revalidate first-page cache ──

    @Test
    fun `fresh cache paints instantly and does not revalidate`() = runTest(testDispatcher) {
        whenever(userRepository.getCachedTasteMatchesFirstPage())
            .thenReturn(cachedPage(listOf("a", "b", "c"), cursor = null, hasMore = false, fetchedAt = System.currentTimeMillis()))
        whenever(userRepository.isTasteMatchesCacheFresh()).thenReturn(true)

        val vm = vm()
        vm.loadInitial()
        advanceUntilIdle()

        // Painted from cache, no skeleton, list matches the cache.
        assertEquals(listOf("a", "b", "c"), vm.matches.value.map { it.user.id })
        assertFalse(vm.isLoading.value)
        assertTrue(vm.endReached.value)
        // Fresh → NO network fetch at all.
        verify(userRepository, never()).getTasteMatchesPage(anyOrNull(), any())
    }

    @Test
    fun `stale cache paints instantly and revalidates once`() = runTest(testDispatcher) {
        whenever(userRepository.getCachedTasteMatchesFirstPage())
            .thenReturn(cachedPage(listOf("a", "b"), cursor = "c1", hasMore = true, fetchedAt = 0L))
        whenever(userRepository.isTasteMatchesCacheFresh()).thenReturn(false)
        // Revalidation returns a refreshed first page.
        whenever(userRepository.getTasteMatchesPage(cursor = null, limit = 15))
            .thenReturn(page(listOf("a", "b", "x"), cursor = null, hasMore = false))

        val vm = vm()
        vm.loadInitial()
        advanceUntilIdle()

        // Reconciled to the refreshed first page.
        assertEquals(listOf("a", "b", "x"), vm.matches.value.map { it.user.id })
        // Revalidation hit the network exactly once, with a null (first-page) cursor.
        verify(userRepository, times(1)).getTasteMatchesPage(eq(null), eq(15))
    }

    @Test
    fun `no cache falls back to a cold fetch via fetchPage`() = runTest(testDispatcher) {
        whenever(userRepository.getCachedTasteMatchesFirstPage()).thenReturn(null)
        whenever(userRepository.getTasteMatchesPage(cursor = null, limit = 15))
            .thenReturn(page(listOf("a", "b"), cursor = null, hasMore = false))

        val vm = vm()
        vm.loadInitial()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.matches.value.map { it.user.id })
        assertTrue(vm.endReached.value)
        // Cold path caches the fetched first page.
        verify(userRepository, times(1)).cacheTasteMatchesFirstPage(any())
    }

    @Test
    fun `revalidation returning empty does not clear the painted cache`() = runTest(testDispatcher) {
        whenever(userRepository.getCachedTasteMatchesFirstPage())
            .thenReturn(cachedPage(listOf("a", "b"), cursor = null, hasMore = false, fetchedAt = 0L))
        whenever(userRepository.isTasteMatchesCacheFresh()).thenReturn(false)
        // A transient empty refresh must be treated as "no update".
        whenever(userRepository.getTasteMatchesPage(cursor = null, limit = 15))
            .thenReturn(page(emptyList(), cursor = null, hasMore = false))

        val vm = vm()
        vm.loadInitial()
        advanceUntilIdle()

        // Cached matches survive the empty refresh.
        assertEquals(listOf("a", "b"), vm.matches.value.map { it.user.id })
        // And the empty page is never written back over the good cache.
        verify(userRepository, never()).cacheTasteMatchesFirstPage(any())
    }

    @Test
    fun `revalidation does not reset the list after paging beyond the first page`() =
        runTest(testDispatcher) {
            // No cache → cold fetch of page 1, then the user scrolls to page 2.
            whenever(userRepository.getCachedTasteMatchesFirstPage()).thenReturn(null)
            whenever(userRepository.getTasteMatchesPage(cursor = null, limit = 15))
                .thenReturn(page(listOf("a", "b"), cursor = "c1", hasMore = true))
            whenever(userRepository.getTasteMatchesPage(cursor = "c1", limit = 15))
                .thenReturn(page(listOf("c", "d"), cursor = null, hasMore = false))

            val vm = vm()
            vm.loadInitial()
            advanceUntilIdle()
            vm.loadMore() // page 2 → pagedBeyondFirst = true
            advanceUntilIdle()

            val scrolled = listOf("a", "b", "c", "d")
            assertEquals(scrolled, vm.matches.value.map { it.user.id })

            // Simulate a background revalidation now (as loadInitial would run it
            // for a stale cache): a fresh, shorter first page must NOT collapse the
            // scrolled list, since the user has paged beyond the first page.
            whenever(userRepository.getTasteMatchesPage(cursor = null, limit = 15))
                .thenReturn(page(listOf("a"), cursor = null, hasMore = false))
            vm.revalidateFirstPageForTest()
            advanceUntilIdle()

            assertEquals(
                "scrolled list is preserved after a background refresh",
                scrolled,
                vm.matches.value.map { it.user.id },
            )
        }

    @Test
    fun `fill stops at the 10-page cap without looping the whole list`() =
        runTest(testDispatcher) {
            // Every page is followed and there's always more — the cap must stop it.
            var call = 0
            whenever(userRepository.getTasteMatchesPage(anyOrNull(), any())).thenAnswer {
                val id = "f${call++}"
                page(listOf(id), cursor = "c$call", hasMore = true)
            }

            val vm = vm()
            vm.loadInitial()
            advanceUntilIdle()
            val afterInitial = call

            vm.markFilling()
            vm.fillIfNeeded(
                // Always filtering, never anything visible → would loop forever
                // without the cap.
                shouldFilter = { true },
                isVisibleEmpty = { true },
            )
            advanceUntilIdle()

            // Initial page + at most 10 fill pages.
            assertTrue("fill capped at 10 pages", call - afterInitial <= 10)
            assertFalse(vm.isFilling.value)
        }
}
