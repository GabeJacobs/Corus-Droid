package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip coverage for the artist / album / director comment attachments
 * (comment_entity_attachments_enabled). Field names are LOCKED cross-platform
 * (mirror the DM sharedArtist/sharedAlbum/sharedDirector messages) — if the
 * model renames or strips a field, iOS/web stop rendering Android-written
 * attachments and vice versa.
 */
class CommentAttachedEntityTest {

    @Test
    fun `artist round-trips through firestore map`() {
        val artist = CommentAttachedArtist(
            artistId = "spotify_artist_id",
            artistName = "Mount Kimbie",
            artistImageURL = "https://img.example/mk.jpg",
        )
        val map = artist.toFirestoreMap()
        assertEquals("spotify_artist_id", map["artistId"])
        assertEquals("Mount Kimbie", map["artistName"])
        assertEquals("https://img.example/mk.jpg", map["artistImageURL"])

        val parsed = CommentAttachedArtist.fromMap(map)
        assertEquals(artist, parsed)
    }

    @Test
    fun `artist omits null image and rejects missing id`() {
        val map = CommentAttachedArtist(artistId = "a", artistName = "n").toFirestoreMap()
        assertTrue("artistImageURL absent when null", !map.containsKey("artistImageURL"))
        assertNull(CommentAttachedArtist.fromMap(mapOf("artistName" to "n")))
        assertNull(CommentAttachedArtist.fromMap(null))
    }

    @Test
    fun `album round-trips through firestore map`() {
        val album = CommentAttachedAlbum(
            albumId = "spotify_album_id",
            albumTitle = "Cold Spring Fault Less Youth",
            albumArtistName = "Mount Kimbie",
            albumCoverURL = "https://img.example/csfly.jpg",
            albumYear = "2013",
        )
        val parsed = CommentAttachedAlbum.fromMap(album.toFirestoreMap())
        assertEquals(album, parsed)
    }

    @Test
    fun `album omits blank optional fields`() {
        val map = CommentAttachedAlbum(albumId = "a", albumTitle = "t").toFirestoreMap()
        assertTrue(!map.containsKey("albumArtistName"))
        assertTrue(!map.containsKey("albumCoverURL"))
        assertTrue(!map.containsKey("albumYear"))
        val parsed = CommentAttachedAlbum.fromMap(map)
        assertNotNull(parsed)
        assertEquals("", parsed!!.albumArtistName)
    }

    @Test
    fun `director round-trips through firestore map`() {
        val director = CommentAttachedDirector(
            directorId = "tmdb_person_id",
            directorName = "Lynne Ramsay",
            directorImageURL = "https://img.example/lr.jpg",
        )
        val parsed = CommentAttachedDirector.fromMap(director.toFirestoreMap())
        assertEquals(director, parsed)
    }

    @Test
    fun `fallback text is legible per kind`() {
        assertEquals("🎤 Mount Kimbie", CommentAttachedArtist(artistId = "a", artistName = "Mount Kimbie").fallbackText)
        assertEquals(
            "💿 CSFLY — Mount Kimbie",
            CommentAttachedAlbum(albumId = "a", albumTitle = "CSFLY", albumArtistName = "Mount Kimbie").fallbackText,
        )
        assertEquals("💿 CSFLY", CommentAttachedAlbum(albumId = "a", albumTitle = "CSFLY").fallbackText)
        assertEquals("🎬 Lynne Ramsay", CommentAttachedDirector(directorId = "d", directorName = "Lynne Ramsay").fallbackText)
    }

    @Test
    fun `cymbal comment parses entity attachments and hasAttachment`() {
        val comment = CymbalComment.fromMap(
            mapOf(
                "id" to "c1",
                "user" to mapOf("id" to "u1"),
                "text" to "🎤 Mount Kimbie",
                "textIsAttachmentFallback" to true,
                "attachedArtist" to mapOf(
                    "artistId" to "spotify_artist_id",
                    "artistName" to "Mount Kimbie",
                ),
            ),
        )
        assertNotNull(comment.attachedArtist)
        assertEquals("Mount Kimbie", comment.attachedArtist!!.artistName)
        assertTrue(comment.hasAttachment)
        assertEquals("", comment.displayText)
    }
}
