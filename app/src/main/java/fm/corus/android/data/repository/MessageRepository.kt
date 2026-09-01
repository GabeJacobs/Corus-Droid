package fm.corus.android.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.InboxSearchResult
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

/**
 * The inbox subscription was refused rather than dropped: this query is not the
 * caller's to read — signed out, or the rules no longer allow it — so attaching
 * again cannot succeed. Distinct from every other listener failure, which is a
 * transient drop worth retrying.
 */
class InboxSubscriptionRefused(cause: Throwable) : Exception(cause)

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
    suspend fun searchThreads(userId: String, query: String, limit: Int = 30): InboxSearchResult {
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

    suspend fun sendSharedArtistMessage(
        threadId: String,
        fromUserId: String,
        text: String = "",
        artistId: String,
        name: String,
        imageUrl: String?,
        clientMessageId: String? = null,
    ) {
        cloudFunctions.sendMessage(
            threadId = threadId,
            fromUserId = fromUserId,
            text = text,
            type = "sharedArtist",
            artistId = artistId,
            artistName = name,
            artistImageURL = imageUrl,
            clientMessageId = clientMessageId,
        )
    }

    suspend fun sendSharedAlbumMessage(
        threadId: String,
        fromUserId: String,
        text: String = "",
        albumId: String,
        title: String,
        artistName: String,
        coverUrl: String?,
        year: String?,
        clientMessageId: String? = null,
    ) {
        cloudFunctions.sendMessage(
            threadId = threadId,
            fromUserId = fromUserId,
            text = text,
            type = "sharedAlbum",
            albumId = albumId,
            albumTitle = title,
            albumArtistName = artistName,
            albumCoverURL = coverUrl,
            albumYear = year,
            clientMessageId = clientMessageId,
        )
    }

    suspend fun sendSharedDirectorMessage(
        threadId: String,
        fromUserId: String,
        text: String = "",
        directorId: String,
        name: String,
        imageUrl: String?,
        clientMessageId: String? = null,
    ) {
        cloudFunctions.sendMessage(
            threadId = threadId,
            fromUserId = fromUserId,
            text = text,
            type = "sharedDirector",
            directorId = directorId,
            directorName = name,
            directorImageURL = imageUrl,
            clientMessageId = clientMessageId,
        )
    }

    suspend fun sendSharedProfileMessage(
        threadId: String,
        fromUserId: String,
        text: String = "",
        sharedUserId: String,
        username: String,
        displayName: String?,
        avatarUrl: String?,
        clientMessageId: String? = null,
    ) {
        cloudFunctions.sendMessage(
            threadId = threadId,
            fromUserId = fromUserId,
            text = text,
            type = "sharedProfile",
            sharedUserId = sharedUserId,
            sharedUsername = username,
            sharedDisplayName = displayName,
            sharedAvatarURL = avatarUrl,
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
            // Metadata is included so an empty *cache* miss is not treated as
            // "this thread has no messages." That snapshot arrives first on a
            // cold open (emulator and first-visit alike), the spinner dropped,
            // and a hung or failed server fetch left a blank thread. Errors
            // close the flow so the screen can show retry instead of waiting
            // forever with nothing on it.
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val messages = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    CymbalMessage.fromFirestoreDoc(doc.id, threadId, data)
                }
                if (!shouldPublishMessagesSnapshot(
                        fromCache = snapshot.metadata.isFromCache,
                        isEmpty = messages.isEmpty(),
                    )
                ) return@addSnapshotListener
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    /**
     * An empty cache snapshot means "we have never fetched this thread," not
     * "this thread is empty." Cached messages can paint immediately; a server
     * snapshot (empty or not) is the real answer.
     */
    internal fun shouldPublishMessagesSnapshot(fromCache: Boolean, isEmpty: Boolean): Boolean =
        !fromCache || !isEmpty

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
     *
     * A listener error tears the registration down for good, so it is surfaced
     * as a flow failure rather than ignored — swallowing it left the inbox with
     * no live source and no way back short of leaving the screen. The collector
     * re-subscribes, except on an [InboxSubscriptionRefused], which says so.
     */
    fun listenToThreadSummaries(userId: String, limit: Long): Flow<List<CymbalThread>> = callbackFlow {
        val registration = firestore
            .collection("users_v2")
            .document(userId)
            .collection("threads")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(subscriptionFailure(error)); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                val summaries = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    parseThreadSummary(doc.id, data)
                }
                trySend(summaries)
            }
        awaitClose { registration.remove() }
    }

    private fun subscriptionFailure(error: FirebaseFirestoreException): Throwable =
        when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> InboxSubscriptionRefused(error)
            else -> error
        }

    /**
     * Parse a raw `users_v2/{uid}/threads` doc into a CymbalThread with no
     * `otherUser` (the doc only stores `otherUserId`). Timestamps are real
     * Firestore `Timestamp`s here, unlike the callable which serializes millis.
     */
    internal fun parseThreadSummary(id: String, data: Map<String, Any?>): CymbalThread {
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
            blocked = data["blocked"] == true,
            updatedAt = (data["updatedAt"] as? Timestamp)?.toDate(),
        )
    }

    /**
     * The caller's own inbox row for one thread, as it stands right now. A null
     * [thread] is the row being absent; [fromCache] says whether that absence is
     * merely local, since a device that has never held the row cannot tell
     * "gone" from "not fetched yet".
     */
    data class ThreadRowSnapshot(
        val thread: CymbalThread?,
        val fromCache: Boolean,
    )

    /**
     * Live view of the caller's own inbox row for one conversation — the row the
     * backend itself consults before it will hand that conversation over, so it
     * is the same fact, arriving without a round trip and continuing to arrive
     * for as long as the conversation is on screen.
     *
     * Metadata changes are included because an absent row carries no data change
     * between the cache's answer and the server's, and absence only means
     * anything once the server has said it.
     */
    fun listenToThreadRow(userId: String, threadId: String): Flow<ThreadRowSnapshot> = callbackFlow {
        val registration = firestore
            .collection("users_v2")
            .document(userId)
            .collection("threads")
            .document(threadId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    // The row is not the caller's to read — signed out, or the
                    // rules refuse it. Nothing about waiting changes that, and a
                    // conversation whose row cannot be read cannot be shown.
                    trySend(ThreadRowSnapshot(thread = null, fromCache = false))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val data = snapshot.data
                trySend(
                    ThreadRowSnapshot(
                        thread = data?.let { parseThreadSummary(snapshot.id, it) },
                        fromCache = snapshot.metadata.isFromCache,
                    )
                )
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
