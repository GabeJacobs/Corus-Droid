package fm.corus.android.ui.screens.live
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
class LiveVoiceReadinessTest {
    @Test fun speechSamplesAreSuppressedUntilReadyAndAgainOnCancellation() {
        var allowed = false
        val processor = LiveVoiceSpeechGate { allowed }
        val buffer = ByteBuffer.allocate(1920)
        fun speech() { for (index in 0 until buffer.capacity()) buffer.put(index, 77) }
        speech(); processor.processAudio(3, 160, buffer)
        assertTrue(buffer.array().all { it == 0.toByte() })
        allowed = true; speech(); processor.processAudio(3, 160, buffer)
        assertTrue(buffer.array().all { it == 77.toByte() })
        allowed = false; processor.processAudio(3, 160, buffer)
        assertTrue(buffer.array().all { it == 0.toByte() })
    }

    @Test fun onlyCurrentAudienceAndAttemptCanReleaseSpeech() {
        val gate = LiveVoiceReadinessGate("session", "track", setOf("listener"), "nonce")
        val value = gate.message
        for ((uid, message) in listOf("stranger" to value, "listener" to value.copy(session="old"), "listener" to value.copy(track="old"), "listener" to value.copy(nonce="old"))) {
            gate.acknowledge(uid, message); assertFalse(gate.ready)
        }
        gate.acknowledge("listener", value); assertTrue(gate.ready)
    }
    @Test fun emptyAudienceIsImmediateAndLastListenerLeavingReleases() {
        assertTrue(LiveVoiceReadinessGate("s", "t", emptySet()).ready)
        val gate = LiveVoiceReadinessGate("s", "t", setOf("listener")); assertFalse(gate.ready)
        gate.updateAudience(emptySet()); assertTrue(gate.ready)
    }
    @Test fun legacyFallbackNeedsAnActualConnectedLegacyListener() {
        val gate = LiveVoiceReadinessGate("s", "t", setOf("listener")); gate.legacySubscribed = true
        gate.allowLegacy(emptySet(), emptySet()); assertFalse(gate.ready)
        gate.allowLegacy(setOf("listener"), setOf("listener")); assertFalse(gate.ready)
        gate.allowLegacy(setOf("listener"), emptySet()); assertTrue(gate.ready)
    }
    @Test fun aLaterAttemptRejectsOldReadiness() {
        val first = LiveVoiceReadinessGate("s", "t", setOf("listener"))
        val second = LiveVoiceReadinessGate("s", "t", setOf("listener"))
        second.acknowledge("listener", first.message); assertFalse(second.ready)
    }
}
