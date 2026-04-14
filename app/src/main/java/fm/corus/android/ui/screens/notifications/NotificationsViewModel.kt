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

    private val pageSize = 15

    private val _notifications = MutableStateFlow<List<CymbalNotification>>(emptyList())
    val notifications: StateFlow<List<CymbalNotification>> = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMoreNotifications = MutableStateFlow(true)
    val hasMoreNotifications: StateFlow<Boolean> = _hasMoreNotifications.asStateFlow()

    fun refreshNotifications() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _notifications.value = notificationRepository.getNotifications(userId, limit = pageSize)
                _hasMoreNotifications.value = _notifications.value.size >= pageSize
            } catch (_: Exception) { }
            _isRefreshing.value = false
        }
    }

    fun loadNotifications() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Use real-time listener for the first page
                notificationRepository.observeNotifications(userId, limit = pageSize).collect { incoming ->
                    mergeNotifications(incoming)
                    _hasMoreNotifications.value = incoming.size >= pageSize
                    _isLoading.value = false
                }
            } catch (_: Exception) {
                // Fallback to one-shot fetch
                try {
                    _notifications.value = notificationRepository.getNotifications(userId, limit = pageSize)
                    _hasMoreNotifications.value = _notifications.value.size >= pageSize
                } catch (_: Exception) { }
                _isLoading.value = false
            }
        }
    }

    /**
     * Merges incoming real-time listener results (first page) with existing
     * paginated items, matching iOS behaviour. Only the "head" window is
     * reconciled — older paginated "tail" items are kept untouched.
     */
    private fun mergeNotifications(incoming: List<CymbalNotification>) {
        val current = _notifications.value
        if (current.isEmpty()) {
            _notifications.value = incoming
            return
        }

        val incomingIds = incoming.map { it.id }.toSet()

        // Split: items in the incoming window vs older paginated items
        val tailItems = current.filter { it.id !in incomingIds }

        // Build head from incoming order (includes new + updated items)
        _notifications.value = incoming + tailItems
    }

    fun loadMoreNotifications() {
        val userId = authRepository.currentUserId ?: return
        if (_isLoadingMore.value || !_hasMoreNotifications.value) return
        val lastTimestamp = _notifications.value.lastOrNull()?.timestamp?.time ?: return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val fetched = notificationRepository.getNotifications(
                    userId, limit = pageSize, lastTimestamp = lastTimestamp,
                )
                val existingIds = _notifications.value.map { it.id }.toSet()
                val newItems = fetched.filter { it.id !in existingIds }
                _notifications.value = _notifications.value + newItems
                _hasMoreNotifications.value = fetched.size >= pageSize && newItems.isNotEmpty()
            } catch (_: Exception) {
                _hasMoreNotifications.value = false
            }
            _isLoadingMore.value = false
        }
    }

    fun markAllRead() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try { notificationRepository.markAllRead(userId) } catch (_: Exception) { }
        }
    }
}
