package fm.corus.android.ui.components

/**
 * Visual theme for profile share cards. Dark matches link unfurls; light is for
 * sharing onto bright surfaces like Instagram Stories.
 */
enum class ShareCardTheme(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    ;

    /** Query value for the preview renderer. Dark is the default everywhere. */
    val queryValue: String? get() = if (this == LIGHT) "light" else null

    /** Analytics param for profile share events. */
    val analyticsValue: String get() = if (this == LIGHT) "light" else "dark"
}

fun shareCardPreviewUrl(
    shareableLink: String,
    version: String? = null,
    theme: ShareCardTheme = ShareCardTheme.DARK,
): String {
    if (shareableLink.isBlank()) return ""
    val params = buildList {
        version?.takeIf { it.isNotEmpty() }?.let { add("v=$it") }
        theme.queryValue?.let { add("theme=$it") }
    }
    val base = "$shareableLink/preview"
    return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
}
