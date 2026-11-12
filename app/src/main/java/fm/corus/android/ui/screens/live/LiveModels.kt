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
) {
    val id get() = song.id
    val duration get() = song.durationMs
    val spotifyUri: String?
        get() = song.spotifyURI.takeIf(spotifyUriPattern::matches)
            ?: song.id.takeIf { song.source == TrackSource.SPOTIFY && spotifyIdPattern.matches(it) }
                ?.let { "spotify:track:$it" }

    fun fields(): Map<String, Any> = buildMap {
        put("id", id)
        put("title", song.name)
        put("artist", song.artistName)
        put("source", song.source.raw)
        put("durationMs", duration)
        song.isrc?.let { put("isrc", it) }
        song.appleMusicId?.let { put("appleMusicId", it) }
        spotifyUri?.let { put("spotifyURI", it) }
        song.albumArtURL?.let { put("artwork", it) }
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
            )
        }
    }
}

internal data class LivePerson(val uid: String, val username: String, val avatar: String?) {
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
    val customTitle = fields.text("title")
    val title = customTitle.ifBlank { "@${host.username.removePrefix("@")}" }
    val allowDJRequests = fields["allowDJRequests"] != false
    val allowSongRequests = fields["allowSongRequests"] != false
    val status = fields.text("status")
    val current = fields["current"].liveFields()?.let(LiveTrack::parse)
    val queue = fields.rows("queue").mapNotNull(LiveTrack::parse)
    val recent = fields.rows("recent").mapNotNull(LiveTrack::parse)
    val revision = fields.number("revision")
    val queueVersion = fields.number("queueVersion")
    val endedAt = fields.number("endedAt")
    val deadline = fields.number("deadline")
    val leaseUntil = fields.number("leaseUntil")
    val startedAt = fields.number("startedAt")
    val pausedAt = (fields["pausedAt"] as? Number)?.toLong()
    val likes = fields.number("likes")
    val hearts = fields.number("heartReactions")
    val effect = fields.text("effect")
    val voiceEnabled = fields.flag("voiceEnabled")
    val voiceMode = fields.text("voiceMode")
    val voiceSession = fields["voiceSession"] as? String
    val djs = fields.rows("djs")
    val collaborative = fields["djs"] is List<*>
    val welcomeUrl = fields["welcomeMessage"].liveFields()?.get("url") as? String
    val pendingSkip = fields["pendingSkip"].liveFields()?.text("id")
    val talkRequests = fields["talkRequests"].liveFields()?.values.orEmpty().mapNotNull { it.liveFields() }
    val currentDJ = fields["currentDJ"].liveFields()?.let(LivePerson::parse)
    val hostBackgroundUntil = fields.number("hostBackgroundUntil")
    val setlist get() = recent.asReversed() + listOfNotNull(current?.copy(playedBy = currentDJ ?: host))

    fun musicShouldPlay(now: Long): Boolean =
        active(now) && status == "playing" && current != null &&
            pausedAt == null && voiceMode != "talk" && deadline > now &&
            (hostBackgroundUntil == 0L || now < deadline)

    fun active(now: Long): Boolean = status != "ended" && leaseUntil > now

    fun position(now: Long): Long =
        ((pausedAt ?: now) - startedAt).coerceIn(0, (current?.duration ?: 1).toLong() - 1)
}

internal data class LiveMessage(val id: String, val fields: LiveFields) {
    val author = LivePerson.parse(fields)
    val text = fields.text("text")
    val kind = fields.text("kind")
    val gifUrl = fields["gifURL"] as? String
}

internal data class LiveAccess(
    val collaboration: Boolean,
    val confirmedSkip: Boolean,
    val hostMonetization: Boolean,
    val talkRequests: Boolean,
    val listenerCounts: Boolean,
    val saves: Boolean,
)

internal data class LiveSetup(
    val id: String,
    val title: String = "",
    val queue: List<LiveTrack> = emptyList(),
    val invitees: List<LivePerson> = emptyList(),
    val notifyFollowers: Boolean = false,
    val notificationMessage: String = "",
    val micTested: Boolean = false,
    val welcome: LiveRecording? = null,
)

internal data class LiveSuggestions(
    val recent: List<LiveTrack> = emptyList(),
    val saved: List<LiveTrack> = emptyList(),
    val trending: List<LiveTrack> = emptyList(),
)

internal sealed interface LiveSaveItem {
    val id: String

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
