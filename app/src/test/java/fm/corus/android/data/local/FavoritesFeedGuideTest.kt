package fm.corus.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class FavoritesFeedGuideTest {
    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
    @Test fun `disabled features never consume the guide and each account has its own persisted claim`() = runTest {
        val store = MemoryStore()
        val prefs = PreferencesDataStore(store, mock<Context>())
        assertFalse(prefs.claimFavoritesFeedGuide("a", false, true))
        assertFalse(prefs.claimFavoritesFeedGuide("a", true, false))
        assertFalse(prefs.claimFavoritesFeedGuide("", true, true))
        assertTrue(prefs.claimFavoritesFeedGuide("a", true, true))
        assertFalse(PreferencesDataStore(store, mock<Context>()).claimFavoritesFeedGuide("a", true, true))
        assertTrue(prefs.claimFavoritesFeedGuide("b", true, true))
    }
    @Test fun `simultaneous successful favorites only claim one explanation`() = runTest {
        val prefs = PreferencesDataStore(MemoryStore(), mock<Context>())
        val claims = (1..10).map { async { prefs.claimFavoritesFeedGuide("a", true, true) } }.awaitAll()
        assertEquals(1, claims.count { it })
    }
}
