package fm.corus.android.ui.screens.live

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.repository.UserRepository
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

internal class LiveRepository @Inject constructor(
    private val database: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val music: MusicSearchRepository,
    private val cloud: CloudFunctionsDataSource,
    private val storage: FirebaseStorageDataSource,
    private val users: UserRepository,
    private val posts: fm.corus.android.data.repository.PostRepository,
    private val explore: fm.corus.android.data.repository.ExploreRepository,
) {
    private val rooms get() = database.collection("corusLiveRooms")

    suspend fun call(action: String, roomId: String? = null, fields: LiveFields = emptyMap()): LiveFields {
        val payload = fields + mapOf("action" to action, "clientPlatform" to "android") +
            if (roomId == null) emptyMap() else mapOf("roomId" to roomId)
        return functions.getHttpsCallable("corusLive").call(payload).await().getData().liveFields().orEmpty()
    }

    suspend fun access(): LiveAccess? {
        val fields = call("availability", fields = mapOf(
            "savesProtocol" to 1, "djTalkRequestProtocol" to 1, "hostMonetizationProtocol" to 1,
        ))
        if (!fields.flag("available")) return null
        return LiveAccess(
            fields.flag("collaborationAvailable"),
            fields.flag("confirmedSkipAvailable"),
            fields.flag("hostMonetizationAvailable"),
            fields.flag("djTalkRequestsAvailable"),
            fields.flag("discoveryListenerCountsAvailable"),
            fields.flag("savesAvailable"),
        )
    }

    suspend fun invite(uid: String, roomId: String): LiveFields? =
        database.collection("corusLiveDJInboxes").document(uid).collection("invites")
            .document(roomId).get(Source.SERVER).await().data?.plus("roomId" to roomId)

    suspend fun room(id: String): LiveRoom? = rooms.document(id).get(Source.SERVER).await().toRoom()

    fun discovery(): Flow<List<LiveRoom>> =
        rooms.whereEqualTo("status", "playing").limit(25).snapshots { it.mapNotNull(DocumentSnapshot::toLiveRoom) }

    fun observeRoom(id: String): Flow<LiveRoom?> = callbackFlow {
        val registration = rooms.document(id).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.toRoom())
        }
        awaitClose { registration.remove() }
    }

    suspend fun hostedRoom(uid: String): LiveRoom? = coroutineScope {
        val owned = rooms.whereEqualTo("hostId", uid)
        val playing = async { owned.whereEqualTo("status", "playing").get(Source.SERVER).await().documents }
        val waiting = async { owned.whereEqualTo("status", "waiting").get(Source.SERVER).await().documents }
        val candidates = (playing.await() + waiting.await()).mapNotNull { it.toRoom() }.sortedByDescending { it.leaseUntil }
        for (room in candidates) {
            try {
                call("inspect", room.id)
                return@coroutineScope room
            } catch (error: FirebaseFunctionsException) {
                if (error.code !in setOf(FirebaseFunctionsException.Code.FAILED_PRECONDITION, FirebaseFunctionsException.Code.NOT_FOUND)) throw error
            }
        }
        null
    }

    fun messages(id: String): Flow<List<LiveMessage>> = rooms.document(id).collection("messages")
        .orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
        .snapshots { documents -> documents.asReversed().mapNotNull { doc -> doc.data?.let { LiveMessage(doc.id, it) } } }

    fun invites(uid: String): Flow<List<LiveFields>> =
        database.collection("corusLiveDJInboxes").document(uid).collection("invites")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(20)
            .snapshots { documents -> documents.mapNotNull { doc -> doc.data?.plus("roomId" to doc.id) } }

    fun roomRecords(roomId: String, collection: String): Flow<List<LiveFields>> =
        rooms.document(roomId).collection(collection)
            .snapshots { documents -> documents.mapNotNull { doc -> doc.data?.plus("uid" to doc.id) } }

    fun membership(roomId: String, uid: String): Flow<LiveFields> = ownRequest(roomId, "members", uid)

    fun ownRequest(roomId: String, collection: String, uid: String): Flow<LiveFields> = callbackFlow {
        val registration = rooms.document(roomId).collection(collection).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else trySend(snapshot?.data.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun savedIds(uid: String, ids: List<String>): Set<String> = coroutineScope {
        val collection = database.collection("users_v2").document(uid).collection("liveTrackSaves")
        ids.map { id ->
            async {
                try {
                    id.takeIf { collection.document(it).get().await().exists() }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull().toSet()
    }

    suspend fun exportSetlist(id: String): String {
        val result = functions.getHttpsCallable("exportLiveSetlist").call(mapOf("roomId" to id))
            .await().getData().liveFields().orEmpty()
        return result.text("playlistWebURL").ifBlank { error("Couldn’t export playlist.") }
    }

    suspend fun search(query: String, offset: Int): List<LiveTrack> =
        music.search(query, offset = offset, limit = 40).tracks.toLiveTracks()

    private fun List<CymbalTrack>.toLiveTracks(): List<LiveTrack> = filter { song ->
        song.durationMs >= 1_000 && song.source in listOf(TrackSource.SPOTIFY, TrackSource.APPLEMUSIC)
    }.distinctBy { it.id }.map { LiveTrack(it) }

    suspend fun lastSong(uid: String): LiveTrack? =
        posts.getProfilePosts(uid, uid, limit = 1, mediaType = "track")
            .firstOrNull()?.track?.takeIf { it.durationMs > 0 && it.spotifyURI.isNotBlank() }
            ?.let { LiveTrack(it) }

    suspend fun mixedSaves(cursor: LiveFields? = null, limit: Int = 24): LiveSavesPage {
        val fields = buildMap<String, Any> {
            put("limit", limit)
            cursor?.let { put("cursor", it) }
        }
        val result = functions.getHttpsCallable("getMixedSaves").call(fields)
            .await().getData().liveFields().orEmpty()
        val items = result.rows("items").mapNotNull { row ->
            when (row.text("kind")) {
                "liveTrack" -> row["track"].liveFields()?.let(LiveTrack::parse)?.let { track ->
                    LiveSaveItem.Track(row.text("id"), track, row["playedBy"].liveFields()?.let(LivePerson::parse),
                        row.text("roomId"), row.text("setTitle"))
                }
                "post" -> row["post"].liveFields()?.let(CymbalPost::fromCloudData)?.let { LiveSaveItem.Post(it) }
                else -> null
            }
        }
        return LiveSavesPage(items, result["cursor"].liveFields())
    }

    suspend fun suggestions(uid: String): LiveSuggestions = coroutineScope {
        val recent = async {
            posts.getProfilePosts(uid, uid, limit = 24, mediaType = "track").map { it.track }.toLiveTracks()
        }
        val saved = async {
            try {
                mixedSaves().items.mapNotNull { item ->
                    when (item) {
                        is LiveSaveItem.Track -> item.track.song
                        is LiveSaveItem.Post -> item.post.takeIf { !it.isMovie }?.track
                    }
                }.toLiveTracks()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                cloud.getSavedPosts(uid, limit = 24).filterNot { it.isMovie }.map { it.track }.toLiveTracks()
            }
        }
        val latest = recent.await()
        val savedSongs = saved.await()
        val trending = if (latest.isEmpty() && savedSongs.isEmpty()) {
            explore.fetchTrendingSongs(limit = 12).map { it.track }.toLiveTracks()
        } else emptyList()
        LiveSuggestions(latest, savedSongs, trending)
    }

    suspend fun spotifyPlaylist(link: String): Pair<String, List<LiveTrack>> {
        val uri = android.net.Uri.parse(link.trim())
        val id = uri.pathSegments.lastOrNull()
        require(uri.scheme == "https" && uri.host == "open.spotify.com" &&
            uri.pathSegments.dropLast(1).lastOrNull() == "playlist" &&
            id?.matches(Regex("[A-Za-z0-9]{22}")) == true) { "Paste a Spotify playlist link." }
        val data = functions.getHttpsCallable("corusLiveSpotifyPlaylist").call(mapOf(
            "playlistId" to id, "market" to Locale.getDefault().country.ifBlank { "US" },
        )).await().getData().liveFields().orEmpty()
        val tracks = data.rows("tracks").mapNotNull { row ->
            val duration = row.number("duration_ms").toInt()
            if (duration < 1_000 || row.text("id").isBlank() || row.text("name").isBlank()) return@mapNotNull null
            val album = row["album"].liveFields().orEmpty()
            LiveTrack(CymbalTrack(
                id = row.text("id"), name = row.text("name"),
                artistName = row.rows("artists").joinToString(", ") { it.text("name") },
                albumName = album.text("name"), albumArtURL = album.rows("images").firstOrNull()?.text("url"),
                durationMs = duration, spotifyURI = row.text("uri"),
                isrc = row["external_ids"].liveFields()?.text("isrc"),
                previewUrl = row["preview_url"] as? String,
            ))
        }
        return data.text("name").ifBlank { "Spotify playlist" } to tracks
    }

    suspend fun spotifyUri(track: LiveTrack): String? = track.spotifyUri ?: track.song.let { song ->
        val result = cloud.spotifyTrackLookup(song.name, song.artistName, song.isrc, song.appleMusicId)
        result.spotifyUri?.takeIf { it.matches(Regex("spotify:track:[A-Za-z0-9]{22}")) }
            ?: result.webUrl?.substringAfter("/track/", "")?.substringBefore('?')
                ?.takeIf { it.matches(Regex("[A-Za-z0-9]{22}")) }?.let { "spotify:track:$it" }
    }

    suspend fun searchPeople(query: String, uid: String): List<LivePerson> =
        if (query.isBlank()) {
            users.fetchUsersByIdsBatched(users.fetchFollowerIds(uid).take(30))
        } else {
            users.searchUsers(query.trim().removePrefix("@"), includeFollowed = true)
        }.filter { it.id != uid }.map { LivePerson(it.id, it.username, it.avatarThumbURL ?: it.avatarURL) }

    suspend fun uploadWelcome(uid: String, id: String, recording: LiveRecording): Map<String, Any> = mapOf(
        "url" to storage.uploadVoiceNote(uid, id, recording.bytes),
        "duration" to recording.seconds,
    )

    suspend fun voiceToken(id: String, session: String): LiveFields =
        functions.getHttpsCallable("corusLiveVoice").call(mapOf("roomId" to id, "voiceSession" to session))
            .await().getData().liveFields().orEmpty()

    suspend fun gifs(query: String): List<String> =
        cloud.searchKlipyGifs(query).results.mapNotNull { it.fullURL.takeIf(String::isNotBlank) }

    private fun DocumentSnapshot.toRoom(): LiveRoom? = toLiveRoom()

    private fun <T> Query.snapshots(transform: (List<DocumentSnapshot>) -> T): Flow<T> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(transform(snapshot?.documents.orEmpty()))
        }
        awaitClose { registration.remove() }
    }

    companion object {
        fun locale(): String = Locale.getDefault().toLanguageTag()
    }
}

private fun DocumentSnapshot.toLiveRoom(): LiveRoom? = data?.let { LiveRoom(id, it) }
