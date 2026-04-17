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
        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenReturn(prefsEditor)
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
        repeat(SubscriptionRepository.DAILY_POST_LIMIT) { repo.incrementPostCount() }
        assertTrue(repo.canPost)
    }

    @Test
    fun `canPost is false when at soft limit and not verified`() {
        repeat(SubscriptionRepository.DAILY_POST_LIMIT) { repo.incrementPostCount() }
        assertFalse(repo.canPost)
    }

    @Test
    fun `canPost is always true when club not enabled`() {
        whenever(remoteConfig.corusClubEnabled).thenReturn(false)
        repeat(SubscriptionRepository.DAILY_POST_LIMIT + 5) { repo.incrementPostCount() }
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
