package fm.corus.android.ui.theme

enum class AppearanceMode(val storageValue: String, val displayLabel: String) {
    // Declaration order drives the Settings dropdown order: System, then Light, then Dark.
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
}

fun String.toAppearanceMode(): AppearanceMode =
    AppearanceMode.values().firstOrNull { it.storageValue == this } ?: AppearanceMode.SYSTEM
