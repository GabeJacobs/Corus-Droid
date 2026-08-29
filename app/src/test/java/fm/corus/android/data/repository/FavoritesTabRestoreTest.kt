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
 * Favorites tab cache is per-uid and count-driven. A leftover unlocked flag
 * from another account (or a previous visit) must not show the tab for a
 * user with 0 favorites.
 */
class FavoritesTabRestoreTest {

    private lateinit var repo: SubscriptionRepository
    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val remoteConfig = mock<RemoteConfigService>()
    private val analyticsService = mock<AnalyticsService>()
    private val prefs = mock<SharedPreferences>()
    private val prefsEditor = mock<SharedPreferences.Editor>()
    private val store = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        whenever(prefs.getBoolean(any(), any())).thenAnswer { inv ->
            store[inv.getArgument(0)] as? Boolean ?: inv.getArgument(1)
        }
        whenever(prefs.getInt(any(), any())).thenAnswer { inv ->
            store[inv.getArgument(0)] as? Int ?: inv.getArgument(1)
        }
        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenAnswer { inv ->
            store[inv.getArgument(0)] = inv.getArgument(1)
            prefsEditor
        }
        whenever(prefsEditor.putInt(any(), any())).thenAnswer { inv ->
            store[inv.getArgument(0)] = inv.getArgument(1)
            prefsEditor
        }
        whenever(prefsEditor.apply()).then { }
        repo = SubscriptionRepository(cloudFunctions, remoteConfig, analyticsService, prefs)
    }

    @Test
    fun `restore ignores a stale unlocked flag when this uid has no favorites`() {
        store["cached_favoritesTabUnlocked"] = true
        store["cached_favoritesTabUnlocked_userB"] = true
        store["cached_favoritesCount_userB"] = 0

        repo.restoreFavoritesForUser("userB")

        assertEquals(0, repo.favoritesCount.value)
        assertFalse(repo.favoritesTabUnlocked.value)
    }

    @Test
    fun `restore shows the tab when this uid has a cached count`() {
        store["cached_favoritesCount_gabe"] = 4

        repo.restoreFavoritesForUser("gabe")

        assertEquals(4, repo.favoritesCount.value)
        assertTrue(repo.favoritesTabUnlocked.value)
    }

    @Test
    fun `logout then restore of an empty account does not keep the previous latch`() {
        repo.restoreFavoritesForUser("gabe")
        repo.setFavoritesCount(6)
        assertTrue(repo.favoritesTabUnlocked.value)

        repo.logoutUser()
        repo.restoreFavoritesForUser("empty")

        assertEquals(0, repo.favoritesCount.value)
        assertFalse(repo.favoritesTabUnlocked.value)
    }

    @Test
    fun `explicit zero after logout does not re-unlock the next account`() {
        repo.restoreFavoritesForUser("gabe")
        repo.setFavoritesCount(3)
        repo.logoutUser()
        repo.restoreFavoritesForUser("empty")
        repo.setFavoritesCount(0, allowZero = true)

        assertEquals(0, repo.favoritesCount.value)
        assertFalse(repo.favoritesTabUnlocked.value)
    }
}
