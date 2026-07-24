package fm.corus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.UsernameValidator
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

    private val _invalidReason = MutableStateFlow<String?>(null)
    val invalidReason: StateFlow<String?> = _invalidReason.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var checkJob: Job? = null
    private var originalUsername: String = ""

    init {
        originalUsername = authRepository.userProfile.value?.username ?: ""
        _username.value = originalUsername
    }

    fun onUsernameChanged(value: String) {
        val cleaned = UsernameValidator.clean(value)
        _username.value = cleaned

        if (cleaned == originalUsername) {
            _validationState.value = ValidationState.Idle
            _invalidReason.value = null
            return
        }

        when (val result = UsernameValidator.validate(cleaned)) {
            is UsernameValidator.Result.Empty -> {
                _validationState.value = ValidationState.Idle
                _invalidReason.value = null
                return
            }
            is UsernameValidator.Result.TooShort -> {
                // Neutral while typing — don't flash the length error mid-edit.
                // Save stays disabled until the handle reaches 3 valid chars.
                _validationState.value = ValidationState.Idle
                _invalidReason.value = null
                return
            }
            is UsernameValidator.Result.Invalid -> {
                _validationState.value = ValidationState.Invalid
                _invalidReason.value = result.message
                return
            }
            UsernameValidator.Result.Valid -> {
                _invalidReason.value = null
            }
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
