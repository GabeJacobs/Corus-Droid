package fm.corus.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single canonical DataStore delegate for `corus_prefs`. Do not declare another
 * `preferencesDataStore(name = "corus_prefs")` anywhere — Android throws if two
 * delegates open the same file.
 */
internal val Context.corusPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "corus_prefs",
)
