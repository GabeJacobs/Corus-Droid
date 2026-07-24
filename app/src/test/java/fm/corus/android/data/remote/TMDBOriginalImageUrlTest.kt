package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class TMDBOriginalImageUrlTest {

    @Test
    fun `h632 rewrites to original with the file path preserved`() {
        assertEquals(
            "https://image.tmdb.org/t/p/original/8Yy3rlKUAr0aXPGnJvOhQeU3Ceo.jpg",
            TMDBApiService.originalImageUrl("https://image.tmdb.org/t/p/h632/8Yy3rlKUAr0aXPGnJvOhQeU3Ceo.jpg"),
        )
    }

    @Test
    fun `w185 rewrites to original with the file path preserved`() {
        assertEquals(
            "https://image.tmdb.org/t/p/original/8Yy3rlKUAr0aXPGnJvOhQeU3Ceo.jpg",
            TMDBApiService.originalImageUrl("https://image.tmdb.org/t/p/w185/8Yy3rlKUAr0aXPGnJvOhQeU3Ceo.jpg"),
        )
    }

    @Test
    fun `original input is idempotent`() {
        val url = "https://image.tmdb.org/t/p/original/8Yy3rlKUAr0aXPGnJvOhQeU3Ceo.jpg"
        assertEquals(url, TMDBApiService.originalImageUrl(url))
    }

    @Test
    fun `spotify url passes through unchanged`() {
        val url = "https://i.scdn.co/image/ab6761610000e5ebd42a27db3286b58553da8858"
        assertEquals(url, TMDBApiService.originalImageUrl(url))
    }

    @Test
    fun `deezer url passes through unchanged`() {
        val url = "https://e-cdns-images.dzcdn.net/images/artist/f2bc007e9133c946ac3c3907ddc5d2ea/1000x1000-000000-80-0-0.jpg"
        assertEquals(url, TMDBApiService.originalImageUrl(url))
    }

    @Test
    fun `malformed tmdb urls pass through unchanged`() {
        assertEquals(
            "https://image.tmdb.org/t/p/",
            TMDBApiService.originalImageUrl("https://image.tmdb.org/t/p/"),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/h632",
            TMDBApiService.originalImageUrl("https://image.tmdb.org/t/p/h632"),
        )
        assertEquals(
            "https://image.tmdb.org/t/p//poster.jpg",
            TMDBApiService.originalImageUrl("https://image.tmdb.org/t/p//poster.jpg"),
        )
        assertEquals("not a url", TMDBApiService.originalImageUrl("not a url"))
    }

    @Test
    fun `empty string passes through unchanged`() {
        assertEquals("", TMDBApiService.originalImageUrl(""))
    }
}
