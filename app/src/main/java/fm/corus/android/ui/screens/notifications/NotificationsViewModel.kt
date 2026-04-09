package fm.corus.android.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<CymbalNotification>>(emptyList())
    val notifications: StateFlow<List<CymbalNotification>> = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refreshNotifications() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _notifications.value = notificationRepository.getNotifications(userId)
            } catch (_: Exception) { }
            _isRefreshing.value = false
        }
    }

    fun loadNotifications() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Use real-time listener
                notificationRepository.observeNotifications(userId).collect { notifications ->
                    _notifications.value = notifications
                    _isLoading.value = false
                }
            } catch (_: Exception) {
                // Fallback to one-shot fetch
                try {
                    _notifications.value = notificationRepository.getNotifications(userId)
                } catch (_: Exception) { }
                _isLoading.value = false
            }
        }
    }

    fun markAllRead() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try { notificationRepository.markAllRead(userId) } catch (_: Exception) { }
        }
    }
}
