package fm.corus.android.ui.screens.findpeople

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BotListViewModel @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _bots = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val bots: StateFlow<List<SuggestedUserMatch>> = _bots.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _followedIds = MutableStateFlow<Set<String>>(emptySet())

    fun loadBots(botType: String?) {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                _bots.value = cloudFunctions.getBotSuggestions(uid, botType = botType)
            } catch (_: Exception) { }
            _isLoading.value = false
        }
        viewModelScope.launch {
            userRepository.followingIds.collect { _followedIds.value = it }
        }
    }

    fun isFollowed(userId: String): Boolean {
        return _followedIds.value.contains(userId)
    }

    fun toggleFollow(user: CymbalUser) {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            if (isFollowed(user.id)) {
                try { userRepository.unfollowUser(uid, user.id) } catch (_: Exception) { }
            } else {
                try { userRepository.followUser(uid, user.id) } catch (_: Exception) { }
            }
        }
    }
}
