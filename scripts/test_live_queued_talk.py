"""Execute the production transition policy using lightweight room inputs."""
from pathlib import Path
import os
import subprocess
import tempfile
source = (Path(__file__).resolve().parents[1] / 'app/src/main/java/fm/corus/android/ui/screens/live/LiveQueuedTalk.kt').read_text().split('\n', 1)[1]
harness = '''
data class Person(val uid: String)
data class LiveRoom(val id: String = "room", val host: Person = Person("host"), val revision: Long = 3, val talkAfterSongArmed: Boolean = false, val voiceMode: String = "talk", val voiceSession: String? = "new", val status: String = "playing")
fun main() {
    val before = LiveRoom(talkAfterSongArmed = true, voiceMode = "music", voiceSession = "old")
    val after = LiveRoom()
    check(after.startsQueuedTalkAfter(before, "host")) { "Host must start unmuted" }
    check(!after.startsQueuedTalkAfter(before, "dj"))
    check(!after.startsQueuedTalkAfter(null, "host"))
    check(!after.startsQueuedTalkAfter(after, "host"))
    check(!after.startsQueuedTalkAfter(before.copy(talkAfterSongArmed = false), "host"))
    for (invalid in listOf(after.copy(id = "other"), after.copy(host = Person("other")), after.copy(revision = 4), after.copy(voiceSession = "old"), after.copy(voiceSession = null), after.copy(status = "ended"))) {
        check(!invalid.startsQueuedTalkAfter(before, "host"))
    }
    println("PASS: Android queued Talk enables only the transitioning host")
}
'''
with tempfile.TemporaryDirectory() as folder:
    path = Path(folder) / 'QueuedTalk.kt'
    path.write_text(source + harness)
    binary = Path(folder) / 'test.jar'
    env = dict(os.environ, JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home')
    compiler = '/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc'
    subprocess.run(['bash', compiler, str(path), '-include-runtime', '-d', str(binary)], check=True, env=env)
    subprocess.run([env['JAVA_HOME'] + '/bin/java', '-jar', str(binary)], check=True)
