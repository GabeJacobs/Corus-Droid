package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    private val firestoreDataSource: FirestoreDataSource,
    private val userRepository: UserRepository,
) {
    suspend fun getNotifications(userId: String, limit: Int = 15, lastTimestamp: Long? = null): List<CymbalNotification> {
        return cloudFunctions.getNotifications(userId, limit, lastTimestamp)
    }

    // Firestore notification docs store only `fromUserId`, so the listener
    // returns notifications with empty `fromUser.username`/`avatarURL`. Batch
    // fetch the sender profiles (cached) and hydrate — matches iOS
    // listenForNotifications behaviour.
    fun observeNotifications(userId: String, limit: Int = 15): Flow<List<CymbalNotification>> {
        return firestoreDataSource.observeNotifications(userId, limit).map { notifications ->
            val ids = notifications.map { it.fromUser.id }.filter { it.isNotEmpty() }.distinct()
            if (ids.isEmpty()) return@map notifications
            val byId = userRepository.fetchUsersByIdsBatched(ids).associateBy { it.id }
            notifications.map { n ->
                val hydrated = byId[n.fromUser.id]
                if (hydrated != null) n.copy(fromUser = hydrated) else n
            }
        }
    }

    suspend fun markNotificationRead(notificationId: String) {
        firestoreDataSource.markNotificationRead(notificationId)
    }

    suspend fun markAllRead(userId: String) {
        firestoreDataSource.markAllNotificationsRead(userId)
    }
}
