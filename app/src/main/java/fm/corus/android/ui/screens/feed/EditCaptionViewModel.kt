package fm.corus.android.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.ui.components.extractMentions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditCaptionViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _mentionSuggestions = MutableStateFlow<List<CymbalUser>>(emptyList())
    val mentionSuggestions: StateFlow<List<CymbalUser>> = _mentionSuggestions.asStateFlow()

    fun saveCaption(
        postId: String,
        caption: String,
        oldCaption: String = "",
        postAlbumArtURL: String? = null,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                postRepository.updateCaption(postId, caption)
                notifyNewMentions(postId, caption, oldCaption, postAlbumArtURL)
                onSuccess()
            } catch (_: Exception) { }
            _isSaving.value = false
        }
    }

    private suspend fun notifyNewMentions(
        postId: String,
        newCaption: String,
        oldCaption: String,
        postAlbumArtURL: String?,
    ) {
        val userId = authRepository.currentUserId ?: return
        val newMentions = extractMentions(newCaption).toSet()
        val oldMentions = extractMentions(oldCaption).toSet()
        val added = newMentions - oldMentions

        for (username in added) {
            try {
                val user = userRepository.fetchUserByUsername(username) ?: continue
                if (user.id == userId) continue
                postRepository.createNotification(
                    type = "tag",
                    fromUserId = userId,
                    toUserId = user.id,
                    postId = postId,
                    postAlbumArtURL = postAlbumArtURL,
                )
            } catch (_: Exception) { }
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
