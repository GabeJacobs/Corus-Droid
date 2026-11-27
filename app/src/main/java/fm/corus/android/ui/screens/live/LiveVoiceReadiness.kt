package fm.corus.android.ui.screens.live

import java.util.UUID

internal data class LiveVoiceReadinessMessage(val session: String, val track: String, val nonce: String)

internal class LiveVoiceReadinessGate(session: String, track: String, audience: Set<String>, nonce: String = UUID.randomUUID().toString()) {
    val message = LiveVoiceReadinessMessage(session, track, nonce)
    var audience: Set<String> = audience
        private set
    @Volatile var ready = audience.isEmpty()
        private set
    var legacySubscribed = false
    fun updateAudience(ids: Set<String>) { audience = ids; if (ids.isEmpty()) ready = true }
    fun acknowledge(from: String, value: LiveVoiceReadinessMessage) {
        if (from in audience && value == message) ready = true
    }
    fun allowLegacy(connected: Set<String>, capable: Set<String>) {
        if (legacySubscribed && audience.intersect(capable).isEmpty() && audience.intersect(connected - capable).isNotEmpty()) ready = true
    }
}
