package fm.corus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.domain.MusicServicePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
}
