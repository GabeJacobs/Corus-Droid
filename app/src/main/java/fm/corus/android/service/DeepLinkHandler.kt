package fm.corus.android.service

import android.content.Intent
import android.net.Uri

sealed class DeepLinkDestination {
    data class Profile(val userId: String) : DeepLinkDestination()
    data class ProfileByUsername(val username: String) : DeepLinkDestination()
    data class Post(val postId: String) : DeepLinkDestination()
    data class Thread(val threadId: String, val otherUserId: String = "") : DeepLinkDestination()
    data class Hashtag(val tag: String) : DeepLinkDestination()
}

object DeepLinkHandler {
    fun parse(intent: Intent?): DeepLinkDestination? {
        val uri = intent?.data ?: return null
        return parse(uri)
    }

    fun parse(uri: Uri): DeepLinkDestination? {
        return when (uri.scheme) {
            "corus" -> parseCorusScheme(uri)
            "https" -> if (uri.host == "corus.fm") parseWebUrl(uri) else null
            else -> null
        }
    }

    private fun parseCorusScheme(uri: Uri): DeepLinkDestination? {
        return when (uri.host) {
            "profile" -> uri.pathSegments.firstOrNull()?.let { DeepLinkDestination.Profile(it) }
            "post" -> uri.pathSegments.firstOrNull()?.let { DeepLinkDestination.Post(it) }
            "thread" -> uri.pathSegments.firstOrNull()?.let { DeepLinkDestination.Thread(it) }
            "hashtag" -> uri.pathSegments.firstOrNull()?.let { DeepLinkDestination.Hashtag(it) }
            else -> null
        }
    }

    private fun parseWebUrl(uri: Uri): DeepLinkDestination? {
        val segments = uri.pathSegments
        if (segments.isEmpty()) return null
        return when (segments[0]) {
            "post" -> segments.getOrNull(1)?.let { DeepLinkDestination.Post(it) }
            "u" -> segments.getOrNull(1)?.let { DeepLinkDestination.ProfileByUsername(it) }
            else -> null
        }
    }

    /**
     * Parse push notification data payload into a navigation destination.
     * Matches the notification types used by iOS DeepLinkManager.
     */
    fun parseNotificationData(data: Map<String, String>): DeepLinkDestination? {
        val type = data["type"] ?: return fallbackParse(data)
        return when (type) {
            "follow" -> data["userId"]?.let { DeepLinkDestination.Profile(it) }
            "message" -> data["threadId"]?.let { DeepLinkDestination.Thread(it, data["userId"] ?: "") }
            "comment", "reply", "mention", "comment_like" -> data["postId"]?.let { DeepLinkDestination.Post(it) }
            "like", "save", "new_post" -> data["postId"]?.let { DeepLinkDestination.Post(it) }
            else -> fallbackParse(data)
        }
    }

    private fun fallbackParse(data: Map<String, String>): DeepLinkDestination? {
        data["threadId"]?.let { return DeepLinkDestination.Thread(it, data["userId"] ?: "") }
        data["postId"]?.let { return DeepLinkDestination.Post(it) }
        data["userId"]?.let { return DeepLinkDestination.Profile(it) }
        return null
    }
}
