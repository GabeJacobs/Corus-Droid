package fm.corus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.ui.theme.AppearanceMode
import fm.corus.android.ui.theme.toAppearanceMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
) : ViewModel() {

    val appearanceMode: StateFlow<AppearanceMode> = preferencesDataStore.appearanceMode
        .map { it.toAppearanceMode() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            preferencesDataStore.unsetThemeDefaultStorageValue().toAppearanceMode(),
        )

    fun setAppearanceMode(mode: AppearanceMode) {
        viewModelScope.launch {
            preferencesDataStore.setAppearanceMode(mode.storageValue)
        }
    }
}
