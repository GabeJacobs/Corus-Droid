package fm.corus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.service.AppIcon
import fm.corus.android.service.AppIconManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppIconSettingsViewModel @Inject constructor(
    private val icons: AppIconManager,
    private val subscription: SubscriptionRepository,
) : ViewModel() {
    val hasFullAccess = subscription.hasFullAccessFlow
    private val _selectedIcon = MutableStateFlow(icons.selectedIcon())
    val selectedIcon = _selectedIcon.asStateFlow()
    private val _isChanging = MutableStateFlow(false)
    val isChanging = _isChanging.asStateFlow()
    private val _changeFailed = MutableStateFlow(false)
    val changeFailed = _changeFailed.asStateFlow()

    fun refresh() { _selectedIcon.value = icons.selectedIcon() }
    fun dismissError() { _changeFailed.value = false }

    fun changeIcon(icon: AppIcon) {
        if (!subscription.hasFullAccess || _isChanging.value || icons.selectedIcon() == icon) return
        _isChanging.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { icons.changeIcon(icon) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _changeFailed.value = true
            } finally {
                refresh()
                _isChanging.value = false
            }
        }
    }
}
