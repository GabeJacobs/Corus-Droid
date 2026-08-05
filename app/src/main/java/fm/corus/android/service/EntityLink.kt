package fm.corus.android.service

/**
 * The public corus.fm route segments whose URL carries an entity key.
 * `/post` and `/u` are deliberately absent: a post id is always an id, and a
 * profile is named by its username, so neither has a slug map to consult.
 */
enum class EntitySegment(val segment: String) {
    SONG("song"),
    ALBUM("album"),
    ARTIST("artist"),
    FILM("film"),
    DIRECTOR("director");

    companion object {
        fun from(segment: String): EntitySegment? = entries.firstOrNull { it.segment == segment }
    }
}

/** What the app must do with the key a public URL carried. */
sealed class EntityLinkResolution {
    /**
     * The key is an id. Every link shared before clean URLs existed lands here,
     * and lands here without a network call, so it keeps working exactly as it
     * did — including while offline and including if the resolver is down.
     */
    data class Id(val id: String) : EntityLinkResolution()

    /** The key can only be a slug, and only the server knows what it names. */
    data class Slug(val slug: String) : EntityLinkResolution()
}

/**
 * Reading a `/segment/key` path component.
 *
 * The id shapes mirror `ENTITY_ID_SHAPES` in
 * `Corus-Web/backend/functions/seoCanonical.js`, and are checked FIRST for the
 * same reason the server checks them first: a key that could be read either way
 * (`112`, `311`) is an id, because an id link that already exists must never be
 * re-read as somebody else's slug.
 *
 * A key that is not an id is only treated as a slug when it has the shape
 * `slugify` can produce — lowercase alphanumerics joined by single hyphens.
 * Anything else is left as an id, which is the one reading that cannot break a
 * link already in the wild: song ids are shared under prefixes (`amk:`, `tdl:`,
 * `dzr:`) that the server's prefix list does not name, and those must keep
 * resolving as the ids they are.
 */
object EntityLink {
    private val PREFIXED_ID_PREFIXES = listOf("am:", "sc:", "dz:", "nm:")
    private val SPOTIFY_ID = Regex("^[A-Za-z0-9]{22}$")
    private val TMDB_ID = Regex("^(?:tmdb_)?[0-9]+$")
    private val SLUG_SHAPE = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

    fun resolution(key: String, segment: EntitySegment): EntityLinkResolution {
        if (looksLikeEntityId(key, segment)) return EntityLinkResolution.Id(key)
        return if (SLUG_SHAPE.matches(key)) EntityLinkResolution.Slug(key) else EntityLinkResolution.Id(key)
    }

    fun looksLikeEntityId(value: String, segment: EntitySegment): Boolean {
        if (value.isEmpty()) return false
        if (PREFIXED_ID_PREFIXES.any { value.startsWith(it) }) return true
        return when (segment) {
            EntitySegment.SONG, EntitySegment.ALBUM, EntitySegment.ARTIST -> SPOTIFY_ID.matches(value)
            EntitySegment.FILM, EntitySegment.DIRECTOR -> TMDB_ID.matches(value)
        }
    }
}
