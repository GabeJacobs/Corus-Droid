package fm.corus.android.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Saves that couldn't be mirrored into the user's Spotify library yet.
 *
 * App Remote's [com.spotify.android.appremote.api.UserApi] only exists while
 * the IPC session is live — the Spotify app performs the write itself, which
 * is exactly why it sidesteps the Web API's development-mode 25-user cap. The
 * cost is that a save made while Spotify is closed has nowhere to go. Park the
 * track URI here and drain it on the next App Remote connect.
 *
 * Pure list operations, deliberately free of Android and DataStore types so
 * the ordering, dedupe and overflow rules are unit-testable on the JVM.
 * Persistence lives in
 * [fm.corus.android.data.local.PreferencesDataStore.pendingSpotifyLibraryUrisJson],
 * JSON-encoded to match the `for_you_seen_ids` ring buffer next to it.
 */
object SpotifyLibraryQueue {

    /**
     * A user who saves for weeks without opening Spotify shouldn't grow this
     * without bound. Oldest entries go first — a save from three weeks ago
     * matters less than one from this morning.
     */
    const val MAX_DEPTH = 200

    private val JSON = Json { ignoreUnknownKeys = true }
    private val SERIALIZER = ListSerializer(String.serializer())

    /** Oldest first. Malformed or absent JSON reads as empty, never throws. */
    fun decode(json: String): List<String> =
        runCatching { JSON.decodeFromString(SERIALIZER, json) }.getOrDefault(emptyList())

    fun encode(uris: List<String>): String = JSON.encodeToString(SERIALIZER, uris)

    /**
     * Appends [uri] unless it's already queued. Re-saving a song that's still
     * pending is a no-op rather than a second delivery attempt.
     */
    fun enqueue(current: List<String>, uri: String): List<String> {
        if (uri.isBlank()) return current
        if (current.contains(uri)) return current
        return (current + uri).takeLast(MAX_DEPTH)
    }

    /**
     * Drops [uri] — either delivered, or rejected by Spotify in a way that
     * retrying won't fix.
     */
    fun remove(current: List<String>, uri: String): List<String> =
        current.filterNot { it == uri }
}
