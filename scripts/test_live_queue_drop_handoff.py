"""Run HostControls' real move callback before the VM's queued update is collected."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess, os
root = Path(__file__).resolve().parents[1]
s = (root / 'app/src/main/java/fm/corus/android/ui/screens/live/LiveRoomScreen.kt').read_text().split('private fun HostControls(',1)[1].split('private fun RoomQueueEditor(',1)[0]
def block(marker):
    begin = s.index(marker) + len(marker)
    depth = 1
    for i in range(begin, len(s)):
        if s[i] == '{': depth += 1
        if s[i] == '}':
            depth -= 1
            if depth == 0: return s[begin:i]
    raise AssertionError(marker)
move = block('move = { destination ->')
queue_expression = s.split('    val queue = ',1)[1].split('\n',1)[0]
# The network worker owns StateFlow; its collector deliberately has not run yet.
# Releasing the drag preview and scrolling happen synchronously in this callback.
local = block('fun editQueue(desired: List<LiveTrack>): Boolean {') if 'fun editQueue(' in s else ''
method = 'fun editQueue(desired: List<LiveTrack>): Boolean {' + local + '}' if local else ''
handoff = block('LaunchedEffect(state.queuePreview, pendingOrder.value) {') if 'LaunchedEffect(state.queuePreview, pendingOrder.value) {' in s else ''
harness = '''
data class LiveTrack(val id: String)
class Box<T>(var value: T)
fun <T> mutableStateOf(value: T) = Box(value)
class State(var queuePreview: List<LiveTrack>? = null, val hosting: Boolean = true)
class Scroll { val firstVisibleItemIndex = 0; val firstVisibleItemScrollOffset = 0
    fun requestScrollToItem(index: Int, offset: Int = 0) {} }
class VM {
    var finish: (() -> Unit)? = null
    var accept = true
    fun updateQueue(queue: List<LiveTrack>, version: Long, hosting: Boolean, done: () -> Unit = {}): Boolean {
        finish = done
        return accept
    }
}
fun main() {
    for ((index, destination) in listOf(3 to 1, 3 to 0)) {
        val serverQueue = listOf("a","b","c","d").map(::LiveTrack)
        val state = State()
        val pendingOrder = mutableStateOf<Pair<Any, List<LiveTrack>>?>(null)
        val pendingSource = mutableStateOf<List<LiveTrack>?>(null)
        val vm = VM(); val version = 1L; val queueScroll = Scroll()
        fun displayed() = ''' + queue_expression + '''
        val queue = displayed()
''' + method + '''
        val move: (Int) -> Unit = { destination ->
''' + move + '''
        }
        move(destination)
        val expected = serverQueue.toMutableList().apply { add(destination, removeAt(index)) }
        check(displayed() == expected) { "Drop/menu cleared its visual position before the queued VM update reached the screen" }
        // Hand off when the VM preview is collected, not when the network finishes.
        state.queuePreview = expected
        fun collected() {
''' + handoff + '''
        }
        collected()
        val withNewSongs = expected + listOf(LiveTrack("new1"), LiveTrack("new2"), LiveTrack("new3"))
        state.queuePreview = withNewSongs
        collected()
        check(displayed() == withNewSongs) { "A stale local drag/removal hides songs added by the picker until its older network save finishes" }
        state.queuePreview = expected
        vm.finish!!()
        check(displayed() == expected)
        // Completion of an older save cannot clear a newer local edit.
''' + ('''
        editQueue(serverQueue)
        val olderFinish = vm.finish!!
        editQueue(expected.reversed())
        olderFinish()
        check(displayed() == expected.reversed())
        state.queuePreview = expected.reversed()
        vm.finish!!()
        check(displayed() == expected.reversed())
        // StateFlow can skip the intermediate removal when additions follow it.
        editQueue(emptyList())
        state.queuePreview = withNewSongs
        collected()
        check(displayed() == withNewSongs) { "Conflated VM updates must not strand an older empty display order" }
        vm.finish!!()
        state.queuePreview = expected.reversed()
        vm.accept = false
        check(!editQueue(serverQueue))
        check(displayed() == expected.reversed())
''' if method else '') + '''
    }
    println("PASS: drag drop and Move to top update the displayed queue before VM collection")
}
'''
with TemporaryDirectory() as d:
    path = Path(d) / 'Test.kt'; path.write_text(harness); jar = Path(d) / 'test.jar'
    java = '/Applications/Android Studio.app/Contents/jbr/Contents/Home'
    kotlin = '/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc'
    env = {**os.environ, 'JAVA_HOME': java}
    subprocess.run(['bash', kotlin+'/bin/kotlinc', str(path), '-include-runtime', '-d', str(jar)], check=True, env=env)
    subprocess.run([java+'/bin/java', '-jar', str(jar)], check=True, env=env)
