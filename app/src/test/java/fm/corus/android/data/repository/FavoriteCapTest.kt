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
 * Favorite-people cap pre-check unit tests. Mirror the backend
 * `shouldRejectFavorite` matrix so client + server stay in lockstep on the gate
 * behavior. Mirrors SaveCapTest.
 */
class FavoriteCapTest {

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
        whenever(remoteConfig.favoritePeopleCapLimit).thenReturn(3)
        repo = SubscriptionRepository(cloudFunctions, remoteConfig, analyticsService, prefs)
    }

    @Test
    fun `cap not enforced - never rejects`() {
        whenever(remoteConfig.favoritePeopleCapEnforced).thenReturn(false)
        repo.setFavoritesCount(1000)
        assertFalse(repo.shouldRejectFavorite())
    }

    @Test
    fun `verified user - never rejected even at high count`() {
        whenever(remoteConfig.favoritePeopleCapEnforced).thenReturn(true)
        repo.updateVerifiedStatus(true)
        repo.setFavoritesCount(1000)
        assertFalse(repo.shouldRejectFavorite())
    }

    @Test
    fun `free user under cap - allowed`() {
        whenever(remoteConfig.favoritePeopleCapEnforced).thenReturn(true)
        repo.setFavoritesCount(2)
        assertFalse(repo.shouldRejectFavorite())
    }

    @Test
    fun `free user at cap - rejected`() {
        whenever(remoteConfig.favoritePeopleCapEnforced).thenReturn(true)
        repo.setFavoritesCount(3)
        assertTrue(repo.shouldRejectFavorite())
    }

    @Test
    fun `free user grandfathered above cap - new favorites rejected`() {
        whenever(remoteConfig.favoritePeopleCapEnforced).thenReturn(true)
        repo.setFavoritesCount(1000)
        assertTrue(repo.shouldRejectFavorite())
    }

    @Test
    fun `setFavoritesCount clamps negative to zero`() {
        repo.setFavoritesCount(-5)
        assertEquals(0, repo.favoritesCount.value)
    }
}
