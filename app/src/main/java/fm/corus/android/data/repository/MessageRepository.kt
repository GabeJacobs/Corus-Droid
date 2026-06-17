package fm.corus.android.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    private val storageDataSource: FirebaseStorageDataSource,
    private val firestore: FirebaseFirestore,
) {
    suspend fun listThreads(userId: String): List<CymbalThread> {
        return cloudFunctions.listThreads(userId)
    }

    /** Ranked people the user actually shares posts with, for the share sheet. */
    suspend fun listShareRecipients(limit: Int = 12): List<CymbalUser> {
        return cloudFunctions.listShareRecipients(limit)
    }

    suspend fun listThreadsPage(
        userId: String,
        limit: Int = 30,
        startAfter: Long? = null,
    ): CloudFunctionsDataSource.ThreadListPage {
        return cloudFunctions.listThreadsPage(userId, limit, startAfter)
    }

    /** Searches the caller's full DM history server-side (not just loaded threads). */
    suspend fun searchThreads(userId: String, query: String, limit: Int = 30): List<CymbalThread> {
        return cloudFunctions.searchThreads(userId, query, limit)
    }

    suspend fun listMessages(threadId: String, limit: Int = 50, lastTimestamp: Long? = null): List<CymbalMessage> {
        return cloudFunctions.listMessages(threadId, limit, lastTimestamp)
    }

    suspend fun sendTextMessage(
        threadId: String,
        fromUserId: String,
        text: String,
        replyToMessageId: String? = null,
        replyToText: String? = null,
        replyToUserId: String? = null,
        clientMessageId: String? = null,
    ) {
        cloudFunctions.sendMessage(
            threadId = threadId,
            fromUserId = fromUserId,
            text = text,
            replyToMessageId = replyToMessageId,
            replyToText = replyToText,
            replyToUserId = replyToUserId,
            clientMessageId = clientMessageId,
        )
    }

    suspend fun toggleReaction(threadId: String, messageId: String, emoji: String) {
        cloudFunctions.toggleMessageReaction(threadId, messageId, emoji)
    }

    suspend fun sendImageMessage(threadId: String, fromUserId: String, imageData: ByteArray, clientMessageId: String? = null): String {
        val messageId = clientMessageId ?: "${System.currentTimeMillis()}"
        val url = storageDataSource.uploadMessageImage(fromUserId, threadId, messageId, imageData)
        cloudFunctions.sendMessage(threadId = threadId, fromUserId = fromUserId, text = "", type = "image", mediaURL = url, clientMessageId = clientMessageId)
        return url
    }

    suspend fun sendGifMessage(threadId: String, fromUserId: String, gifURL: String, clientMessageId: String? = null) {
        cloudFunctions.sendMessage(threadId = threadId, fromUserId = fromUserId, text = "", type = "gif", mediaURL = gifURL, clientMessageId = clientMessageId)
    }

    suspend fun sendSharedTrackMessage(
        threadId: String,
        fromUserId: String,
        text: String = "",
        track: CymbalTrack,
        clientMessageId: String? = null,
    ) {
        val isSoundCloud = track.source == TrackSource.SOUNDCLOUD
        cloudFunctions.sendMessage(
            threadId = threadId,
            fromUserId = fromUserId,
            text = text,
            type = "sharedTrack",
            trackId = track.id,
            trackName = track.name,
            artistName = track.artistName,
            artistIds = track.artistIds,
            albumName = track.albumName,
            albumArtURL = track.albumArtURL,
            albumArtLargeURL = track.albumArtLargeURL,
            spotifyURI = track.spotifyURI,
            spotifyURL = track.spotifyWebURL,
            previewUrl = track.previewUrl,
            isrc = track.isrc,
            durationMs = track.durationMs,
            source = if (isSoundCloud) "soundcloud" else null,
            soundcloudId = if (isSoundCloud) track.soundcloudId else null,
            soundcloudPermalinkUrl = if (isSoundCloud) track.soundcloudPermalinkUrl else null,
            clientMessageId = clientMessageId,
        )
    }

    suspend fun sendSharedFilmMessage(
        threadId: String,
        fromUserId: String,
        text: String = "",
        movie: CymbalMovie,
        clientMessageId: String? = null,
    ) {
        cloudFunctions.sendMessage(
            threadId = threadId,
            fromUserId = fromUserId,
            text = text,
            type = "sharedFilm",
            movieId = movie.id,
            movieTitle = movie.title,
            directorName = movie.directorName,
            directorIds = movie.directorIds,
            releaseYear = movie.year,
            posterURL = movie.posterURL,
            posterLargeURL = movie.posterLargeURL,
            tmdbWebURL = movie.tmdbWebURL,
            clientMessageId = clientMessageId,
        )
    }

    suspend fun getOrCreateThread(userId: String, otherUserId: String): String {
        return cloudFunctions.getOrCreateThread(userId, otherUserId)
    }

    suspend fun markThreadRead(threadId: String, userId: String) {
        cloudFunctions.markThreadRead(threadId, userId)
    }

    fun listenToMessages(threadId: String): Flow<List<CymbalMessage>> = callbackFlow {
        val registration = firestore
            .collection("threads")
            .document(threadId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val messages = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    CymbalMessage.fromFirestoreDoc(doc.id, threadId, data)
                }
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Emits the user's `settings.messaging.readReceiptsEnabled` setting,
     * defaulting to `true` when missing. The sender hides "read" status on
     * outgoing messages when this is `false` (mutual two-way behavior).
     */
    fun listenToReadReceiptsEnabled(userId: String): Flow<Boolean> = callbackFlow {
        val registration = firestore
            .collection("users_v2")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(true); return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val settings = snapshot.data?.get("settings") as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val messaging = settings?.get("messaging") as? Map<String, Any?>
                val enabled = messaging?.get("readReceiptsEnabled") as? Boolean ?: true
                trySend(enabled)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Emits the recipient's unread count for the thread, read from
     * `threads/{threadId}.unreadCount[otherUserId]`. Used to derive the
     * "read" boundary for messages I sent.
     */
    fun listenToRecipientUnreadCount(threadId: String, otherUserId: String): Flow<Int> = callbackFlow {
        val registration = firestore
            .collection("threads")
            .document(threadId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(0); return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val map = snapshot.data?.get("unreadCount") as? Map<String, Any?>
                val count = (map?.get(otherUserId) as? Number)?.toInt() ?: 0
                trySend(count)
            }
        awaitClose { registration.remove() }
    }
}
