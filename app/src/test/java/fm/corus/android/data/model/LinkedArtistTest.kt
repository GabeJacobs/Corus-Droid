package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkedArtistTest {

    @Test
    fun parsesFromProfilePayload() {
        val artist = LinkedArtist.fromMap(
            mapOf(
                "id" to "spotify-id",
                "name" to "  egee  ",
                "imageUrl" to "https://example.com/egee.jpg",
            ),
        )
        assertEquals("spotify-id", artist?.id)
        assertEquals("egee", artist?.name)
        assertEquals("https://example.com/egee.jpg", artist?.imageUrl)
    }

    @Test
    fun missingIdReturnsNull() {
        assertNull(LinkedArtist.fromMap(null))
        assertNull(LinkedArtist.fromMap(mapOf("name" to "egee")))
        assertNull(LinkedArtist.fromMap(mapOf("id" to "   ")))
    }
}
