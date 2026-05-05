package fm.corus.android.data.repository

import android.content.SharedPreferences
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionRepositoryTest {

    private lateinit var repo: SubscriptionRepository
    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val remoteConfig = mock<RemoteConfigService>()
    private val analyticsService = mock<AnalyticsService>()
    private val prefs = mock<SharedPreferences>()
    private val prefsEditor = mock<SharedPreferences.Editor>()

    @Before
    fun setUp() {
        whenever(remoteConfig.corusClubEnabled).thenReturn(true)
        whenever(prefs.getBoolean("cached_isClubMember", false)).thenReturn(false)
        whenever(prefs.getBoolean("cached_isVerified", false)).thenReturn(false)
        whenever(prefs.getInt("cached_dailyPostLimit", SubscriptionRepository.DEFAULT_DAILY_POST_LIMIT))
            .thenReturn(SubscriptionRepository.DEFAULT_DAILY_POST_LIMIT)
        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenReturn(prefsEditor)
        whenever(prefsEditor.putInt(any(), any())).thenReturn(prefsEditor)
        repo = SubscriptionRepository(cloudFunctions, remoteConfig, analyticsService, prefs)
    }

    // ── hasFullAccess ──

    @Test
    fun `hasFullAccess is false by default`() {
        assertFalse(repo.hasFullAccess)
    }

    @Test
    fun `hasFullAccess is true when verified`() {
        repo.updateVerifiedStatus(true)
        assertTrue(repo.hasFullAccess)
    }

    @Test
    fun `hasFullAccess reverts to false when verified removed`() {
        repo.updateVerifiedStatus(true)
        assertTrue(repo.hasFullAccess)

        repo.updateVerifiedStatus(false)
        assertFalse(repo.hasFullAccess)
    }

    // ── canPost ──

    @Test
    fun `canPost is true when under daily limit`() {
        assertTrue(repo.canPost)
    }

    @Test
    fun `canPost is true when verified regardless of post count`() {
        repo.updateVerifiedStatus(true)
        repeat(repo.dailyPostLimit.value) { repo.incrementPostCount() }
        assertTrue(repo.canPost)
    }

    @Test
    fun `canPost is false when at soft limit and not verified`() {
        repeat(repo.dailyPostLimit.value) { repo.incrementPostCount() }
        assertFalse(repo.canPost)
    }

    @Test
    fun `canPost is always true when club not enabled`() {
        whenever(remoteConfig.corusClubEnabled).thenReturn(false)
        repeat(repo.dailyPostLimit.value + 5) { repo.incrementPostCount() }
        assertTrue(repo.canPost)
    }

    @Test
    fun `canPost is false for verified users at the hard cap`() {
        repo.updateVerifiedStatus(true)
        repeat(SubscriptionRepository.DAILY_POST_LIMIT_HARD) { repo.incrementPostCount() }
        assertFalse(repo.canPost)
        assertTrue(repo.isHardCapped)
    }

    @Test
    fun `isHardCapped is false below the hard cap`() {
        repeat(SubscriptionRepository.DAILY_POST_LIMIT_HARD - 1) { repo.incrementPostCount() }
        assertFalse(repo.isHardCapped)
    }

    // ── postCountLoaded / server refresh ──

    @Test
    fun `postCountLoaded is false before refresh`() {
        assertFalse(repo.postCountLoaded.value)
    }

    @Test
    fun `refreshPostLimit populates recentPostCount and marks loaded`() = runTest {
        whenever(cloudFunctions.checkCanPost()).thenReturn(
            CloudFunctionsDataSource.CheckCanPostResult(canPost = false, recentCount = 3, dailyLimit = 3)
        )

        repo.refreshPostLimit()

        assertEquals(3, repo.recentPostCount.value)
        assertTrue(repo.postCountLoaded.value)
        assertFalse(repo.canPost)
    }

    @Test
    fun `refreshPostLimit adopts server dailyLimit and persists it`() = runTest {
        whenever(cloudFunctions.checkCanPost()).thenReturn(
            CloudFunctionsDataSource.CheckCanPostResult(canPost = true, recentCount = 0, dailyLimit = 5)
        )

        repo.refreshPostLimit()

        assertEquals(5, repo.dailyPostLimit.value)
        verify(prefsEditor).putInt("cached_dailyPostLimit", 5)
    }

    @Test
    fun `canPost respects server-driven limit`() = runTest {
        whenever(cloudFunctions.checkCanPost()).thenReturn(
            CloudFunctionsDataSource.CheckCanPostResult(canPost = true, recentCount = 0, dailyLimit = 5)
        )
        repo.refreshPostLimit()

        // 4 posts: under the new server limit of 5 -> still allowed.
        repeat(4) { repo.incrementPostCount() }
        assertTrue(repo.canPost)

        // 5th post hits the limit.
        repo.incrementPostCount()
        assertFalse(repo.canPost)
    }

    @Test
    fun `canPost falls back to default 3 when server has not responded`() {
        // Default cache returns DEFAULT_DAILY_POST_LIMIT (3) — no refresh has happened.
        repeat(3) { repo.incrementPostCount() }
        assertFalse(repo.canPost)
    }

    @Test
    fun `null or non-positive dailyLimit from server is ignored`() = runTest {
        whenever(cloudFunctions.checkCanPost()).thenReturn(
            CloudFunctionsDataSource.CheckCanPostResult(canPost = true, recentCount = 0, dailyLimit = null)
        )

        val before = repo.dailyPostLimit.value
        repo.refreshPostLimit()
        assertEquals(before, repo.dailyPostLimit.value)

        whenever(cloudFunctions.checkCanPost()).thenReturn(
            CloudFunctionsDataSource.CheckCanPostResult(canPost = true, recentCount = 0, dailyLimit = 0)
        )
        repo.refreshPostLimit()
        assertEquals(before, repo.dailyPostLimit.value)
    }

    @Test
    fun `refreshPostLimit silently swallows errors and leaves postCountLoaded false`() = runTest {
        whenever(cloudFunctions.checkCanPost()).thenThrow(RuntimeException("network down"))

        repo.refreshPostLimit()

        assertFalse(repo.postCountLoaded.value)
    }

    @Test
    fun `checkCanPostFromServer returns server value and caches count`() = runTest {
        whenever(cloudFunctions.checkCanPost()).thenReturn(
            CloudFunctionsDataSource.CheckCanPostResult(canPost = false, recentCount = 4, dailyLimit = 3)
        )

        val allowed = repo.checkCanPostFromServer()

        assertFalse(allowed)
        assertEquals(4, repo.recentPostCount.value)
        assertTrue(repo.postCountLoaded.value)
    }

    @Test
    fun `checkCanPostFromServer fails open when the callable throws`() = runTest {
        whenever(cloudFunctions.checkCanPost()).thenThrow(RuntimeException("boom"))

        // Fail-open so a flaky network can't block posting — the server-side trigger is the safety net.
        assertTrue(repo.checkCanPostFromServer())
        assertFalse(repo.postCountLoaded.value)
    }

    // ── refreshPostLimitIfNeeded (foreground throttle) ──

    @Test
    fun `refreshPostLimitIfNeeded skips for verified users`() = runTest {
        repo.updateVerifiedStatus(true)

        repo.refreshPostLimitIfNeeded()

        // Verified users bypass the soft limit entirely — no point hitting the server.
        verify(cloudFunctions, org.mockito.kotlin.never()).checkCanPost()
    }

    @Test
    fun `refreshPostLimitIfNeeded calls server for free users on first foreground`() = runTest {
        whenever(cloudFunctions.checkCanPost()).thenReturn(
            CloudFunctionsDataSource.CheckCanPostResult(canPost = true, recentCount = 1, dailyLimit = 3)
        )

        repo.refreshPostLimitIfNeeded()

        verify(cloudFunctions).checkCanPost()
        assertEquals(1, repo.recentPostCount.value)
    }

    @Test
    fun `refreshPostLimitIfNeeded throttles repeat calls within the window`() = runTest {
        whenever(cloudFunctions.checkCanPost()).thenReturn(
            CloudFunctionsDataSource.CheckCanPostResult(canPost = true, recentCount = 0, dailyLimit = 3)
        )

        // First call lands; subsequent calls inside the throttle window are dropped
        // so rapid background/foreground flips don't spam checkCanPost.
        repo.refreshPostLimitIfNeeded(now = 1_000_000L)
        repo.refreshPostLimitIfNeeded(now = 1_000_000L + SubscriptionRepository.POST_LIMIT_REFRESH_THROTTLE_MS - 1)

        verify(cloudFunctions, org.mockito.kotlin.times(1)).checkCanPost()
    }

    @Test
    fun `refreshPostLimitIfNeeded refreshes again once the throttle window elapses`() = runTest {
        whenever(cloudFunctions.checkCanPost()).thenReturn(
            CloudFunctionsDataSource.CheckCanPostResult(canPost = true, recentCount = 0, dailyLimit = 3)
        )

        repo.refreshPostLimitIfNeeded(now = 1_000_000L)
        repo.refreshPostLimitIfNeeded(now = 1_000_000L + SubscriptionRepository.POST_LIMIT_REFRESH_THROTTLE_MS + 1)

        verify(cloudFunctions, org.mockito.kotlin.times(2)).checkCanPost()
    }

    // ── decrementPostCount (post-deletion) ──

    @Test
    fun `decrementPostCount frees a slot when the deleted post is within the 24h window`() {
        // Simulate the bug repro: 3 posts in 24h, hitting the soft limit.
        repeat(3) { repo.incrementPostCount() }
        assertFalse(repo.canPost)

        // Delete one that was created an hour ago — should free a slot immediately
        // without needing an app restart or a server round trip.
        val oneHourAgo = java.util.Date(System.currentTimeMillis() - 60L * 60L * 1000L)
        repo.decrementPostCount(oneHourAgo)

        assertEquals(2, repo.recentPostCount.value)
        assertTrue(repo.canPost)
    }

    @Test
    fun `decrementPostCount does not touch recentPostCount for posts outside the window`() {
        repeat(3) { repo.incrementPostCount() }

        // Server only counts the rolling 24h window, so deleting an old post
        // shouldn't decrement — otherwise the local count drifts below the server's.
        val twoDaysAgo = java.util.Date(System.currentTimeMillis() - 2L * SubscriptionRepository.ROLLING_WINDOW_MS)
        repo.decrementPostCount(twoDaysAgo)

        assertEquals(3, repo.recentPostCount.value)
        // totalPostCount still drops because that's lifetime, not windowed.
        assertEquals(2, repo.totalPostCount.value)
    }

    @Test
    fun `decrementPostCount clamps at zero so concurrent deletes never go negative`() {
        // No posts incremented — count is already 0.
        val now = java.util.Date()
        repo.decrementPostCount(now)
        repo.decrementPostCount(now)

        assertEquals(0, repo.recentPostCount.value)
        assertEquals(0, repo.totalPostCount.value)
    }

    @Test
    fun `decrementPostCount with null timestamp leaves recentPostCount alone`() {
        // Cache miss path in PostRepository.deletePost passes null — be conservative
        // and let the next refreshPostLimit reconcile rather than guessing.
        repeat(3) { repo.incrementPostCount() }

        repo.decrementPostCount(null)

        assertEquals(3, repo.recentPostCount.value)
        assertEquals(2, repo.totalPostCount.value)
    }

    // ── PurchaseOutcome ──

    @Test
    fun `PurchaseOutcome Success is distinct from Cancelled and Failed`() {
        val success = PurchaseOutcome.Success
        val cancelled = PurchaseOutcome.Cancelled
        val failed = PurchaseOutcome.Failed("test error")

        assertTrue(success is PurchaseOutcome.Success)
        assertTrue(cancelled is PurchaseOutcome.Cancelled)
        assertTrue(failed is PurchaseOutcome.Failed)
        assertTrue((failed as PurchaseOutcome.Failed).error == "test error")
    }

    // ── SharedPreferences caching ──

    @Test
    fun `updateVerifiedStatus caches to SharedPreferences`() {
        repo.updateVerifiedStatus(true)
        verify(prefsEditor).putBoolean("cached_isVerified", true)
        verify(prefsEditor).apply()
    }
}
