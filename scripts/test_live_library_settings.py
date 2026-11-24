"""Run the production track entry points against enabled/disabled preferences."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
import os
root = Path(__file__).resolve().parents[1]
def source(name):
    return (root / ('app/src/main/java/fm/corus/android/domain/' + name + '.kt')).read_text()
def method(text, signature):
    return signature + text.split(signature, 1)[1].split('\n    }', 1)[0] + '\n    }\n'
spotify = source('SpotifySaveAutoAdd')
apple = source('AppleMusicSaveAutoAdd')
harness = '''
import kotlin.coroutines.*
import java.util.concurrent.CancellationException
class Flow(var value: Boolean)
suspend fun Flow.first() = value
class Scope { fun launch(block: suspend () -> Unit) { block.startCoroutine(object: Continuation<Unit> { override val context = EmptyCoroutineContext; override fun resumeWith(result: Result<Unit>) { result.getOrThrow() } }) } }
object Log { fun w(tag: String, text: String) {} }
enum class MusicService { SPOTIFY, APPLE_MUSIC }
class Selection(var value: MusicService = MusicService.SPOTIFY)
class Preference { val current = Selection() }
class Preferences { val autoAddSavedToSpotify = Flow(true); val autoAddSavedToAppleMusic = Flow(true) }
class Config { var spotifyLibrarySaveEnabled = true }
class CymbalTrack(val id: String = "song")
fun CymbalTrack.toQueuedTrack() = this
class Apple { var writes = 0; suspend fun addToLibrary(track: CymbalTrack, user: String, enabled: suspend () -> Boolean) { if (enabled()) writes++ } }
class SpotifySettings {
    val scope = Scope(); val remoteConfig = Config(); val musicServicePreference = Preference(); val preferencesDataStore = Preferences(); var writes = 0; val TAG = "test"
    suspend fun resolveSpotifyTrackId(track: CymbalTrack): String? = track.id
    suspend fun deliver(uri: String) { writes++ }
''' + method(spotify, '    fun handleSaved(track: CymbalTrack) {') + method(spotify, '    private suspend fun isEnabled(): Boolean {') + '''
}
class AppleSettings {
    val scope = Scope(); val musicService = Preference(); val preferences = Preferences(); val appleMusic = Apple()
''' + method(apple, '    fun handleSaved(track: CymbalTrack, userId: String) {') + '''
    private suspend fun isEnabled(): Boolean =
''' + apple.split('    private suspend fun isEnabled(): Boolean =\n', 1)[1].split('\n}', 1)[0] + '''
}
fun main() {
    val spotify = SpotifySettings(); val apple = AppleSettings(); val track = CymbalTrack()
    spotify.handleSaved(track); check(spotify.writes == 1)
    spotify.preferencesDataStore.autoAddSavedToSpotify.value = false
    spotify.handleSaved(track); check(spotify.writes == 1) { "Disabled Spotify setting must block delivery" }
    spotify.preferencesDataStore.autoAddSavedToSpotify.value = true
    spotify.musicServicePreference.current.value = MusicService.APPLE_MUSIC
    spotify.handleSaved(track); check(spotify.writes == 1)
    apple.handleSaved(track, "uid"); check(apple.appleMusic.writes == 0)
    apple.musicService.current.value = MusicService.APPLE_MUSIC
    apple.handleSaved(track, "uid"); check(apple.appleMusic.writes == 1)
    apple.preferences.autoAddSavedToAppleMusic.value = false
    apple.handleSaved(track, "uid"); check(apple.appleMusic.writes == 1) { "Disabled Apple Music setting must block delivery" }
    println("PASS: Android track helpers respect service selection and library settings")
}
'''
with TemporaryDirectory(prefix='corus-library-settings-') as directory:
    path = Path(directory) / 'Test.kt'; path.write_text(harness)
    jar = Path(directory) / 'test.jar'
    java = '/Applications/Android Studio.app/Contents/jbr/Contents/Home'
    env = {**os.environ, 'JAVA_HOME': java}
    subprocess.run(['bash', '/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc', str(path), '-include-runtime', '-d', str(jar)], check=True, env=env)
    subprocess.run([java + '/bin/java', '-jar', str(jar)], check=True, env=env)
