package fm.corus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.domain.MusicServicePreference
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val musicServicePreference: MusicServicePreference,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    val isClubMember: StateFlow<Boolean> = subscriptionRepository.isClubMember
    val isVerified: StateFlow<Boolean> = subscriptionRepository.isVerified
}
