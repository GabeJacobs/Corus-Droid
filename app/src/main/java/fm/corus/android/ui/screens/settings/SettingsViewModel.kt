package fm.corus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.domain.MusicServicePreference
import fm.corus.android.i18n.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val musicServicePreference: MusicServicePreference,
    private val subscriptionRepository: SubscriptionRepository,
    private val preferencesDataStore: PreferencesDataStore,
) : ViewModel() {

    val isClubMember: StateFlow<Boolean> = subscriptionRepository.isClubMember
    val isVerified: StateFlow<Boolean> = subscriptionRepository.isVerified

    val autoplayNextSong: StateFlow<Boolean> = preferencesDataStore.autoplayNextSong
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAutoplayNextSong(value: Boolean) {
        viewModelScope.launch { preferencesDataStore.setAutoplayNextSong(value) }
    }

    /**
     * Persist the user's language preference to Firestore so the backend can send
     * push notifications in their preferred language. Mirrors iOS DatabaseService.updateLanguage.
     * Stored at users_v2/{uid}.settings.language as one of "system" | "en" | "pt-BR".
     */
    fun syncLanguagePreference(language: AppLanguage) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val value = when (language) {
            AppLanguage.SYSTEM -> "system"
            AppLanguage.ENGLISH -> "en"
            AppLanguage.PORTUGUESE_BR -> "pt-BR"
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users_v2")
                    .document(uid)
                    .set(mapOf("settings" to mapOf("language" to value)), SetOptions.merge())
                    .await()
            } catch (_: Exception) { /* best effort — local pref still applied */ }
        }
    }
}
