package fm.corus.android.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import fm.corus.android.service.ActiveThreadTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide unread counts backed by Firestore snapshot listeners.
 *
 * Matches iOS MainTabView listeners:
 *  - `notifications` where `toUserId == uid && isRead == false`
 *  - `users_v2/{uid}/threads` where `unreadCount > 0`
 *
 * Lifecycle is driven by [AuthViewModel] — [start] on sign-in, [stop] on sign-out.
 */
@Singleton
class UnreadCountsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val _notificationCount = MutableStateFlow(0)
    val notificationCount: StateFlow<Int> = _notificationCount.asStateFlow()

    private val _unreadMessageCount = MutableStateFlow(0)
    val unreadMessageCount: StateFlow<Int> = _unreadMessageCount.asStateFlow()

    private var notificationListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var currentUserId: String? = null

    fun start(userId: String) {
        if (userId.isBlank() || currentUserId == userId) return
        stop()
        currentUserId = userId

        notificationListener = firestore.collection("notifications")
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                _notificationCount.value = snapshot.size()
            }

        messagesListener = firestore.collection("users_v2")
            .document(userId)
            .collection("threads")
            .whereGreaterThan("unreadCount", 0)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                // Exclude the thread the user is actively viewing so its count
                // never contributes to the badge (the thread auto-marks-read, but
                // excluding it here removes the brief tick between the message
                // arriving and the read write landing).
                _unreadMessageCount.value = snapshot.documents
                    .filter { it.id != ActiveThreadTracker.activeThreadId }
                    .sumOf { doc -> (doc.get("unreadCount") as? Number)?.toInt() ?: 0 }
            }
    }

    fun stop() {
        notificationListener?.remove()
        messagesListener?.remove()
        notificationListener = null
        messagesListener = null
        currentUserId = null
        _notificationCount.value = 0
        _unreadMessageCount.value = 0
    }

    /**
     * Optimistically zero the in-memory notification count. Matches iOS
     * MainTabView which sets `notificationCount = 0` on Activity tab entry —
     * the subsequent Firestore batch write from markAllRead eventually
     * confirms the zero via the listener, but the optimistic zero means the
     * badge stays cleared even if the write fails silently (e.g. offline,
     * permissions).
     */
    fun clearNotificationCount() {
        _notificationCount.value = 0
    }
}
