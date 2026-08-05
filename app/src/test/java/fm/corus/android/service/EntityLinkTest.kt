package fm.corus.android.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a public URL's path component is read, and what that costs.
 *
 * The rule these pin down is the one that keeps two promises at once: a clean
 * URL has to reach the same screen an id URL reaches, and every id link already
 * in the wild has to keep working exactly as it did — offline, on an old
 * session, and with the resolver unreachable.
 */
class EntityLinkTest {

    private val spotifyIds = listOf(
        "4Z8W4fKeB5YxbusRsdQVPb",
        "0oSGxfWSnnOXhD2fKuz2Gy",
        "1vCWHaC5f2uS3yhpwWbIA6",
    )

    private fun read(key: String, segment: EntitySegment) = EntityLink.resolution(key, segment)

    @Test
    fun `spotify backed segments read twenty two char keys as ids`() {
        for (segment in listOf(EntitySegment.ARTIST, EntitySegment.ALBUM, EntitySegment.SONG)) {
            for (id in spotifyIds) {
                assertEquals(EntityLinkResolution.Id(id), read(id, segment))
            }
        }
    }

    @Test
    fun `tmdb backed segments read numeric keys as ids`() {
        for (segment in listOf(EntitySegment.FILM, EntitySegment.DIRECTOR)) {
            assertEquals(EntityLinkResolution.Id("27205"), read("27205", segment))
            assertEquals(EntityLinkResolution.Id("tmdb_27205"), read("tmdb_27205", segment))
        }
    }

    /**
     * THE backwards-compatibility guarantee. A shared id link must not acquire a
     * network dependency it never had, so an id key resolves without a lookup.
     */
    @Test
    fun `id keys never require a lookup`() {
        val keys = listOf(
            EntitySegment.SONG to "am:1440857781",
            EntitySegment.SONG to "sc:12345",
            EntitySegment.ALBUM to "dz:987",
            EntitySegment.DIRECTOR to "nm:0000123",
            EntitySegment.FILM to "27205",
            EntitySegment.DIRECTOR to "tmdb_138",
        ) + spotifyIds.map { EntitySegment.SONG to it }
        for ((segment, key) in keys) {
            assertEquals(key, EntityLinkResolution.Id(key), read(key, segment))
        }
    }

    /**
     * Song ids are shared under prefixes the server's list does not name. They
     * have never been slugs and must never start being read as slugs.
     */
    @Test
    fun `id prefixes the server does not name stay ids`() {
        for (key in listOf("amk:12345", "tdl:98765", "dzr:5544", "spotify:track:abc")) {
            assertEquals(key, EntityLinkResolution.Id(key), read(key, EntitySegment.SONG))
        }
    }

    @Test
    fun `clean url keys are read as slugs`() {
        assertEquals(EntityLinkResolution.Slug("radiohead"), read("radiohead", EntitySegment.ARTIST))
        assertEquals(EntityLinkResolution.Slug("burial-burial"), read("burial-burial", EntitySegment.ALBUM))
        assertEquals(
            EntityLinkResolution.Slug("christopher-nolan"),
            read("christopher-nolan", EntitySegment.DIRECTOR),
        )
        assertEquals(EntityLinkResolution.Slug("radiohead-2"), read("radiohead-2", EntitySegment.ARTIST))
    }

    /**
     * Where the two readings overlap the id wins, exactly as the server decides
     * it. `112` is a band whose slug would collide with a TMDB id, so it is a
     * slug under /artist and an id under /film — one rule, two answers.
     */
    @Test
    fun `overlapping keys agree with the server per segment`() {
        assertEquals(EntityLinkResolution.Slug("112"), read("112", EntitySegment.ARTIST))
        assertEquals(EntityLinkResolution.Id("112"), read("112", EntitySegment.FILM))
        assertEquals(EntityLinkResolution.Slug("311"), read("311", EntitySegment.SONG))
        assertEquals(EntityLinkResolution.Id("311"), read("311", EntitySegment.DIRECTOR))
    }

    @Test
    fun `twenty two character lowercase keys are ids not slugs`() {
        val key = "yesterdayneverhappened"
        assertEquals(22, key.length)
        assertEquals(EntityLinkResolution.Id(key), read(key, EntitySegment.ARTIST))
    }

    /**
     * Anything slugify could not have produced is left as an id, because
     * guessing "slug" on an unfamiliar key is what breaks a live link.
     */
    @Test
    fun `keys slugify could not produce are not treated as slugs`() {
        for (key in listOf("Radiohead", "radio head", "-radiohead", "radiohead-", "radio--head", "radiohead!", "")) {
            assertEquals(key, EntityLinkResolution.Id(key), read(key, EntitySegment.ARTIST))
        }
    }

    @Test
    fun `every public catalog segment is covered`() {
        assertEquals(
            setOf("song", "album", "artist", "film", "director"),
            EntitySegment.entries.map { it.segment }.toSet(),
        )
    }
}
