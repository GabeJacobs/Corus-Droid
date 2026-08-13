package fm.corus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.domain.MusicServicePreference
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.i18n.AppLanguage
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val musicServicePreference: MusicServicePreference,
    private val subscriptionRepository: SubscriptionRepository,
    private val preferencesDataStore: PreferencesDataStore,
    private val remoteConfigService: RemoteConfigService,
    private val analyticsService: AnalyticsService,
    private val nowPlayingManager: NowPlayingManager,
) : ViewModel() {

    val isClubMember: StateFlow<Boolean> = subscriptionRepository.isClubMember
    val isVerified: StateFlow<Boolean> = subscriptionRepository.isVerified

    /** Whether the TIDAL option should appear in the music-service picker. */
    val tidalEnabled: Boolean
        get() = remoteConfigService.tidalEnabled

    /** Whether the YouTube Music option should appear in the music-service picker. */
    val youtubeMusicEnabled: Boolean
        get() = remoteConfigService.youtubeMusicEnabled

    /** Whether the Deezer option should appear in the music-service picker. */
    val deezerEnabled: Boolean
        get() = remoteConfigService.deezerEnabled

    val alwaysPlayFullSongs: StateFlow<Boolean> = preferencesDataStore.alwaysPlayFullSongs
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAlwaysPlayFullSongs(value: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setAlwaysPlayFullSongs(value)
            nowPlayingManager.applyPlaybackModeToggle(toFull = value)
        }
    }

    /** Gate for the "Add Saved Songs to Library" App Remote opt-in settings row. */
    val spotifyLibrarySaveEnabled: Boolean
        get() = remoteConfigService.spotifyLibrarySaveEnabled

    val autoAddSavedToSpotify: StateFlow<Boolean> = preferencesDataStore.autoAddSavedToSpotify
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            preferencesDataStore.autoAddSavedToSpotifySync(),
        )

    init {
        // Overlay the account's saved toggle before Settings paints, so a
        // login doesn't show the unset default over `settings.autoAddToSpotify`.
        viewModelScope.launch { musicServicePreference.syncFromFirestore() }
    }

    /**
     * Toggles the "Add Saved Songs to Library" opt-in. Saves are mirrored over
     * App Remote now, so the library scopes ride along with the playback
     * authorization the user already grants when they first play a full song.
     * There's nothing to consent to here and no OAuth trip to make — this is
     * just a preference. Dual-written to Firestore so other devices restore it.
     */
    fun onSpotifyLibrarySaveToggled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.setAutoAddSavedToSpotify(enabled)
            musicServicePreference.syncAutoAddToSpotifyToFirestore(enabled)
        }
    }

    /** Persist the user's music-service choice (local cache + Firestore). */
    fun setMusicService(service: MusicService) {
        analyticsService.logMusicServiceSelected(service.value)
        viewModelScope.launch { musicServicePreference.syncToFirestore(service) }
    }

    enum class RestoreResult { Success, NoSubscription, Failed }

    private val _restoreInProgress = MutableStateFlow(false)
    val restoreInProgress: StateFlow<Boolean> = _restoreInProgress.asStateFlow()

    private val _restoreResult = MutableStateFlow<RestoreResult?>(null)
    val restoreResult: StateFlow<RestoreResult?> = _restoreResult.asStateFlow()

    /**
     * Replays the StoreKit/Play receipt from RevenueCat and syncs the resulting club
     * status to Firestore. Surfaced from the non-member Settings row so a paid user
     * whose client lost their entitlement can recover without walking through the
     * upsell sheet just to find the restore button.
     */
    fun restorePurchases() {
        if (_restoreInProgress.value) return
        _restoreInProgress.value = true
        _restoreResult.value = null
        subscriptionRepository.restorePurchases { success ->
            _restoreInProgress.value = false
            if (success) {
                analyticsService.logPurchaseRestored()
                _restoreResult.value = RestoreResult.Success
            } else {
                // RevenueCat reports success-with-no-entitlement and hard failure the
                // same way (onSuccess where isClubMember stays false) for our purposes,
                // so log as the more common case: nothing to restore.
                analyticsService.logPurchaseRestoreFailed("No active subscription found")
                _restoreResult.value = RestoreResult.NoSubscription
            }
        }
    }

    fun clearRestoreResult() {
        _restoreResult.value = null
    }

    val feedFollowsNowPlaying: StateFlow<Boolean> = preferencesDataStore.feedFollowsNowPlaying
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setFeedFollowsNowPlaying(value: Boolean) {
        viewModelScope.launch { preferencesDataStore.setFeedFollowsNowPlaying(value) }
    }

    /**
     * Persist the user's language preference to Firestore so the backend can send
     * push notifications in their preferred language. Mirrors iOS DatabaseService.updateLanguage.
     * Stored at users_v2/{uid}.settings.language as one of "system" | "en" | "pt-BR" | "es".
     */
    fun syncLanguagePreference(language: AppLanguage) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val value = when (language) {
            AppLanguage.SYSTEM -> "system"
            AppLanguage.ENGLISH -> "en"
            AppLanguage.PORTUGUESE_BR -> "pt-BR"
            AppLanguage.SPANISH -> "es"
            AppLanguage.JAPANESE -> "ja"
            AppLanguage.CHINESE_SIMPLIFIED -> "zh-Hans"
            AppLanguage.GERMAN -> "de"
            AppLanguage.FRENCH -> "fr"
            AppLanguage.KOREAN -> "ko"
            AppLanguage.ITALIAN -> "it"
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
