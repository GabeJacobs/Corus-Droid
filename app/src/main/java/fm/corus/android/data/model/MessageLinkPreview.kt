package fm.corus.android.data.model

data class MessageLinkPreview(
    val url: String,
    val canonicalUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    val imageURL: String? = null,
    val siteName: String? = null,
    val domain: String? = null,
    val kind: String = "link",
    val isVideo: Boolean = false,
    val fileName: String? = null,
    val mimeType: String? = null,
) {
    val openUrl: String get() = canonicalUrl?.takeIf { it.isNotBlank() } ?: url

    val displayDomain: String
        get() {
            val host = domain?.takeIf { it.isNotBlank() }
                ?: hostOf(openUrl)
                ?: hostOf(url)
                ?: ""
            return host.removePrefix("www.")
        }

    val isMediaKind: Boolean get() = kind == "image" || kind == "gif" || kind == "video"
    val showsPlayBadge: Boolean get() = isVideo || kind == "video"

    companion object {
        private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

        fun parse(raw: Any?): MessageLinkPreview? {
            val data = raw as? Map<*, *> ?: return null
            val url = (data["url"] as? String)?.trim().orEmpty()
            if (url.isEmpty()) return null
            fun str(key: String): String? =
                (data[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            return MessageLinkPreview(
                url = url,
                canonicalUrl = str("canonicalUrl"),
                title = str("title"),
                description = str("description"),
                imageURL = str("imageURL"),
                siteName = str("siteName"),
                domain = str("domain"),
                kind = str("kind") ?: "link",
                isVideo = data["isVideo"] as? Boolean ?: false,
                fileName = str("fileName"),
                mimeType = str("mimeType"),
            )
        }

        fun isUrlOnly(text: String?, previewURL: String): Boolean {
            val trimmed = text?.trim().orEmpty()
            if (trimmed.isEmpty() || previewURL.isBlank()) return false
            val matches = URL_REGEX.findAll(trimmed).map { it.value.replace(Regex("""[),.;!?]+$"""), "") }.toList()
            if (matches.size != 1) return false
            val remainder = trimmed.replace(matches[0], "")
                .replace("<", "")
                .replace(">", "")
                .trim()
            return remainder.isEmpty()
        }

        fun firstHttpUrl(text: String?): String? {
            val trimmed = text?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val match = URL_REGEX.find(trimmed) ?: return null
            return match.value.replace(Regex("""[),.;!?]+$"""), "")
        }

        private fun hostOf(value: String): String? = try {
            java.net.URI(value).host
        } catch (_: Exception) {
            null
        }
    }
}
