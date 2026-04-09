package fm.corus.android.data.model

enum class SnowIntensity(val value: String) {
    OFF("off"),
    LIGHT("light"),
    MEDIUM("medium"),
    HEAVY("heavy");

    val displayName: String
        get() = when (this) {
            OFF -> "Off"
            LIGHT -> "Light Flurry"
            MEDIUM -> "Snowfall"
            HEAVY -> "Blizzard"
        }

    val flakeCount: Int
        get() = when (this) {
            OFF -> 0
            LIGHT -> 30
            MEDIUM -> 65
            HEAVY -> 120
        }

    companion object {
        fun from(value: String?): SnowIntensity =
            entries.firstOrNull { it.value == value } ?: OFF
    }
}
