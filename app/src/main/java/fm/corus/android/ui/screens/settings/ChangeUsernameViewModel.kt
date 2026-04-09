package fm.corus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangeUsernameViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _validationState = MutableStateFlow(ValidationState.Idle)
    val validationState: StateFlow<ValidationState> = _validationState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var checkJob: Job? = null
    private var originalUsername: String = ""

    init {
        originalUsername = authRepository.userProfile.value?.username ?: ""
        _username.value = originalUsername
    }

    fun onUsernameChanged(value: String) {
        val cleaned = value.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '.' }
        _username.value = cleaned

        if (cleaned == originalUsername) {
            _validationState.value = ValidationState.Idle
            return
        }

        if (cleaned.length < 3) {
            _validationState.value = ValidationState.Invalid
            return
        }

        val validPattern = Regex("^[a-z0-9_.]+$")
        if (!validPattern.matches(cleaned)) {
            _validationState.value = ValidationState.Invalid
            return
        }

        _validationState.value = ValidationState.Checking
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            delay(400)
            try {
                val available = userRepository.checkUsernameAvailable(cleaned)
                _validationState.value = if (available) ValidationState.Available else ValidationState.Taken
            } catch (_: Exception) {
                _validationState.value = ValidationState.Invalid
            }
        }
    }

    fun save(onSuccess: () -> Unit) {
        val uid = authRepository.currentUserId ?: return
        val newUsername = _username.value
        if (newUsername == originalUsername || _validationState.value != ValidationState.Available) return

        _isSaving.value = true
        viewModelScope.launch {
            try {
                userRepository.updateUserProfile(uid, mapOf("username" to newUsername))
                authRepository.refreshUserProfile()
                originalUsername = newUsername
                _validationState.value = ValidationState.Idle
                onSuccess()
            } catch (_: Exception) { }
            _isSaving.value = false
        }
    }

    enum class ValidationState {
        Idle, Checking, Available, Taken, Invalid
    }
}
