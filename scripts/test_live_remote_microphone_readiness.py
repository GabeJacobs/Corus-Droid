"""Execute LiveVoice's real readiness checks across microphone transitions."""
from pathlib import Path
import os
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/fm/corus/android/ui/screens/live/LiveVoice.kt").read_text()
readiness = source[source.index("    fun isConnected("):source.index("    fun levels()")]
harness = """
object Track { enum class Source { MICROPHONE } }
class Publication(var muted: Boolean = false, var track: Any? = null)
class Participant(val publication: Publication) {
 fun getTrackPublication(source: Track.Source): Publication? = publication
}
class Room {
 enum class State { CONNECTED, DISCONNECTED, RECONNECTING }
 var state = State.CONNECTED
 val remoteParticipants = mutableMapOf<String, Participant>()
}
class Voice(var room: Room?, var session: String = "session") {
 var receivedRemoteMicrophone = false
""" + readiness + """
}
fun main() {
 val room = Room()
 val voice = Voice(room)
 check(voice.waitingForRemoteMicrophone("session"))
 check(!voice.holdingTalkPresentation("session"))
 val publication = Publication()
 room.remoteParticipants["clifton"] = Participant(publication)
 check(voice.waitingForRemoteMicrophone("session")) { "Published but unsubscribed mic is still connecting" }
 publication.track = Any()
 check(!voice.waitingForRemoteMicrophone("session"))
 check(voice.hasReceivedRemoteMicrophone("session"))
 check(!voice.holdingTalkPresentation("session"))
 publication.muted = true
 publication.track = null
 check(!voice.waitingForRemoteMicrophone("session")) { "Muted mic must not spin forever" }
 check(!voice.holdingTalkPresentation("session")) { "Explicit mute must not retain a speaking label" }
 publication.muted = false
 check(!voice.waitingForRemoteMicrophone("session")) { "Established mic shutting down must not restart initial Connecting" }
 check(voice.holdingTalkPresentation("session")) { "Keep the last label during microphone teardown" }
 room.remoteParticipants.clear()
 check(!voice.waitingForRemoteMicrophone("session")) { "Host leaving Talk must not flash Connecting before Music arrives" }
 check(voice.holdingTalkPresentation("session"))
 room.state = Room.State.DISCONNECTED
 check(!voice.waitingForRemoteMicrophone("session"))
 check(!voice.isConnected("session"))
 room.state = Room.State.RECONNECTING
 check(voice.isReconnecting("session"))
 check(!voice.holdingTalkPresentation("session"))
 room.state = Room.State.CONNECTED
 check(!voice.waitingForRemoteMicrophone("old-session"))
 check(!voice.hasReceivedRemoteMicrophone("old-session"))
 check(!voice.holdingTalkPresentation("old-session"))
 voice.receivedRemoteMicrophone = false
 check(voice.waitingForRemoteMicrophone("session")) { "A fresh Talk session must show initial Connecting again" }
 println("PASS: Android distinguishes joining, publishing, subscribing, ready, and muted microphones")
}
"""
with tempfile.TemporaryDirectory() as folder:
    path = Path(folder) / "Readiness.kt"
    path.write_text(harness)
    binary = Path(folder) / "readiness.jar"
    env = dict(os.environ, JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home")
    compiler = "/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc"
    subprocess.run(["bash", compiler, str(path), "-include-runtime", "-d", str(binary)], check=True, env=env)
    subprocess.run([env["JAVA_HOME"] + "/bin/java", "-jar", str(binary)], check=True)
