"""Execute the production updateQueue against a suspended server callable."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess, os
root = Path(__file__).resolve().parents[1]
s = (root / 'app/src/main/java/fm/corus/android/ui/screens/live/LiveViewModel.kt').read_text()
method = '    fun updateQueue(' + s.split('    fun updateQueue(',1)[1].split('\n    fun skip(',1)[0]
helper = (root / 'app/src/main/java/fm/corus/android/ui/screens/live/LiveQueueAdditions.kt').read_text().split('\n',1)[1]
harness = '''
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
typealias LiveFields = Map<String, Any?>
fun LiveFields.number(key: String) = (get(key) as? Number)?.toLong() ?: 0L
fun LiveFields.text(key: String) = get(key) as? String ?: ""
fun LiveFields.rows(key: String) = get(key) as? List<LiveFields> ?: emptyList()
data class Song(val source: String = "spotify")
data class LiveTrack(val id: String, val playsFromSource: Boolean = false, val song: Song = Song()) {
    fun fields(): LiveFields = mapOf("id" to id)
    companion object { fun parse(fields: LiveFields) = LiveTrack(fields.text("id")) }
}
data class Person(val uid: String = "me")
data class LiveRoom(val id: String = "room", val queue: List<LiveTrack> = listOf("a","b","c").map(::LiveTrack), val queueVersion: Long = 1, val host: Person = Person(), val djs: List<LiveFields> = emptyList(), val status: String = "playing")
data class State(val room: LiveRoom? = LiveRoom(), val dj: LiveFields? = null, val uid: String = "me", val hosting: Boolean = true, val queueUpdating: Boolean = false, val queuePreview: List<LiveTrack>? = null, val queuePreviewVersion: Long? = null, val error: String? = null)
object R { object string { const val live_the_queue_changed_try_again = 1 } }
class Context { fun getString(id: Int) = "queue changed" }
class FirebaseFunctionsException(val code: Code): Exception() { enum class Code { ABORTED, FAILED_PRECONDITION } }
class Repo {
    var latest = LiveRoom()
    var staleNextRead: LiveRoom? = null
    suspend fun room(id: String): LiveRoom {
        val stale = staleNextRead
        staleNextRead = null
        return stale ?: latest
    }
}
''' + helper + '''
class Model {
    val state = MutableStateFlow(State())
    val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    var queueUpdateJob: Job? = null
    var queueSaveTail: Deferred<Boolean>? = null
    var queueEditToken: UUID? = null
    var queueEditEpoch = 0
    val context = Context()
    val repository = Repo()
    var staleAcknowledgment: LiveRoom? = null
    var delaySnapshot = false
    val calls = mutableListOf<LiveFields>()
    val gates = mutableListOf<CompletableDeferred<Unit>>()
    fun update(block: State.() -> State) { state.value = state.value.block() }
    fun showError(error: Exception) { update { copy(error = error.message) } }
    suspend fun prepareQueueTrack(track: LiveTrack) = track
    suspend fun command(action: String, room: String, fields: LiveFields) {
        calls.add(fields)
        val gate = CompletableDeferred<Unit>(); gates.add(gate); gate.await()
        val tracks = fields.rows("queue").map(LiveTrack::parse)
        repository.latest = repository.latest.copy(queue = tracks, queueVersion = repository.latest.queueVersion + 1)
        repository.staleNextRead = staleAcknowledgment
        if (!delaySnapshot) state.value = state.value.copy(room = repository.latest)
    }
''' + method + '''
}
fun main() = runBlocking {
    val model = Model()
    fun tracks(vararg ids: String) = ids.map(::LiveTrack)
    check(model.updateQueue(tracks("b","c","a"), 1, true))
    check(model.updateQueue(tracks("c","a","b"), 1, true)) { "Second move rejected while first sync is pending" }
    check(model.calls.size == 1) { "Writes must be serialized" }
    check(model.state.value.queuePreview == tracks("c","a","b")) { "Latest order must be visible immediately" }
    model.gates[0].complete(Unit)
    check(model.calls.size == 2)
    check(model.calls[1].number("queueVersion") == 2L) { "Second move must use a fresh version" }
    model.gates[1].complete(Unit)
    check(model.repository.latest.queue == tracks("c","a","b"))
    model.viewModelScope.cancel()
    val failed = Model()
    check(failed.updateQueue(tracks("b","c","a"), 1, true))
    check(failed.updateQueue(tracks("c","a","b"), 1, true))
    failed.gates[0].completeExceptionally(IllegalStateException("offline"))
    check(failed.calls.size == 1 && failed.state.value.queuePreview == null && !failed.state.value.queueUpdating)
    check(failed.state.value.error == "offline")
    check(failed.updateQueue(tracks("c","a","b"), 1, true))
    check(failed.calls.size == 2)
    failed.gates[1].complete(Unit)
    failed.viewModelScope.cancel()
    val stale = Model()
    stale.repository.latest = LiveRoom(queue = emptyList())
    stale.state.value = State(room = stale.repository.latest)
    stale.staleAcknowledgment = stale.repository.latest
    stale.delaySnapshot = true
    check(stale.updateQueue(tracks("new1", "new2", "new3"), 1, true, previousQueue = emptyList()))
    stale.gates[0].complete(Unit)
    check(stale.state.value.queuePreview == tracks("new1", "new2", "new3")) {
        "A stale post-save server read must not clear the preview and flash an empty queue"
    }
    check(stale.state.value.queuePreviewVersion == 2L && stale.state.value.queueUpdating)
    stale.viewModelScope.cancel()
    val id: (String) -> String = { it }
    check(liveRebaseQueue(listOf("c","a","b"), listOf("b","a","c"), listOf("b","c"), true, id) == listOf("c","b"))
    check(liveRebaseQueue(listOf("b","a","a"), listOf("a","a","b"), listOf("a","b"), true, id) == listOf("b","a"))
    check(liveRebaseQueue(listOf("b","a","a"), listOf("a","a","b"), listOf("a","b"), false, id) == listOf("b","a"))
    check(liveRebaseQueue(listOf("c","a","b"), listOf("a","b","c"), listOf("b","c","d"), false, id) == listOf("c","b","d"))
    check(liveRebaseQueue(listOf("b","a"), listOf("a","b"), listOf("x","y"), true, id) == null)
    println("PASS: Android accepts rapid edits, displays the latest order, and serializes versioned writes")
}
'''
with TemporaryDirectory() as d:
    path = Path(d) / 'Test.kt'; path.write_text(harness); jar = Path(d) / 'test.jar'
    java = '/Applications/Android Studio.app/Contents/jbr/Contents/Home'
    kotlin = '/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc'
    coroutines = kotlin + '/lib/kotlinx-coroutines-core-jvm.jar'
    env = {**os.environ, 'JAVA_HOME': java}
    subprocess.run(['bash', kotlin+'/bin/kotlinc', str(path), '-cp', coroutines, '-include-runtime', '-d', str(jar)], check=True, env=env)
    subprocess.run([java+'/bin/java', '-cp', str(jar)+':'+coroutines, 'TestKt'], check=True, env=env)
