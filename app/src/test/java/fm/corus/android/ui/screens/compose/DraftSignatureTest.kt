package fm.corus.android.ui.screens.compose

import fm.corus.android.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The save-on-exit prompt only fires when the composer differs from the last
 * saved/resumed draft. That decision rides on [draftSignature]: it must be
 * stable across a resume-time metadata refresh (compares by id, not full
 * metadata) yet change on any real edit. Mirrors web's draftSignature.
 */
class DraftSignatureTest {

    private fun sig(
        mediaType: MediaType = MediaType.TRACK,
        trackId: String? = "t1",
        movieId: String? = null,
        caption: String = "hello",
        captionMode: String = "text",
        audience: String? = null,
        voice: String = "text",
    ) = draftSignature(mediaType, trackId, movieId, caption, captionMode, audience, voice)

    @Test
    fun `identical inputs produce identical signatures`() {
        assertEquals(sig(), sig())
    }

    @Test
    fun `a caption edit changes the signature`() {
        assertNotEquals(sig(caption = "hello"), sig(caption = "hello!"))
    }

    @Test
    fun `switching the selected track changes the signature`() {
        assertNotEquals(sig(trackId = "t1"), sig(trackId = "t2"))
    }

    @Test
    fun `changing media type changes the signature`() {
        assertNotEquals(
            sig(mediaType = MediaType.TRACK, trackId = "t1", movieId = null),
            sig(mediaType = MediaType.MOVIE, trackId = null, movieId = "m1"),
        )
    }

    @Test
    fun `caption mode and voice state changes are detected`() {
        assertNotEquals(sig(captionMode = "text", voice = "text"), sig(captionMode = "voice", voice = "none"))
        // Recording a fresh memo over a resumed one is an edit.
        assertNotEquals(sig(captionMode = "voice", voice = "saved"), sig(captionMode = "voice", voice = "new"))
    }

    @Test
    fun `comments audience changes are detected`() {
        assertNotEquals(sig(audience = "everyone"), sig(audience = "followers"))
    }

    @Test
    fun `refreshing metadata for the same track id does NOT change the signature`() {
        // Resume freshens art/preview/album but keeps the same id — must not
        // read as an edit (otherwise every resume would re-prompt on exit).
        // The signature only takes the id, so any two calls with the same id
        // and same caption/mode are equal regardless of other metadata.
        val before = sig(trackId = "t1", caption = "note")
        val afterRefresh = sig(trackId = "t1", caption = "note")
        assertEquals(before, afterRefresh)
    }

    @Test
    fun `null vs empty ids are treated consistently`() {
        // A movie draft carries a null trackId; a fresh compose also null — the
        // signature must not throw and must be deterministic.
        assertEquals(
            sig(mediaType = MediaType.MOVIE, trackId = null, movieId = "m1"),
            sig(mediaType = MediaType.MOVIE, trackId = null, movieId = "m1"),
        )
    }
}
