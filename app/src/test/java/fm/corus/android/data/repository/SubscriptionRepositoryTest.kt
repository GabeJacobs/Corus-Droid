package fm.corus.android.data.repository

import fm.corus.android.data.remote.CloudFunctionsDataSource
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

    @Before
    fun setUp() {
        whenever(remoteConfig.corusClubEnabled).thenReturn(true)
        repo = SubscriptionRepository(mock<CloudFunctionsDataSource>(), remoteConfig)
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
}
