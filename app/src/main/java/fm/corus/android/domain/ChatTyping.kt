package fm.corus.android.domain

data class TypingPulse(val uid: String, val atMs: Long)

object ChatTyping {
    const val HEARTBEAT_MS = 2_500L
    const val IDLE_MS = 2_000L
    const val STALE_MS = 6_000L
    val TESTER_UIDS = setOf(
        "FUQZIrZR08T2Ux2vYpPzWx7B1rv1", // @gabe
        "u3UmswvOg5c2r9zYlOidJYFzqbp2", // @clifton
    )

    fun isEnabled(flag: Boolean, uid: String?): Boolean =
        flag || (uid != null && uid in TESTER_UIDS)

    fun threadKind(isGroup: Boolean, cityChatId: String?): String = when {
        !cityChatId.isNullOrBlank() -> "city"
        isGroup -> "group"
        else -> "dm"
    }

    fun activeIds(
        pulses: List<TypingPulse>,
        nowMs: Long,
        selfUid: String,
        blocked: Set<String> = emptySet(),
    ): List<String> = pulses.mapNotNull { pulse ->
        when {
            pulse.uid.isBlank() || pulse.uid == selfUid -> null
            pulse.uid in blocked -> null
            nowMs - pulse.atMs > STALE_MS -> null
            else -> pulse.uid
        }
    }
}
