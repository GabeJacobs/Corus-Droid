package fm.corus.android.data.model

/**
 * Catalog artist linked to a Corus user via admin-curated `artistLinks`.
 * Drives the profile "View artist page" card. One user → at most one artist.
 */
data class LinkedArtist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
) {
    companion object {
        fun fromMap(raw: Map<String, Any?>?): LinkedArtist? {
            val id = (raw?.get("id") as? String)?.trim().orEmpty()
            if (id.isEmpty()) return null
            val name = (raw?.get("name") as? String)?.trim().orEmpty()
            val imageUrl = (raw?.get("imageUrl") as? String)?.trim()?.takeIf { it.isNotEmpty() }
            return LinkedArtist(id = id, name = name, imageUrl = imageUrl)
        }
    }
}
