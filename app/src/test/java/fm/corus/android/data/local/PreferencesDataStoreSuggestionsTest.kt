package fm.corus.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PreferencesDataStoreSuggestionsTest {
    private fun repository(preferences: Preferences): PreferencesDataStore {
        val store = mock<DataStore<Preferences>>()
        whenever(store.data).thenReturn(MutableStateFlow(preferences))
        return PreferencesDataStore(store, mock<Context>())
    }

    @Test fun missingCacheReturnsWithoutWaitingForAnotherEmission() = runTest {
        assertNull(withTimeout(1000) {
            repository(emptyPreferences()).loadSuggestedMatchesAsync("gabe")
        })
    }

    @Test fun persistedCacheReturnsWithoutWaitingForAnotherEmission() = runTest {
        val preferences = preferencesOf(
            stringPreferencesKey("suggestedMatches_gabe") to """{"matches":[],"fetchedAt":123}""",
        )
        val result = withTimeout(1000) {
            repository(preferences).loadSuggestedMatchesAsync("gabe")
        }
        assertEquals(123L, result?.second)
        assertEquals(emptyList<Any>(), result?.first)
    }
}
