package fm.corus.android.ui.screens.live

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackSource

internal typealias LiveFields = Map<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun Any?.liveFields(): LiveFields? = this as? Map<String, Any?>

internal fun LiveFields.text(key: String): String = (get(key) as? String).orEmpty()
internal fun LiveFields.number(key: String): Long = (get(key) as? Number)?.toLong() ?: 0L
internal fun LiveFields.flag(key: String): Boolean = get(key) == true
internal fun LiveFields.rows(key: String): List<LiveFields> =
    (get(key) as? List<*>)?.mapNotNull { it.liveFields() }.orEmpty()

internal data class LiveTrack(
    val song: CymbalTrack,
    val playRevision: Long = 0,
    val playedBy: LivePerson? = null,
    val likes: Long? = null,
) {
    val id get() = song.id
    val duration get() = song.durationMs
    val playsFromSource get() = song.source == TrackSource.SOUNDCLOUD || song.source == TrackSource.BANDCAMP
    val spotifyUri: String?
        get() = song.spotifyURI.takeIf(spotifyUriPattern::matches)
            ?: song.id.takeIf { song.source == TrackSource.SPOTIFY && spotifyIdPattern.matches(it) }
                ?.let { "spotify:track:$it" }

    fun fields(): Map<String, Any> = buildMap {
        put("id", id)
        put("title", song.name)
        put("artist", song.artistName)
        if (song.artistIds.isNotEmpty()) put("artistIds", song.artistIds)
        put("source", song.source.raw)
        put("durationMs", duration)
        song.isrc?.let { put("isrc", it) }
        song.appleMusicId?.let { put("appleMusicId", it) }
        spotifyUri?.let { put("spotifyURI", it) }
        song.liveDisplayArtwork?.let { put("artwork", it) }
        song.albumName.takeIf(String::isNotBlank)?.let { put("album", it) }
        song.soundcloudId?.let { put("soundcloudId", it) }
        song.soundcloudPermalinkUrl?.let { put("soundcloudPermalinkUrl", it) }
        song.bandcampId?.let { put("bandcampId", it) }
        song.bandcampUrl?.let { put("bandcampUrl", it) }
    }

    companion object {
        private val spotifyUriPattern = Regex("spotify:track:[A-Za-z0-9]{22}")
        private val spotifyIdPattern = Regex("[A-Za-z0-9]{22}")

        fun parse(fields: LiveFields): LiveTrack? {
            val id = fields.text("id").takeIf(String::isNotBlank) ?: return null
            val title = fields.text("title").takeIf(String::isNotBlank) ?: return null
            val duration = fields.number("durationMs").takeIf { it in 1..Int.MAX_VALUE } ?: return null
            return LiveTrack(
                CymbalTrack(
                    id = id,
                    name = title,
                    artistName = fields.text("artist"),
                    artistIds = (fields["artistIds"] as? List<*>)
                        ?.mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }.orEmpty(),
                    albumName = fields.text("album"),
                    albumArtURL = fields["artwork"] as? String,
                    spotifyURI = fields.text("spotifyURI"),
                    durationMs = duration.toInt(),
                    isrc = fields["isrc"] as? String,
                    appleMusicId = fields["appleMusicId"] as? String,
                    source = TrackSource.fromRaw(fields.text("source")),
                    soundcloudId = fields["soundcloudId"] as? String,
                    soundcloudPermalinkUrl = fields["soundcloudPermalinkUrl"] as? String,
                    bandcampId = fields["bandcampId"] as? String,
                    bandcampUrl = fields["bandcampUrl"] as? String,
                ),
                fields.number("playRevision"),
                fields["playedBy"].liveFields()?.let(LivePerson::parse),
                (fields["likes"] as? Number)?.toLong(),
            )
        }
    }
}

internal data class LivePerson(
    val uid: String,
    val username: String,
    val avatar: String?,
    val profile: fm.corus.android.data.model.CymbalUser? = null,
) {
    companion object {
        fun parse(fields: LiveFields): LivePerson = LivePerson(
            fields.text("uid"),
            fields.text("username").ifBlank { fields.text("displayName") },
            (fields["avatar"] ?: fields["avatarURL"]) as? String,
        )
    }
}

internal data class LiveRoom(val id: String, private val fields: LiveFields) {
    val host = LivePerson(
        fields.text("hostId"),
        fields.text("hostUsername").ifBlank { fields.text("hostName") },
        fields["hostAvatar"] as? String,
    )
    val hostHandle = fields.text("hostUsername").trim().removePrefix("@")
        .takeUnless { it.isBlank() || it == "…" }?.let { "@$it" }
    val customTitle = fields.text("title")
    val title = customTitle.ifBlank { "@${host.username.removePrefix("@")}" }
    val chatEnabled = fields["chatEnabled"] != false
    val allowDJRequests = fields["allowDJRequests"] != false
    val allowSongRequests = fields["allowSongRequests"] != false
    val status = fields.text("status")
    val current = fields["current"].liveFields()?.let(LiveTrack::parse)
    val queue = fields.rows("queue").mapNotNull(LiveTrack::parse)
    val recent = fields.rows("recent").mapNotNull(LiveTrack::parse)
    val revision = fields.number("revision")
    val queueVersion = fields.number("queueVersion")
    private data class QueuedPick(val djUid: String, val order: Long, val track: LiveTrack)

