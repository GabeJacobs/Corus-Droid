"""Execute the production Live save action with music-library delivery spies."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
import os
root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/fm/corus/android/ui/screens/live/LiveViewModel.kt').read_text()
method = '    fun save(track: LiveTrack) {' + source.split('    fun save(track: LiveTrack) {', 1)[1].split('\n    fun refreshSaves', 1)[0]
harness = '''
data class Song(val id: String = "song", val source: Source = Source())
data class Source(val raw: String = "spotify")
data class LiveTrack(val song: Song = Song(), val playRevision: Long = 1) { val id get() = song.id }
data class Room(val revision: Long = 1)
data class State(val room: Room? = Room(), val uid: String? = "uid", val savedIds: Set<String> = emptySet(), val mixedSaves: List<Item>? = null, val hosting: Boolean = false)
interface Item
object LiveSaveItem { data class Track(val id: String): Item }
class Box(var value: State = State())
class Mirror { val ids = mutableListOf<String>(); fun handleSaved(track: Song) { ids.add(track.id) }; fun handleSaved(track: Song, uid: String) { ids.add(track.id) } }
class Model {
    val state = Box(); val spotifySaveAutoAdd = Mirror(); val appleMusicSaveAutoAdd = Mirror(); var fail = false
    fun saveId(track: LiveTrack) = track.id
    fun action(name: String, fields: Map<String, Any?>, success: () -> Unit) { if (!fail) success() }
    fun update(block: State.() -> State) { state.value = state.value.block() }
    fun loadMixedSaves(reset: Boolean) {}
    fun event(name: String, source: String) {}
''' + method + '''
}
fun main() {
    val model = Model(); val track = LiveTrack()
    model.save(track)
    check(model.spotifySaveAutoAdd.ids == listOf("song")) { "Live save never reaches Spotify" }
    check(model.appleMusicSaveAutoAdd.ids == listOf("song")) { "Live save never reaches Apple Music" }
    model.save(track)
    check(model.spotifySaveAutoAdd.ids.size == 1) { "Unsave must not add a song" }
    model.fail = true; model.save(track)
    check(model.spotifySaveAutoAdd.ids.size == 1) { "Failed Corus save must not add a song" }
    println("PASS: Android Live mirrors successful new saves only")
}
'''
with TemporaryDirectory(prefix='corus-live-library-') as directory:
    path = Path(directory) / 'Test.kt'; path.write_text(harness)
    jar = Path(directory) / 'test.jar'
    java = '/Applications/Android Studio.app/Contents/jbr/Contents/Home'
    env = {**os.environ, 'JAVA_HOME': java}
    subprocess.run(['bash', '/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc', str(path), '-include-runtime', '-d', str(jar)], check=True, env=env)
    subprocess.run([java + '/bin/java', '-jar', str(jar)], check=True, env=env)
