package fm.corus.android.service

import android.content.Intent
import android.net.Uri

sealed class DeepLinkDestination {
    data class Profile(val userId: String) : DeepLinkDestination()
    data class ProfileByUsername(val username: String) : DeepLinkDestination()
    data class Post(val postId: String) : DeepLinkDestination()
    data class PostComment(val postId: String, val commentId: String) : DeepLinkDestination()
    data class Thread(val threadId: String, val otherUserId: String = "") : DeepLinkDestination()
    data class Hashtag(val tag: String) : DeepLinkDestination()
    data object Club : DeepLinkDestination()

    /**
     * A catalog page named by a public URL. [key] is exactly what the path
     * carried — an id or a clean-URL slug — because only the server can tell a
     * slug what it names, and reading it here would be a second slug map.
     */
    data class Entity(val segment: EntitySegment, val key: String) : DeepLinkDestination()

    fun analyticsType(): String = when (this) {
        is Profile -> "profile"
        is ProfileByUsername -> "profile"
        is Post -> "post"
        is PostComment -> "post_comment"
        is Thread -> "thread"
        is Hashtag -> "hashtag"
        is Club -> "club"
        is Entity -> segment.segment
    }
}

object DeepLinkHandler {
    fun parse(intent: Intent?): DeepLinkDestination? {
        val uri = intent?.data ?: return null
        return parse(uri)
    }

    fun parse(uri: Uri): DeepLinkDestination? {
        return when (uri.scheme) {
            "corus" -> parseCorusScheme(uri)
            "https" -> when (uri.host) {
                // corus.fm hosts the public pages, and every one of them has a
                // screen in the app.
                "corus.fm" -> parsePublicUrl(uri)
                // app.corus.fm IS the web app. The Club paywall is the one path
                // worth taking over, because the app is where the purchase can
                // actually happen; everything else there must stay in the browser
                // rather than be answered by a second copy of the same screen.
                "app.corus.fm" -> if (uri.pathSegments == listOf("settings", "club")) {
                    DeepLinkDestination.Club
                } else {
                    null
                }
                else -> null
            }
            else -> null
        }
    }

    private fun parseCorusScheme(uri: Uri): DeepLinkDestination? {
        return when (uri.host) {
            "profile" -> uri.pathSegments.firstOrNull()?.let { DeepLinkDestination.Profile(it) }
            "post" -> uri.pathSegments.firstOrNull()?.let { DeepLinkDestination.Post(it) }
            "thread" -> uri.pathSegments.firstOrNull()?.let { DeepLinkDestination.Thread(it) }
            "hashtag" -> uri.pathSegments.firstOrNull()?.let { DeepLinkDestination.Hashtag(it) }
            "club" -> DeepLinkDestination.Club
            else -> entityDestination(uri.host, uri.pathSegments.firstOrNull())
        }
    }

    private fun entityDestination(segment: String?, key: String?): DeepLinkDestination? {
        val entitySegment = segment?.let { EntitySegment.from(it) } ?: return null
        if (key.isNullOrEmpty()) return null
        return DeepLinkDestination.Entity(entitySegment, key)
    }

    private fun parsePublicUrl(uri: Uri): DeepLinkDestination? {
        val segments = uri.pathSegments
        if (segments.isEmpty()) return null
        return when (segments[0]) {
            "post" -> segments.getOrNull(1)?.let { DeepLinkDestination.Post(it) }
            "u" -> segments.getOrNull(1)?.let { DeepLinkDestination.ProfileByUsername(it) }
            else -> entityDestination(segments[0], segments.getOrNull(1))
        }
    }

    /**
     * Parse push notification data payload into a navigation destination.
     * Matches the notification types used by iOS DeepLinkManager.
     */
    fun parseNotificationData(data: Map<String, String>): DeepLinkDestination? {
        val type = data["type"] ?: return fallbackParse(data)
        return when (type) {
            "follow", "contact_joined" -> {
                val userId = data["fromUserId"].orEmpty().ifEmpty { data["userId"].orEmpty() }
                if (userId.isNotEmpty()) DeepLinkDestination.Profile(userId) else null
            }
            "message" -> data["threadId"]?.let {
                DeepLinkDestination.Thread(it, data["fromUserId"] ?: data["userId"] ?: "")
            }
            "comment", "reply", "mention", "comment_like" -> {
                val postId = data["postId"] ?: return fallbackParse(data)
                val commentId = data["commentId"]
                if (commentId != null) DeepLinkDestination.PostComment(postId, commentId)
                else DeepLinkDestination.Post(postId)
            }
            "like", "save", "new_post", "trending", "play_milestone" -> data["postId"]?.let { DeepLinkDestination.Post(it) }
            "taste_match" -> {
                val subtype = data["subtype"]
                val postId = data["postId"]
                val userId = data["fromUserId"] ?: data["userId"]
                if ((subtype == "activity_song" || subtype == "activity_film") && !postId.isNullOrEmpty()) {
                    DeepLinkDestination.Post(postId)
                } else if (!userId.isNullOrEmpty()) {
                    DeepLinkDestination.Profile(userId)
                } else {
                    null
                }
            }
            else -> fallbackParse(data)
        }
    }

    private fun fallbackParse(data: Map<String, String>): DeepLinkDestination? {
        data["threadId"]?.takeIf { it.isNotEmpty() }?.let {
            return DeepLinkDestination.Thread(
                it,
                data["fromUserId"]?.takeIf { v -> v.isNotEmpty() }
                    ?: data["userId"]?.takeIf { v -> v.isNotEmpty() }
                    ?: "",
            )
        }
        data["postId"]?.takeIf { it.isNotEmpty() }?.let { return DeepLinkDestination.Post(it) }
        data["fromUserId"]?.takeIf { it.isNotEmpty() }?.let { return DeepLinkDestination.Profile(it) }
        data["userId"]?.takeIf { it.isNotEmpty() }?.let { return DeepLinkDestination.Profile(it) }
        return null
    }
}
