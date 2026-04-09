package fm.corus.android.data.model

enum class RainIntensity(val value: String) {
    OFF("off"),
    LIGHT("light"),
    MEDIUM("medium"),
    HEAVY("heavy");

    val displayName: String
        get() = when (this) {
            OFF -> "Off"
            LIGHT -> "Light Drizzle"
            MEDIUM -> "Steady Rain"
            HEAVY -> "Downpour"
        }

    val dropCount: Int
        get() = when (this) {
            OFF -> 0
            LIGHT -> 40
            MEDIUM -> 80
            HEAVY -> 140
        }

    companion object {
        fun from(value: String?): RainIntensity =
            entries.firstOrNull { it.value == value } ?: OFF
    }
}