    private val nextPick: QueuedPick?
        get() {
            val cursor = fields.number("currentDJOrder")
            val guestPicks = djs.mapNotNull { dj ->
                val track = dj.rows("queue").firstOrNull()?.let(LiveTrack::parse) ?: return@mapNotNull null
                QueuedPick(dj.text("uid"), dj.number("order"), track)
            }
            return guestPicks.firstOrNull { it.order > cursor }
                ?: queue.firstOrNull()?.let { QueuedPick(host.uid, 0, it) }
                ?: guestPicks.firstOrNull()
        }
    val nextTrack get() = nextPick?.track
    val nextDJUid get() = if (djs.isEmpty()) null else nextPick?.djUid
    val playingDJUid get() = (currentDJ?.uid ?: host.uid).takeIf {
        status == "playing" && current != null &&
            voiceMode != "talk" && pausedAt == null && pendingSkip == null
    }
    val endedAt = fields.number("endedAt")
    val deadline = fields.number("deadline")
    val leaseUntil = fields.number("leaseUntil")
    val naturalAdvance = fields.flag("naturalAdvance")
    val startedAt = fields.number("startedAt")
    val pausedAt = (fields["pausedAt"] as? Number)?.toLong()
    val likes = fields.number("likes")
    val hearts = fields.number("heartReactions")
    val effect = fields.text("effect")
    val talkAfterSongArmed = (fields["talkAfterSongRevision"] as? Number)?.toLong() == revision
    val voiceEnabled = fields.flag("voiceEnabled")
    val voiceMode = fields.text("voiceMode")
    val talkDeadline = fields.number("talkDeadline").takeIf { it > 0 }
    val talkRoomLimitReached = fields.flag("talkRoomLimitReached")
    val talkDailyResetAt = fields.number("talkDailyResetAt").takeIf { it > 0 }

    fun talkLimitReached(now: Long): Boolean = talkRoomLimitReached || (talkDailyResetAt ?: 0) > now
    val voiceSession = fields["voiceSession"] as? String
    val djs = fields.rows("djs")
    val talkSpeakerUid: String?
        get() {
            if (voiceMode != "talk") return null
            return (fields["talkStartedBy"] as? String)
                ?: host.uid.takeIf { fields.number("voiceProtocol") != 2L || djs.isEmpty() }
        }
    fun djHandle(uid: String): String? {
        if (uid == host.uid) return hostHandle
        return djs.firstOrNull { it.text("uid") == uid }?.let(LivePerson::parse)
            ?.username?.trimStart('@')?.takeIf { it.isNotBlank() }?.let { "@$it" }
    }
    val collaborative = fields["djs"] is List<*>
    val welcomeUrl = fields["welcomeMessage"].liveFields()?.get("url") as? String
    val pendingSkip = fields["pendingSkip"].liveFields()?.text("id")
    val talkRequests = fields["talkRequests"].liveFields()?.values.orEmpty().mapNotNull { it.liveFields() }
    val currentDJ = fields["currentDJ"].liveFields()?.let(LivePerson::parse)
    val hostBackgroundUntil = fields.number("hostBackgroundUntil")
    val hostWaitStartsAt get() = hostBackgroundUntil.takeIf { it > 0L }?.let {
        if (current == null || pausedAt != null) 0L else deadline
    }

    fun matchesFollowing(following: Set<String>?): Boolean =
        following == null || host.uid in following || djs.any { it.text("uid") in following }

    fun miniPlayerTrack(now: Long): LiveTrack? = current?.takeIf {
        status == "playing" && hostWaitStartsAt?.let { now >= it } != true &&
            (pausedAt != null || now < deadline)
    }

    fun canLikeFromMiniPlayer(uid: String?, now: Long): Boolean {
        val track = miniPlayerTrack(now) ?: return false
        return uid != null && uid != (currentDJ?.uid ?: track.playedBy?.uid ?: host.uid)
    }

    fun remoteHeartsSince(previous: LiveRoom, ownSender: String): Long {
        if (fields["heartReactions"] !is Number) {
            return if (revision == previous.revision) (likes - previous.likes).coerceAtLeast(0) else 0
        }
        val ownHearts = fields.rows("recentHeartBatches").sumOf { batch ->
            if (batch.text("sender") != ownSender) return@sumOf 0L
            val total = batch.number("total")
            val batchStart = total - batch.number("count")
            (minOf(hearts, total) - maxOf(previous.hearts, batchStart)).coerceAtLeast(0)
        }
        return (hearts - previous.hearts - ownHearts).coerceAtLeast(0)
    }

    val setlist get() = recent.asReversed() + listOfNotNull(current?.copy(playedBy = currentDJ ?: host))

