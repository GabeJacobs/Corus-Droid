package fm.corus.android.data.repository

import android.content.SharedPreferences
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SubscriptionRepositoryTest {

    private lateinit var repo: SubscriptionRepository
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
        whenever(prefsEditor.putBoolean(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(prefsEditor)
        repo = SubscriptionRepository(mock<CloudFunctionsDataSource>(), remoteConfig, analyticsService, prefs)
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
        org.mockito.kotlin.verify(prefsEditor).putBoolean("cached_isVerified", true)
        org.mockito.kotlin.verify(prefsEditor).apply()
    }
}
