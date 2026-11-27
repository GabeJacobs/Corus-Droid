package fm.corus.android.ui.screens.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

internal data class LiveDiscoverySnapshotEntry(
    val room: LiveRoom, val previews: List<LiveTrack>, val listenerCount: Int, val peopleCount: Int,
)
internal data class LiveDiscoverySnapshot(val rooms: List<LiveDiscoverySnapshotEntry>, val generatedAt: Long) {
    companion object {
        fun parse(fields: LiveFields): LiveDiscoverySnapshot {
            require(fields.number("protocol") == 1L)
            require(fields["rooms"] is List<*> && fields["rankedRoomIds"] is List<*> && fields["generatedAt"] is Number)
            val entries = fields.rows("rooms").map { row ->
                val id = row.text("id").also { require(it.isNotBlank()) }
                val room = row["room"].liveFields() ?: error("Missing room")
                val people = (row["peopleCount"] as? Number)?.toInt() ?: error("Missing population")
                val listeners = (row["listenerCount"] as? Number)?.toInt() ?: error("Missing listeners")
                require(people >= 1 && listeners >= 0)
                LiveDiscoverySnapshotEntry(LiveRoom(id, room), row.rows("previewTracks").mapNotNull(LiveTrack::parse), listeners, people)
            }
            require(entries.map { it.room.id } == fields["rankedRoomIds"])
            require(entries.map { it.room.id }.distinct().size == entries.size)
            return LiveDiscoverySnapshot(entries, fields.number("generatedAt"))
        }
    }
}

internal class LiveDiscoverySnapshotState {
    var snapshot by mutableStateOf<LiveDiscoverySnapshot?>(null)
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var pending = false
    // Keep the last complete list during re-entry and refresh; the VM isolates accounts.

    suspend fun load(vm: LiveViewModel, query: String) {
        if (loading) { pending = true; return }
        failed = false
        loading = true
        try {
            do {
                pending = false
                try {
                    val complete = LiveDiscoverySnapshot.parse(vm.loadDiscoverySnapshot(query))
                    // One state assignment publishes metadata, counts and ranking together.
                    snapshot = complete
                    failed = false
                    vm.event("discovery_snapshot_loaded", "success", "live_browse")
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true; vm.event("discovery_snapshot_loaded", "failed", "live_browse") }
            } while (pending)
        } finally { loading = false }
    }
}

internal fun liveDiscoveryVisible(room: LiveRoom, now: Long): Boolean =
    room.status in listOf("playing", "waiting") && room.leaseUntil > now