    fun musicShouldPlay(now: Long): Boolean =
        active(now) && status == "playing" && current != null &&
            pausedAt == null && voiceMode != "talk" && deadline > now &&
            (hostBackgroundUntil == 0L || now < deadline)

    fun active(now: Long): Boolean = status != "ended" && leaseUntil > now

    fun position(now: Long): Long =
        ((pausedAt ?: now) - startedAt).coerceIn(0, (current?.duration ?: 1).toLong() - 1)
}

internal data class LiveMessage(
    val id: String,
    val fields: LiveFields,
    val author: LivePerson = LivePerson.parse(fields),
) {
    val text = fields.text("text")
    val kind = fields.text("kind")
    val isSongLikeNotice get() = kind == "songLiked"
    val likeNoticeHandle get() = fields.text("displayName").takeIf { it.startsWith("@") }
    inline fun likeNoticeText(localized: (String?) -> String): String =
        if (fields.number("likeCount") > 1 && text.isNotEmpty()) text else localized(likeNoticeHandle)
    val gifUrl = fields["gifURL"] as? String
    val supportsReactions get() = kind.isEmpty() && (text.isNotEmpty() || gifUrl != null)
    val chatReactionEmojis: Set<String>
        get() = fields["chatReactions"].liveFields()?.keys.orEmpty()
    fun reactionUsers(emoji: String): List<String> =
        (fields["chatReactions"].liveFields()?.get(emoji) as? List<*>)?.filterIsInstance<String>().orEmpty()
    val createdAt = fields.number("createdAt")
    val isEdited get() = fields.number("editedAt") > 0
    val requestedTrack = fields["requestedTrack"].liveFields()?.let(LiveTrack::parse)
    val requestStatus = fields.text("requestStatus").ifBlank {
        if (kind == "songRequest" || kind == "djRequest") "pending" else ""
    }
    val isPrivateDJRequest get() = kind == "djRequest" && id.startsWith("private-dj-request:")

    fun requestTarget(): LiveFields = if (isPrivateDJRequest) {
        mapOf("targetUid" to author.uid, "requestCreatedAt" to createdAt)
    } else {
        mapOf("targetUid" to author.uid, "requestMessageId" to id)
    }
}

internal data class PendingLiveLikeNotice(val message: LiveMessage, val batchId: String? = null) {
    fun matches(published: LiveMessage): Boolean =
        published.isSongLikeNotice &&
            published.fields.number("revision") == message.fields.number("revision") &&
            (published.author.uid == message.author.uid ||
                batchId != null && (published.fields["reactionBatchIds"] as? List<*>)?.contains(batchId) == true)
}

internal data class LiveAccess(
    val collaboration: Boolean,
    val confirmedSkip: Boolean,
    val hostMonetization: Boolean,
    val talkRequests: Boolean,
    val listenerCounts: Boolean,
    val saves: Boolean,
    val sourceTracks: Boolean,
    val appleMusicHostAutoplay: Boolean = false,
    val appleMusicHostEarlyAdvance: Boolean = false,
)

internal data class LiveSearchPage(val tracks: List<LiveTrack>, val nextOffset: Int?)

internal data class LiveSetup(
    val id: String,
    val title: String = "",
    val queue: List<LiveTrack> = emptyList(),
    val invitees: List<LivePerson> = emptyList(),
    val notifyFollowers: Boolean = false,
    val notificationMessage: String = "",
    val micTested: Boolean = false,
    val welcome: LiveRecording? = null,
    val loadingFirstSong: Boolean = false,
) {
    val firstSongArtwork: String? get() = queue.firstOrNull()?.song?.liveDisplayArtwork
}

internal data class LiveSuggestions(
    val recent: List<LiveTrack> = emptyList(),
    val saved: List<LiveTrack> = emptyList(),
    val trending: List<LiveTrack> = emptyList(),
)

internal sealed interface LiveSaveItem {
    val id: String
    val feedId get() = if (this is Track) "live-save:$id" else id

    data class Post(val post: fm.corus.android.data.model.CymbalPost) : LiveSaveItem {
        override val id get() = post.id
    }

    data class Track(
        override val id: String,
        val track: LiveTrack,
        val pickedBy: LivePerson?,
        val roomId: String,
        val roomTitle: String,
    ) : LiveSaveItem
}

internal data class LiveSavesPage(val items: List<LiveSaveItem>, val cursor: LiveFields?)

internal val CymbalTrack.liveDisplayArtwork: String?
    get() = (albumArtLargeURL?.takeIf(String::isNotBlank) ?: albumArtURL?.takeIf(String::isNotBlank))
        ?.let { fm.corus.android.domain.collectionArtworkUrl(it) }

internal fun CymbalTrack.withLiveDisplayArtwork(): CymbalTrack =
    copy(albumArtLargeURL = liveDisplayArtwork)

internal data class LiveInvitePush(val roomId: String, val recipientId: String?, val expiresAt: Long?)

internal fun LiveFields.inviteKey(): Pair<String, Long> = text("roomId") to number("expiresAt")
