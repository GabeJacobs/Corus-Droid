package fm.corus.android.ui.theme

enum class AppearanceMode(val storageValue: String, val displayLabel: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "System"),
}

fun String.toAppearanceMode(): AppearanceMode =
    AppearanceMode.values().firstOrNull { it.storageValue == this } ?: AppearanceMode.LIGHT
