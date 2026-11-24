"""Run the production send/retry paths and outbox against deferred server calls."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
import os
root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/fm/corus/android/ui/screens/live/LiveViewModel.kt').read_text()
methods = '    fun send(text: String, gif: String?, done: () -> Unit) {' + source.split('    fun send(text: String, gif: String?, done: () -> Unit) {', 1)[1].split('    private suspend fun recoverSentChatMessage', 1)[0]
outbox = (root / 'app/src/main/java/fm/corus/android/ui/screens/live/LiveChatOutbox.kt').read_text().split('\n', 1)[1]
harness = '''
import kotlinx.coroutines.*
import java.util.UUID
data class LivePerson(val uid: String)
data class LiveMessage(val id: String, val fields: Map<String, Any?>) {
    val author get() = LivePerson(fields["uid"] as String)
    val text get() = fields["text"] as String
    val gifUrl get() = fields["gifURL"] as? String
    val createdAt get() = fields["createdAt"] as Long
}
data class Profile(val username: String = "listener", val avatarURL: String? = null)
data class Room(val id: String = "room", val chatEnabled: Boolean = true, val status: String = "playing")
class Box<T>(var value: T)
class Auth { val userProfile = Box(Profile()) }
internal data class State(val room: Room? = Room(), val uid: String? = "me", val chatOutbox: LiveChatOutbox = LiveChatOutbox(), val pendingChatSends: Map<String, String> = emptyMap())
''' + outbox + '''
internal class Model {
    val state = Box(State()); val auth = Auth()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val viewModelScope get() = scope
    val calls = mutableListOf<Map<String, Any>>()
    val gates = mutableListOf<CompletableDeferred<Unit>>()
    fun update(block: State.() -> State) { state.value = state.value.block() }
    fun serverTime() = System.currentTimeMillis()
    fun showError(error: Exception) {}
    suspend fun command(action: String, roomId: String, fields: Map<String, Any>) {
        calls.add(fields); val gate = CompletableDeferred<Unit>(); gates.add(gate); gate.await()
    }
    suspend fun recoverSentChatMessage(room: String, uid: String, id: String) {}
''' + methods + '''
}
fun main() = runBlocking {
    val model = Model(); var cleared = 0
    model.send("first", null) { cleared++ }
    check(cleared == 1 && model.state.value.chatOutbox.entries.size == 1) { "Send must clear and display before server confirmation" }
    model.send("second", "https://example.com/gif") { cleared++ }
    check(cleared == 2 && model.calls.size == 2) { "Pending sends must not block the next comment" }
    val first = model.state.value.chatOutbox.entries.values.first().message
    val second = model.state.value.chatOutbox.entries.values.last().message
    model.gates[0].completeExceptionally(IllegalStateException("offline"))
    check(model.state.value.chatOutbox.entries[first.id]?.status == LiveChatOutbox.Status.FAILED)
    model.retryChat(first.id); model.retryChat(first.id)
    check(model.calls.size == 3 && model.calls[0]["requestId"] == model.calls[2]["requestId"])
    val server = first.copy(fields = first.fields + ("createdAt" to second.createdAt + 100))
    var box = model.state.value.chatOutbox.reconcile(listOf(server))
    check(box.merge(listOf(server)).map { it.id } == listOf(first.id, second.id)) { "Confirmation must not reorder local comments" }
    box = box.settle(first.id, false)
    check(box.entries[first.id]?.status == LiveChatOutbox.Status.CONFIRMED) { "A lost response must not mark a delivered comment failed" }
    box = box.reconcile(emptyList())
    check(box.merge(emptyList()).map { it.id } == listOf(second.id)) { "Deletion must not resurrect a comment" }
    model.state.value = State(room = Room(id = "another-room"))
    model.gates[2].completeExceptionally(IllegalStateException("late response"))
    check(model.state.value.chatOutbox.entries.isEmpty()) { "Old room responses must not leak into a new room" }
    model.scope.cancel()
    println("PASS: Android instant send, multiple comments, failure, same-ID retry, stable confirmation, deletion and room switching")
}
'''
with TemporaryDirectory(prefix='corus-live-chat-') as d:
    path = Path(d) / 'Test.kt'; path.write_text(harness)
    jar = Path(d) / 'test.jar'
    java = '/Applications/Android Studio.app/Contents/jbr/Contents/Home'
    kotlin = '/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc'
    coroutines = kotlin + '/lib/kotlinx-coroutines-core-jvm.jar'
    env = {**os.environ, 'JAVA_HOME': java}
    subprocess.run(['bash', kotlin+'/bin/kotlinc', str(path), '-cp', coroutines, '-include-runtime', '-d', str(jar)], check=True, env=env)
    subprocess.run([java+'/bin/java', '-cp', str(jar)+':'+coroutines, 'TestKt'], check=True, env=env)
