package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyImageUriTest {

    @Test
    fun `spotify image uri becomes CDN https url`() {
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b273abc",
            spotifyImageUriToHttps("spotify:image:ab67616d0000b273abc"),
        )
    }

    @Test
    fun `https passthrough`() {
        assertEquals(
            "https://i.scdn.co/image/already",
            spotifyImageUriToHttps("https://i.scdn.co/image/already"),
        )
    }

    @Test
    fun `blank and unknown return null`() {
        assertNull(spotifyImageUriToHttps(null))
        assertNull(spotifyImageUriToHttps(""))
        assertNull(spotifyImageUriToHttps("not-an-image-uri"))
    }
}
