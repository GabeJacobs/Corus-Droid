package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    private val firestoreDataSource: FirestoreDataSource,
) {
    suspend fun getNotifications(userId: String, limit: Int = 15, lastTimestamp: Long? = null): List<CymbalNotification> {
        return cloudFunctions.getNotifications(userId, limit, lastTimestamp)
    }

    fun observeNotifications(userId: String, limit: Int = 15): Flow<List<CymbalNotification>> {
        return firestoreDataSource.observeNotifications(userId, limit)
    }

    suspend fun markNotificationRead(notificationId: String) {
        firestoreDataSource.markNotificationRead(notificationId)
    }

    suspend fun markAllRead(userId: String) {
        firestoreDataSource.markAllNotificationsRead(userId)
    }
}
