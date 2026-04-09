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
class MutedUsersViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _mutedUsers = MutableStateFlow<List<CymbalUser>>(emptyList())
    val mutedUsers: StateFlow<List<CymbalUser>> = _mutedUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _unmutingIds = MutableStateFlow<Set<String>>(emptySet())
    val unmutingIds: StateFlow<Set<String>> = _unmutingIds.asStateFlow()

    private val _unmuteError = MutableStateFlow<String?>(null)
    val unmuteError: StateFlow<String?> = _unmuteError.asStateFlow()

    init {
        loadMutedUsers()
    }

    private fun loadMutedUsers() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val users = userRepository.fetchMutedUsers(uid)
                _mutedUsers.value = users
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun unmute(targetUserId: String) {
        val uid = authRepository.currentUserId ?: return
        _unmutingIds.value = _unmutingIds.value + targetUserId
        viewModelScope.launch {
            try {
                userRepository.unmuteUser(uid, targetUserId)
                _mutedUsers.value = _mutedUsers.value.filter { it.id != targetUserId }
            } catch (e: Exception) {
                _unmuteError.value = e.localizedMessage ?: "Failed to unmute user"
            }
            _unmutingIds.value = _unmutingIds.value - targetUserId
        }
    }

    fun clearUnmuteError() {
        _unmuteError.value = null
    }
}
