package fm.corus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockedUsersViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _blockedUsers = MutableStateFlow<List<CymbalUser>>(emptyList())
    val blockedUsers: StateFlow<List<CymbalUser>> = _blockedUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _unblockingIds = MutableStateFlow<Set<String>>(emptySet())
    val unblockingIds: StateFlow<Set<String>> = _unblockingIds.asStateFlow()

    private val _unblockError = MutableStateFlow<String?>(null)
    val unblockError: StateFlow<String?> = _unblockError.asStateFlow()

    init {
        loadBlockedUsers()
    }

    private fun loadBlockedUsers() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val ids = userRepository.blockedIds.value
                val users = ids.mapNotNull { userRepository.fetchUserProfile(it) }
                _blockedUsers.value = users
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun unblock(targetUserId: String) {
        val uid = authRepository.currentUserId ?: return
        _unblockingIds.value = _unblockingIds.value + targetUserId
        viewModelScope.launch {
            try {
                userRepository.unblockUser(uid, targetUserId)
                _blockedUsers.value = _blockedUsers.value.filter { it.id != targetUserId }
            } catch (e: Exception) {
                _unblockError.value = e.localizedMessage ?: "Failed to unblock user"
            }
            _unblockingIds.value = _unblockingIds.value - targetUserId
        }
    }

    fun clearUnblockError() {
        _unblockError.value = null
    }
}
