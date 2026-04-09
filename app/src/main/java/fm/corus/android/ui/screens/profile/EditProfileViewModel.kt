package fm.corus.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val postRepository: PostRepository,
    val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    private val _profile = MutableStateFlow<CymbalUser?>(null)
    val profile: StateFlow<CymbalUser?> = _profile.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _bio = MutableStateFlow("")
    val bio: StateFlow<String> = _bio.asStateFlow()

    private val _website = MutableStateFlow("")
    val website: StateFlow<String> = _website.asStateFlow()

    private val _usernameState = MutableStateFlow(UsernameState.IDLE)
    val usernameState: StateFlow<UsernameState> = _usernameState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    // ── Style Picker state ──

    private val _styleSelections = MutableStateFlow(StyleSelections())
    val styleSelections: StateFlow<StyleSelections> = _styleSelections.asStateFlow()

    private val _latestTrackPost = MutableStateFlow<CymbalPost?>(null)
    val latestTrackPost: StateFlow<CymbalPost?> = _latestTrackPost.asStateFlow()

    private val _latestMoviePost = MutableStateFlow<CymbalPost?>(null)
    val latestMoviePost: StateFlow<CymbalPost?> = _latestMoviePost.asStateFlow()

    private val _hasTrackPosts = MutableStateFlow(false)
    val hasTrackPosts: StateFlow<Boolean> = _hasTrackPosts.asStateFlow()

    private val _hasMoviePosts = MutableStateFlow(false)
    val hasMoviePosts: StateFlow<Boolean> = _hasMoviePosts.asStateFlow()

    private val _isStyleSaving = MutableStateFlow(false)
    val isStyleSaving: StateFlow<Boolean> = _isStyleSaving.asStateFlow()

    private var originalUsername = ""
    private var originalDisplayName = ""
    private var originalBio = ""
    private var originalWebsite = ""
    private var usernameCheckJob: Job? = null

    enum class UsernameState {
        IDLE, CHECKING, AVAILABLE, TAKEN, INVALID
    }

    fun loadProfile() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val user = userRepository.fetchUserProfile(userId)
                _profile.value = user
                if (user != null) {
                    _displayName.value = user.displayName
                    _username.value = user.username
                    _bio.value = user.bio
                    _website.value = user.website ?: ""
                    originalDisplayName = user.displayName
                    originalUsername = user.username
                    originalBio = user.bio
                    originalWebsite = user.website ?: ""

                    // Load style selections from user profile
                    _styleSelections.value = StyleSelections(
                        vinylColor = user.vinylStyle,
                        vinylSpinning = user.vinylSpinning,
                        frameColor = user.frameStyle,
                        profileFlair = user.flairStyle,
                        rainEffect = user.rainIntensity,
                        snowEffect = user.snowIntensity,
                        discoEffect = user.discoIntensityLevel,
                    )
                }
            } catch (_: Exception) { }
        }

        // Load user's posts to determine hasTrackPosts / hasMoviePosts and latest posts
        viewModelScope.launch {
            try {
                val posts = postRepository.getProfilePosts(userId, userId, limit = 30)
                val trackPosts = posts.filter { it.isTrack }
                val moviePosts = posts.filter { it.isMovie }
                _hasTrackPosts.value = trackPosts.isNotEmpty()
                _hasMoviePosts.value = moviePosts.isNotEmpty()
                _latestTrackPost.value = trackPosts.firstOrNull()
                _latestMoviePost.value = moviePosts.firstOrNull()
            } catch (_: Exception) { }
        }
    }

    fun updateDisplayName(value: String) {
        _displayName.value = value
    }

    fun updateUsername(value: String) {
        val cleaned = value.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '.' }
        _username.value = cleaned

        if (cleaned == originalUsername) {
            _usernameState.value = UsernameState.IDLE
            return
        }

        if (cleaned.isEmpty()) {
            _usernameState.value = UsernameState.INVALID
            return
        }

        // Validate format
        val isValid = cleaned.matches(Regex("^[a-z0-9_.]+$")) && cleaned.length >= 1
        if (!isValid) {
            _usernameState.value = UsernameState.INVALID
            return
        }

        // Debounced availability check
        usernameCheckJob?.cancel()
        _usernameState.value = UsernameState.CHECKING
        usernameCheckJob = viewModelScope.launch {
            delay(400)
            try {
                val available = userRepository.checkUsernameAvailable(cleaned)
                _usernameState.value = if (available) UsernameState.AVAILABLE else UsernameState.TAKEN
            } catch (_: Exception) {
                _usernameState.value = UsernameState.INVALID
            }
        }
    }

    fun updateBio(value: String) {
        _bio.value = value
    }

    fun updateWebsite(value: String) {
        _website.value = value
    }

    val hasChanges: Boolean
        get() {
            val p = _profile.value ?: return false
            return _displayName.value != p.displayName ||
                    _username.value != p.username ||
                    _bio.value != p.bio ||
                    _website.value != (p.website ?: "")
        }

    val hasUnsavedChanges: Boolean
        get() {
            if (_profile.value == null) return false
            return _displayName.value != originalDisplayName ||
                    _username.value != originalUsername ||
                    _bio.value != originalBio ||
                    _website.value != originalWebsite
        }

    fun clearSaveError() {
        _saveError.value = null
    }

    val canSave: Boolean
        get() {
            if (!hasChanges) return false
            if (_displayName.value.isBlank()) return false
            if (_username.value != originalUsername && _usernameState.value != UsernameState.AVAILABLE) return false
            if (_isSaving.value) return false
            return true
        }

    fun save(onSuccess: () -> Unit) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _saveError.value = null
            try {
                val fields = mutableMapOf<String, Any?>()
                val p = _profile.value!!

                if (_displayName.value != p.displayName) fields["displayName"] = _displayName.value
                if (_username.value != p.username) {
                    fields["username"] = _username.value
                    // Generate search tokens
                    val tokens = mutableListOf<String>()
                    val name = _username.value.lowercase()
                    for (i in 1..name.length) {
                        tokens.add(name.substring(0, i))
                    }
                    fields["searchTokens"] = tokens
                }
                if (_bio.value != p.bio) fields["bio"] = _bio.value
                if (_website.value != (p.website ?: "")) fields["website"] = _website.value

                userRepository.updateUserProfile(userId, fields)
                onSuccess()
            } catch (e: Exception) {
                _saveError.value = "Couldn't save changes. Please try again."
            }
            _isSaving.value = false
        }
    }

    fun saveStyleSelections(selections: StyleSelections, onSuccess: () -> Unit) {
        val userId = authRepository.currentUserId ?: return
        val changedFields = selections.changedFields(_styleSelections.value)
        if (changedFields.isEmpty()) {
            onSuccess()
            return
        }
        viewModelScope.launch {
            _isStyleSaving.value = true
            try {
                userRepository.updateUserProfile(userId, changedFields)
                _styleSelections.value = selections
                onSuccess()
            } catch (e: Exception) {
                _saveError.value = "Couldn't save style changes. Please try again."
            }
            _isStyleSaving.value = false
        }
    }
}
