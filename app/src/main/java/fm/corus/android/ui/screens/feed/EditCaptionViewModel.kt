package fm.corus.android.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditCaptionViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _mentionSuggestions = MutableStateFlow<List<CymbalUser>>(emptyList())
    val mentionSuggestions: StateFlow<List<CymbalUser>> = _mentionSuggestions.asStateFlow()

    fun saveCaption(postId: String, caption: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                postRepository.updateCaption(postId, caption)
                onSuccess()
            } catch (_: Exception) { }
            _isSaving.value = false
        }
    }

    fun searchMentions(query: String) {
        if (query.length < 2) {
            _mentionSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val results = userRepository.searchUsers(query, limit = 4)
                _mentionSuggestions.value = results
            } catch (_: Exception) {
                _mentionSuggestions.value = emptyList()
            }
        }
    }

    fun clearMentions() {
        _mentionSuggestions.value = emptyList()
    }
}
