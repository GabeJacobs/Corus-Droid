"""Compile and execute the actual Talk card status branches."""
from pathlib import Path
import os
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[1] / "app/src/main/java/fm/corus/android/ui/screens/live/LiveRoomScreen.kt").read_text()
readiness = source[source.index("private fun liveMicrophoneIsOn("):source.index("private fun liveAudibleDJs(")]
card = source[source.index("private fun RoomPlayerCard("):]
selection = card[card.index("                val canUseMic"):card.index("                Row(Modifier.heightIn")]
status = card[card.index("                        when {"):card.index("                        CorusFont.songTitle")].strip().removesuffix(",")
harness = """
object R { object string {
const val live_audio_muted = "Muted"
const val live_djs_talking = "DJs talking"
const val live_dj_is_talking = "%s is talking"
const val live_mic_is_muted = "Mic is muted"
const val live_connecting_audio = "Connecting live audio…"
const val live_connecting_microphone = "Connecting microphone…"
const val live_couldnt_connect_to_live_voice = "Couldn’t connect live audio"
} }
object CorusStrings {
const val corus_live_mic_is_on = "Mic is on"
const val corus_live_talk_mode = "Talk mode"
}
fun stringResource(value: String, vararg args: Any) = value.format(*args)
data class Person(val uid: String)
data class State(val hosting: Boolean = false, val dj: Person? = null,
 val micEnabled: Boolean = false, val uid: String = "gabe", val muted: Boolean = false,
 val voiceConnected: Boolean = false, val voiceFailed: Boolean = false,
 val voiceReconnecting: Boolean = false, val hasReceivedRemoteMicrophone: Boolean = false,
 val holdingTalkPresentation: Boolean = false, val lastTalkSpeakerUid: String? = null,
 val waitingForRemoteMicrophone: Boolean = false,
 val unmutedMicrophoneIDs: Set<String> = emptySet())
class Room {
 val talkSpeakerUid = "clifton"
 fun djHandle(uid: String) = "@" + uid
}
""" + readiness.replace("LiveUiState", "State") + """
fun label(state: State, audibleDJs: List<Person> = emptyList()): String {
 val room = Room()
""" + selection + "return " + status + """
}
fun main() {
 val requested = State(hosting = true, micEnabled = true)
 check(label(requested) == "Connecting microphone…") { "Microphone intent must not announce a live microphone" }
 check(label(requested.copy(voiceConnected = true)) == "Connecting microphone…") { "Room connection alone does not prove microphone publication" }
 val published = requested.copy(voiceConnected = true, unmutedMicrophoneIDs = setOf("gabe"))
 check(label(published) == "Mic is on")
 check(label(published.copy(micEnabled = false)) == "Mic is muted")
 check(label(published.copy(voiceConnected = false)) == "Connecting microphone…")
 check(label(requested.copy(voiceFailed = true)) == "Couldn’t connect live audio")
 check(label(requested.copy(muted = true)) == "Muted")
 check(label(State(dj = Person("gabe"), micEnabled = true)) == "Connecting microphone…")
 check(label(State()) == "Connecting live audio…") { "Connecting listener announced the DJ as talking" }
 check(label(State(voiceConnected = true, waitingForRemoteMicrophone = true)) == "Connecting live audio…") {
  "Joining the room must not end Connecting while the remote microphone is still starting"
 }
 check(label(State(voiceConnected = true)) == "Talk mode")
 check(label(State(hasReceivedRemoteMicrophone = true, holdingTalkPresentation = true, lastTalkSpeakerUid = "clifton")) == "@clifton is talking") {
  "Microphone shutdown must keep the Talk label until the room switches directly to its song"
 }
 check(label(State(hasReceivedRemoteMicrophone = true)) == "Talk mode") { "Talk ending must not flash Connecting" }
 check(label(State(hasReceivedRemoteMicrophone = true, voiceReconnecting = true)) == "Connecting live audio…")
 check(label(State(voiceConnected = true, unmutedMicrophoneIDs = setOf("clifton"))) == "@clifton is talking")
 check(label(State(muted = true)) == "Muted")
 check(label(State(voiceFailed = true)) == "Couldn’t connect live audio")
 println("PASS: Android Talk status waits for audio")
}
"""
with tempfile.TemporaryDirectory() as folder:
 path = Path(folder) / "Talk.kt"
 path.write_text(harness)
 binary = Path(folder) / "talk.jar"
 env = dict(os.environ, JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home")
 compiler = "/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc"
 subprocess.run(["bash", compiler, str(path), "-include-runtime", "-d", str(binary)], check=True, env=env)
 subprocess.run([env["JAVA_HOME"] + "/bin/java", "-jar", str(binary)], check=True)
