package fm.corus.android.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MessageType
import fm.corus.android.data.model.TrackSource
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    private val storageDataSource: FirebaseStorageDataSource,
    private val firestore: FirebaseFirestore,
) {
    // Emits a threadId when the caller leaves a group, so the inbox can drop the
    // row immediately (the live snapshot merge only adds/updates, never removes).
    private val _leftThreads = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val leftThreads: SharedFlow<String> = _leftThreads.asSharedFlow()

    // Durable companion to `leftThreads`: thread IDs the caller just left, each
    // with an expiry. The one-shot emit above removes the row once; this set lets
    // the inbox keep filtering it while a stale Firestore cache snapshot (whose
    // mirror doc hasn't yet learned of the server delete) could otherwise re-add
    // it via the live-merge's async profile resolution. Entries expire after the
    // delete has surely propagated, so a later re-add to the same thread still
    // shows. Lazily pruned on read.
    private val leftThreadExpiry = ConcurrentHashMap<String, Long>()

    /** Snapshot of thread IDs the user recently left (expired entries dropped). */
    fun recentlyLeftThreadIds(): Set<String> {
        val now = System.currentTimeMillis()
        leftThreadExpiry.entries.removeAll { it.value <= now }
        return leftThreadExpiry.keys.toSet()
    }

    /**
     * Last-rendered inbox, kept alive across [ThreadListViewModel] instances. The
     * inbox screen is a `navigate()` / `popBackStack()` destination, so its
     * ViewModel (and its threads) are discarded every time the user leaves and
     * returns, forcing a cold `listThreads` fetch behind the skeleton on each
     * reopen. Seeding a fresh ViewModel from this snapshot renders the last-known
     * inbox instantly and reconciles in place (no skeleton). Keyed by `userId` so
     * a different signed-in user never inherits the previous user's threads, which
     * also means there's nothing to clear on sign-out.
     */
    data class CachedInbox(
        val userId: String,
        val threads: List<CymbalThread>,
        val nextCursor: Long?,
        val hasMore: Boolean,
    )

    @Volatile
    var cachedInbox: CachedInbox? = null
        private set

    fun cacheInbox(snapshot: CachedInbox) { cachedInbox = snapshot }

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

    /** Edit one of the caller's own text messages (server-enforced 15-min window). */
    suspend fun editMessage(threadId: String, messageId: String, text: String) {
        cloudFunctions.editMessage(threadId, messageId, text)
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

    /**
     * Share a post to a DM. Mirrors iOS: the message stores only `sharedPostId`
     * (no track/film denormalization). The recipient's thread renderer resolves
     * the post by id to build the card and deep-links to post detail on tap.
     */
    suspend fun sendSharedPostMessage(
        threadId: String,
        fromUserId: String,
        postId: String,
        text: String = "",
        clientMessageId: String? = null,
    ) {
        cloudFunctions.sendMessage(
            threadId = threadId,
            fromUserId = fromUserId,
            text = text,
            type = "sharedPost",
            sharedPostId = postId,
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

    // ── Group messaging ──

    suspend fun createGroupThread(participantIds: List<String>, name: String? = null, photoURL: String? = null): String =
        cloudFunctions.createGroupThread(participantIds, name, photoURL)

    suspend fun addGroupMembers(threadId: String, userIds: List<String>) =
        cloudFunctions.addGroupMembers(threadId, userIds)

    suspend fun removeGroupMember(threadId: String, userId: String) =
        cloudFunctions.removeGroupMember(threadId, userId)

    suspend fun leaveGroup(threadId: String) {
        cloudFunctions.leaveGroup(threadId)
        leftThreadExpiry[threadId] = System.currentTimeMillis() + 30_000L
        _leftThreads.tryEmit(threadId)
    }

    suspend fun renameGroup(threadId: String, name: String) = cloudFunctions.renameGroup(threadId, name)

    suspend fun setGroupPhoto(threadId: String, photoURL: String) = cloudFunctions.setGroupPhoto(threadId, photoURL)

    /** Upload a group photo to message media storage and return its download URL. */
    suspend fun uploadGroupPhoto(userId: String, threadId: String, imageData: ByteArray): String {
        val photoId = java.util.UUID.randomUUID().toString()
        return storageDataSource.uploadMessageImage(userId, threadId, photoId, imageData)
    }

    suspend fun checkGroupAddable(userIds: List<String>, threadId: String? = null): Map<String, CloudFunctionsDataSource.GroupAddability> =
        cloudFunctions.checkGroupAddable(userIds, threadId)

    /**
     * Advertise that this build supports group chat by deep-merging
     * `settings.capabilities.groupChat = true` onto the caller's own user doc
     * (allowed by the self-writable settings rule). Best-effort; lets the backend
     * gate who is addable to a group. Safe to call once per signed-in session.
     */
    suspend fun advertiseGroupMessagingCapability(userId: String) {
        try {
            firestore.collection("users_v2").document(userId)
                .set(
                    mapOf("settings" to mapOf("capabilities" to mapOf("groupChat" to true))),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
        } catch (_: Exception) {
            // best-effort; a later launch retries
        }
    }

    /** Live group metadata from the `threads/{threadId}` root doc, so the thread
     *  screen knows it's a group plus its name/photo/members/creator. */
    data class GroupThreadInfo(
        val isGroup: Boolean,
        val name: String?,
        val photoURL: String?,
        val memberIds: List<String>,
        val createdBy: String?,
    )

    fun listenToGroupThreadInfo(threadId: String): Flow<GroupThreadInfo?> = callbackFlow {
        val registration = firestore
            .collection("threads")
            .document(threadId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null); return@addSnapshotListener
                }
                val data = snapshot.data ?: return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val memberIds = (data["participantIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                trySend(
                    GroupThreadInfo(
                        isGroup = data["type"] == "group",
                        name = data["name"] as? String,
                        photoURL = data["photoURL"] as? String,
                        memberIds = memberIds,
                        createdBy = data["createdBy"] as? String,
                    )
                )
            }
        awaitClose { registration.remove() }
    }

    fun listenToMessages(threadId: String): Flow<List<CymbalMessage>> = callbackFlow {
        val registration = firestore
            .collection("threads")
            .document(threadId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            // limitToLast returns the NEWEST 200 (tail of the ascending order),
            // still oldest-first. A plain limit(200) returns the OLDEST 200, so
            // once a thread passes 200 messages the newest ones fall outside the
            // window and never appear (chat looks blank; only old messages show).
            .limitToLast(200)
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
     * Live snapshot of the first page of the caller's inbox, read straight from
     * `users_v2/{uid}/threads` (ordered by `updatedAt`, newest first). Lets the
     * inbox preview/timestamp/unread update in real time, while the paginated
     * `listThreadsPage` callable still loads older threads on scroll.
     *
     * These docs carry only `otherUserId` (not the resolved profile), so the
     * caller keeps the already-resolved `otherUser` for known threads and looks
     * up profiles only for genuinely new ones.
     */
    fun listenToThreadSummaries(userId: String, limit: Long): Flow<List<CymbalThread>> = callbackFlow {
        val registration = firestore
            .collection("users_v2")
            .document(userId)
            .collection("threads")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val summaries = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    parseThreadSummary(doc.id, data)
                }
                trySend(summaries)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Parse a raw `users_v2/{uid}/threads` doc into a CymbalThread with no
     * `otherUser` (the doc only stores `otherUserId`). Timestamps are real
     * Firestore `Timestamp`s here, unlike the callable which serializes millis.
     */
    private fun parseThreadSummary(id: String, data: Map<String, Any?>): CymbalThread {
        val ts = data["lastMessageAt"] as? Timestamp ?: data["updatedAt"] as? Timestamp
        @Suppress("UNCHECKED_CAST")
        val memberIds = (data["memberIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return CymbalThread(
            id = id,
            otherUser = null,
            otherUserId = data["otherUserId"] as? String ?: "",
            lastMessageText = data["lastMessageText"] as? String ?: "",
            lastMessageType = MessageType.from(data["lastMessageType"] as? String),
            lastMessageAt = ts?.toDate() ?: Date(0),
            lastMessageFromUserId = data["lastMessageFromUserId"] as? String,
            unreadCount = (data["unreadCount"] as? Number)?.toInt() ?: 0,
            isGroup = data["type"] == "group",
            groupName = (data["groupName"] ?: data["name"]) as? String,
            groupPhotoURL = (data["groupPhotoURL"] ?: data["photoURL"]) as? String,
            memberIds = memberIds,
            createdBy = data["createdBy"] as? String,
        )
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
