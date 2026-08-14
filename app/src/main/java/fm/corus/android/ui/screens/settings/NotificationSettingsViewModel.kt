package fm.corus.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class NotificationSettings(
    val likes: Boolean = true,
    val commentsAndReplies: Boolean = true,
    val newFollowers: Boolean = true,
    val followRequests: Boolean = true,
    val contactJoined: Boolean = true,
    val tasteMatches: Boolean = true,
    val plays: Boolean = true,
    val trending: Boolean = true,
    val reactions: Boolean = true,
    val messagePush: Boolean = true,
    val readReceipts: Boolean = true,
    val isLoaded: Boolean = false,
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val analyticsService: AnalyticsService,
    private val remoteConfigService: RemoteConfigService,
) : ViewModel() {

    private val _settings = MutableStateFlow(NotificationSettings())
    val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

    /** Gates the "Plays" toggle row — only shown once play-milestone
     *  notifications are launched (same flag the backend + other clients use). */
    val playMilestoneEnabled: Boolean
        get() = remoteConfigService.playMilestoneEnabled

    private val firestore: FirebaseFirestore = Firebase.firestore

    fun load() {
        if (_settings.value.isLoaded) return
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val doc = firestore.collection("users_v2").document(uid).get().await()
                @Suppress("UNCHECKED_CAST")
                val settingsMap = doc.get("settings") as? Map<String, Any?> ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val notif = settingsMap["notifications"] as? Map<String, Any?> ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val msg = settingsMap["messaging"] as? Map<String, Any?> ?: emptyMap()
                _settings.value = NotificationSettings(
                    likes = notif["likes"] as? Boolean ?: true,
                    commentsAndReplies = notif["commentsAndReplies"] as? Boolean ?: true,
                    newFollowers = notif["newFollowers"] as? Boolean ?: true,
                    followRequests = notif["followRequests"] as? Boolean ?: true,
                    contactJoined = notif["contactJoined"] as? Boolean ?: true,
                    tasteMatches = notif["tasteMatches"] as? Boolean ?: true,
                    plays = notif["plays"] as? Boolean ?: true,
                    trending = notif["trending"] as? Boolean ?: true,
                    reactions = notif["reactions"] as? Boolean ?: true,
                    messagePush = msg["pushEnabled"] as? Boolean ?: true,
                    readReceipts = msg["readReceiptsEnabled"] as? Boolean ?: true,
                    isLoaded = true,
                )
            } catch (_: Exception) {
                _settings.value = _settings.value.copy(isLoaded = true)
            }
        }
    }

    fun setLikes(enabled: Boolean) = updateNotif("likes", enabled) {
        _settings.value = _settings.value.copy(likes = enabled)
    }
    fun setCommentsAndReplies(enabled: Boolean) = updateNotif("commentsAndReplies", enabled) {
        _settings.value = _settings.value.copy(commentsAndReplies = enabled)
    }
    fun setNewFollowers(enabled: Boolean) = updateNotif("newFollowers", enabled) {
        _settings.value = _settings.value.copy(newFollowers = enabled)
    }
    fun setFollowRequests(enabled: Boolean) = updateNotif("followRequests", enabled) {
        _settings.value = _settings.value.copy(followRequests = enabled)
    }
    fun setContactJoined(enabled: Boolean) = updateNotif("contactJoined", enabled) {
        _settings.value = _settings.value.copy(contactJoined = enabled)
    }
    fun setTasteMatches(enabled: Boolean) {
        analyticsService.logTasteMatchSettingsToggled(enabled)
        updateNotif("tasteMatches", enabled) {
            _settings.value = _settings.value.copy(tasteMatches = enabled)
        }
    }
    fun setPlays(enabled: Boolean) = updateNotif("plays", enabled) {
        _settings.value = _settings.value.copy(plays = enabled)
    }
    fun setTrending(enabled: Boolean) = updateNotif("trending", enabled) {
        _settings.value = _settings.value.copy(trending = enabled)
    }
    fun setReactions(enabled: Boolean) = updateNotif("reactions", enabled) {
        _settings.value = _settings.value.copy(reactions = enabled)
    }
    fun setMessagePush(enabled: Boolean) {
        val uid = authRepository.currentUserId ?: return
        _settings.value = _settings.value.copy(messagePush = enabled)
        viewModelScope.launch {
            try {
                userRepository.updateUserProfile(uid, mapOf("settings.messaging.pushEnabled" to enabled))
            } catch (_: Exception) { }
        }
    }

    fun setReadReceipts(enabled: Boolean) {
        val uid = authRepository.currentUserId ?: return
        _settings.value = _settings.value.copy(readReceipts = enabled)
        viewModelScope.launch {
            try {
                userRepository.updateUserProfile(uid, mapOf("settings.messaging.readReceiptsEnabled" to enabled))
                analyticsService.logSettingToggled("read_receipts_enabled", enabled)
            } catch (_: Exception) { }
        }
    }

    private fun updateNotif(key: String, enabled: Boolean, optimistic: () -> Unit) {
        val uid = authRepository.currentUserId ?: return
        optimistic()
        viewModelScope.launch {
            try {
                userRepository.updateUserProfile(uid, mapOf("settings.notifications.$key" to enabled))
                analyticsService.logSettingToggled("${key}_notifications", enabled)
            } catch (_: Exception) { }
        }
    }
}
