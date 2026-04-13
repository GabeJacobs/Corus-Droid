package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    private val storageDataSource: FirebaseStorageDataSource,
) {
    suspend fun listThreads(userId: String): List<CymbalThread> {
        return cloudFunctions.listThreads(userId)
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
    ) {
        cloudFunctions.sendMessage(
            threadId = threadId,
            fromUserId = fromUserId,
            text = text,
            replyToMessageId = replyToMessageId,
            replyToText = replyToText,
            replyToUserId = replyToUserId,
        )
    }

    suspend fun toggleReaction(threadId: String, messageId: String, emoji: String) {
        cloudFunctions.toggleMessageReaction(threadId, messageId, emoji)
    }

    suspend fun sendImageMessage(threadId: String, fromUserId: String, imageData: ByteArray): String {
        val messageId = "${System.currentTimeMillis()}"
        val url = storageDataSource.uploadMessageImage(threadId, messageId, imageData)
        cloudFunctions.sendMessage(threadId = threadId, fromUserId = fromUserId, text = "", type = "image", mediaURL = url)
        return url
    }

    suspend fun sendGifMessage(threadId: String, fromUserId: String, gifURL: String) {
        cloudFunctions.sendMessage(threadId = threadId, fromUserId = fromUserId, text = "", type = "gif", mediaURL = gifURL)
    }

    suspend fun sendSharedTrackMessage(threadId: String, fromUserId: String, text: String, trackName: String, artistName: String, albumArtURL: String?, spotifyURL: String?) {
        cloudFunctions.sendMessage(threadId = threadId, fromUserId = fromUserId, text = text, type = "sharedTrack", trackName = trackName, artistName = artistName, albumArtURL = albumArtURL, spotifyURL = spotifyURL)
    }

    suspend fun sendSharedFilmMessage(threadId: String, fromUserId: String, text: String, movieTitle: String, directorName: String, posterURL: String?, tmdbWebURL: String?) {
        cloudFunctions.sendMessage(threadId = threadId, fromUserId = fromUserId, text = text, type = "sharedFilm", movieTitle = movieTitle, directorName = directorName, posterURL = posterURL, tmdbWebURL = tmdbWebURL)
    }

    suspend fun getOrCreateThread(userId: String, otherUserId: String): String {
        return cloudFunctions.getOrCreateThread(userId, otherUserId)
    }

    suspend fun markThreadRead(threadId: String, userId: String) {
        cloudFunctions.markThreadRead(threadId, userId)
    }
}
