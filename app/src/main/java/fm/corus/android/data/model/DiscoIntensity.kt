package fm.corus.android.data.model

enum class DiscoIntensity(val value: String) {
    OFF("off"),
    LIGHT("light"),
    HEAVY("heavy");

    val displayName: String
        get() = when (this) {
            OFF -> "Off"
            LIGHT -> "Subtle"
            HEAVY -> "Full Disco"
        }

    val lightCount: Int
        get() = when (this) {
            OFF -> 0
            LIGHT -> 3
            HEAVY -> 6
        }

    val beamCount: Int
        get() = when (this) {
            OFF -> 0
            LIGHT -> 2
            HEAVY -> 4
        }

    companion object {
        fun from(value: String?): DiscoIntensity =
            entries.firstOrNull { it.value == value } ?: OFF
    }
}
