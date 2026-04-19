package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackCoverResponseParserTest {

    @Test
    fun `valid url is returned`() {
        val url = parseBackCoverResponse(
            mapOf("backCoverURL" to "https://example.com/back.jpg")
        )
        assertEquals("https://example.com/back.jpg", url)
    }

    @Test
    fun `null data returns null`() {
        assertNull(parseBackCoverResponse(null))
    }

    @Test
    fun `missing backCoverURL key returns null`() {
        assertNull(parseBackCoverResponse(mapOf("other" to "value")))
    }

    @Test
    fun `blank backCoverURL returns null`() {
        assertNull(parseBackCoverResponse(mapOf("backCoverURL" to "")))
    }

    @Test
    fun `non-string backCoverURL returns null`() {
        assertNull(parseBackCoverResponse(mapOf("backCoverURL" to 42)))
    }
}
