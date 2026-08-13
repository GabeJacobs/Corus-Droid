package fm.corus.android.domain

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the user's preferred music service.
 * Reads from SharedPreferences (local cache) and syncs with Firestore.
 * Mirrors the iOS @AppStorage("musicService") pattern.
 *
 * Also hydrates "Add Saved Songs to Library" (`settings.autoAddToSpotify`)
 * so a login shows the account's saved value instead of the unset default.
 */
@Singleton
class MusicServicePreference @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val preferencesDataStore: PreferencesDataStore,
) {
    private val prefs = context.getSharedPreferences("corus_prefs", Context.MODE_PRIVATE)

    private val _current = MutableStateFlow(loadLocal())
    val current: StateFlow<MusicService> = _current.asStateFlow()

    private fun loadLocal(): MusicService {
        val raw = prefs.getString("musicService", MusicService.SPOTIFY.value) ?: MusicService.SPOTIFY.value
        return MusicService.fromValue(raw)
    }

    fun set(service: MusicService) {
        prefs.edit().putString("musicService", service.value).apply()
        _current.value = service
    }

    /**
     * Load from Firestore and update local cache.
     * Called on app launch / settings screen open.
     */
    suspend fun syncFromFirestore() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val doc = firestore.collection("users_v2").document(uid).get().await()
            val settings = doc.get("settings") as? Map<*, *>
            val raw = settings?.get("musicService") as? String
            if (raw != null) {
                val service = MusicService.fromValue(raw)
                set(service)
            }
            syncAutoAddToSpotifyFromSettings(settings)
        } catch (_: Exception) { }
    }

    /**
     * Account-scoped overlay for the Spotify library-save toggle.
     * Firestore is source of truth when the field is present; otherwise a
     * locally explicit value is backfilled so other devices can pick it up.
     * An unset local default is not written — that would stamp "off" onto
     * accounts that never touched the setting.
     */
    private suspend fun syncAutoAddToSpotifyFromSettings(settings: Map<*, *>?) {
        val fromAccount = settings?.get("autoAddToSpotify") as? Boolean
        if (fromAccount != null) {
            preferencesDataStore.setAutoAddSavedToSpotify(fromAccount)
            return
        }
        val local = preferencesDataStore.autoAddSavedToSpotifyOrNull() ?: return
        syncAutoAddToSpotifyToFirestore(local)
    }

    /**
     * Persist the Spotify library-save toggle to Firestore (merge) so logins
     * on other devices restore it instead of the unset default.
     */
    suspend fun syncAutoAddToSpotifyToFirestore(enabled: Boolean) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("users_v2").document(uid)
                .set(mapOf("settings" to mapOf("autoAddToSpotify" to enabled)), SetOptions.merge())
                .await()
        } catch (_: Exception) { }
    }

    /**
     * Save to Firestore and update local cache.
     */
    suspend fun syncToFirestore(service: MusicService) {
        set(service)
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("users_v2").document(uid)
                .set(mapOf("settings" to mapOf("musicService" to service.value)), SetOptions.merge())
                .await()
        } catch (_: Exception) { }
    }
}
