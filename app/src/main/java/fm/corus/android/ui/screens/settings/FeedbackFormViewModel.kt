package fm.corus.android.ui.screens.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackFormViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _feedbackType = MutableStateFlow(FeedbackType.BUG)
    val feedbackType: StateFlow<FeedbackType> = _feedbackType.asStateFlow()

    private val _subject = MutableStateFlow("")
    val subject: StateFlow<String> = _subject.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _showSuccess = MutableStateFlow(false)
    val showSuccess: StateFlow<Boolean> = _showSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val canSubmit: Boolean
        get() = _subject.value.isNotBlank() && _description.value.isNotBlank() && !_isSubmitting.value

    fun onTypeChanged(type: FeedbackType) { _feedbackType.value = type }
    fun onSubjectChanged(value: String) { _subject.value = value }
    fun onDescriptionChanged(value: String) { _description.value = value }
    fun clearError() { _errorMessage.value = null }

    fun submit(onDismiss: () -> Unit) {
        val uid = authRepository.currentUserId ?: return
        if (!canSubmit) return

        _isSubmitting.value = true
        viewModelScope.launch {
            try {
                val deviceInfo = mapOf(
                    "model" to Build.MODEL,
                    "manufacturer" to Build.MANUFACTURER,
                    "systemVersion" to "Android ${Build.VERSION.RELEASE}",
                    "sdkVersion" to Build.VERSION.SDK_INT.toString(),
                )
                userRepository.submitFeedback(
                    userId = uid,
                    type = _feedbackType.value.label,
                    subject = _subject.value.trim(),
                    description = _description.value.trim(),
                    deviceInfo = deviceInfo,
                )
                _isSubmitting.value = false
                _showSuccess.value = true
                delay(1500)
                onDismiss()
            } catch (_: Exception) {
                _isSubmitting.value = false
                _errorMessage.value = "Couldn't submit your feedback. Please try again."
            }
        }
    }

    enum class FeedbackType(val label: String) {
        BUG("Bug Report"),
        FEATURE("Feature Request"),
    }
}
