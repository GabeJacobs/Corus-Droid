package fm.corus.android.data.repository

import android.content.SharedPreferences
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Cap pre-check unit tests. Mirror the backend `shouldRejectSave` matrix so
 * client + server stay in lockstep on the gate behavior.
 */
class SaveCapTest {

    private lateinit var repo: SubscriptionRepository
    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val remoteConfig = mock<RemoteConfigService>()
    private val analyticsService = mock<AnalyticsService>()
    private val prefs = mock<SharedPreferences>()
    private val prefsEditor = mock<SharedPreferences.Editor>()

    @Before
    fun setUp() {
        whenever(prefs.getBoolean("cached_isClubMember", false)).thenReturn(false)
        whenever(prefs.getBoolean("cached_isVerified", false)).thenReturn(false)
        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenReturn(prefsEditor)
        whenever(remoteConfig.corusClubEnabled).thenReturn(true)
        whenever(remoteConfig.saveCapLimit).thenReturn(25)
        whenever(remoteConfig.saveCapWarningAt).thenReturn(23)
        repo = SubscriptionRepository(cloudFunctions, remoteConfig, analyticsService, prefs)
    }

    @Test
    fun `cap not enforced - never rejects`() {
        whenever(remoteConfig.saveCapEnforced).thenReturn(false)
        repo.setSavesCount(1000)
        assertFalse(repo.shouldRejectSave())
    }

    @Test
    fun `verified user - never rejected even at high count`() {
        whenever(remoteConfig.saveCapEnforced).thenReturn(true)
        repo.updateVerifiedStatus(true)
        repo.setSavesCount(1000)
        assertFalse(repo.shouldRejectSave())
    }

    @Test
    fun `free user under cap - allowed`() {
        whenever(remoteConfig.saveCapEnforced).thenReturn(true)
        repo.setSavesCount(24)
        assertFalse(repo.shouldRejectSave())
    }

    @Test
    fun `free user at cap - rejected`() {
        whenever(remoteConfig.saveCapEnforced).thenReturn(true)
        repo.setSavesCount(25)
        assertTrue(repo.shouldRejectSave())
    }

    @Test
    fun `free user grandfathered above cap - new saves rejected`() {
        whenever(remoteConfig.saveCapEnforced).thenReturn(true)
        repo.setSavesCount(1000)
        assertTrue(repo.shouldRejectSave())
    }

    @Test
    fun `setSavesCount clamps negative to zero`() {
        repo.setSavesCount(-5)
        assertEquals(0, repo.savesCount.value)
    }
}
